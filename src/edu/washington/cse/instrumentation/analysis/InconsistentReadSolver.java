package edu.washington.cse.instrumentation.analysis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import boomerang.AliasFinder;
import boomerang.BoomerangTimeoutException;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.FieldGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.cache.AliasResults;
import edu.washington.cse.instrumentation.analysis.aliasing.AliasResolver;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.preanalysis.SingletonTypeAnalysis;
import edu.washington.cse.instrumentation.analysis.rectree.AbstractTreeVisitor;
import edu.washington.cse.instrumentation.analysis.rectree.CallNode;
import edu.washington.cse.instrumentation.analysis.rectree.CompressedTransitiveNode;
import edu.washington.cse.instrumentation.analysis.rectree.EffectEdgeIdentity;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.ParamNode;
import edu.washington.cse.instrumentation.analysis.rectree.PrependFunction;
import edu.washington.cse.instrumentation.analysis.rectree.PrimingNode;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.cse.instrumentation.analysis.rectree.TransitiveNode;
import edu.washington.cse.instrumentation.analysis.rectree.TreeEncapsulatingFunction;
import edu.washington.cse.instrumentation.analysis.rectree.TreeFunction;
import edu.washington.cse.instrumentation.analysis.rectree.TreeVisitor;
import edu.washington.cse.instrumentation.analysis.report.Reporter;
import edu.washington.cse.instrumentation.analysis.solver.CSIDESolver;
import edu.washington.cse.instrumentation.analysis.solver.ContextResolution;
import edu.washington.cse.instrumentation.analysis.solver.EffectNarrowingFunction;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction.EdgeFunctionEffect;
import edu.washington.cse.instrumentation.analysis.utils.MultiTable;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import heros.EdgeFunction;
import heros.FlowFunction;
import heros.edgefunc.AllBottom;
import heros.edgefunc.AllTop;
import heros.edgefunc.EdgeIdentity;
import heros.flowfunc.Identity;
import heros.solver.PathEdge;
import soot.FastHierarchy;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.NopStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JNopStmt;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.StronglyConnectedComponentsFast;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.LiveLocals;
import soot.toolkits.scalar.Pair;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.Numberer;
import sun.misc.Signal;

/*
 * This is a wrapper class that lets us skip writing the enormous type arguments.
 * 
 * Basically a type def.
 */
public class InconsistentReadSolver extends CSIDESolver<Unit, AccessGraph, SootMethod, RecTreeDomain, List<Unit>, JimpleBasedInterproceduralCFG> {
	private static final int MAX_CONTEXT_RETRIES = 5; // in theory our hard limit of k=5 should handle this, but I'm a bad programmer

	private static final boolean LOG_LONG_QUERIES = Boolean.parseBoolean(System.getProperty("legato.log-long", "true")) && !AnalysisConfiguration.VERY_QUIET;
	
	public static AtomicInteger primeCounts = new AtomicInteger();
	public static AtomicInteger maxKCount = new AtomicInteger();
	
	private final AtMostOnceProblem problem;
	private final AtomicInteger reportCount = new AtomicInteger(0);
	private final boolean haltOnFirstError;
	private final boolean trackSites;
	private Reporter errorHandler;
	private AliasResolver ar;
	
	private volatile boolean debugIncoming = false;
	private Collection<SootMethod> ignored;

	private AdaptableKCFAManager kcfaManager;

	private SingletonTypeAnalysis singletonStaticTypes;

	private AnalysisModelExtension extension;

	private static class ImmutableConsList extends AbstractList<Unit> {
		private final List<Unit> delegate;
		public ImmutableConsList(final List<Unit> delegate) {
			this.delegate = delegate;
		}

		@Override
		public Unit get(final int index) {
			return this.delegate.get(index);
		}

		@Override
		public int size() {
			return this.delegate.size();
		}
		
		@Override
		public boolean equals(final Object o) {
			return this == o;
		}
		
		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}
		
	}
	
	private static class AdaptableKCFAManager {
		private final Table<List<Unit>, Pair<Unit, Integer>, List<Unit>> memoCache = HashBasedTable.create();
		private final Map<List<Unit>, ImmutableConsList> listConsing = new HashMap<>();
		
		private List<Unit> extendBy(final List<Unit> b, final Unit u, final int maxK) {
			final Pair<Unit, Integer> column = new Pair<>(u, maxK);
			if(memoCache.contains(b, column)) {
				return memoCache.get(b, column);
			}
			synchronized(memoCache) {
				if(memoCache.contains(b, column)) {
					return memoCache.get(b, column);
				}
				final List<Unit> newList = new ArrayList<>(b);
				if(newList.size() < maxK) {
					newList.add(u);
				} else {
					newList.remove(0);
					newList.add(u);
				}
				final ImmutableConsList consed;
				if(listConsing.containsKey(newList)) {
					consed = listConsing.get(newList);
				} else {
					consed = new ImmutableConsList(newList);
					listConsing.put(newList, consed);
				}
				memoCache.put(b, column, consed);
				return consed;
			}
		}
		
		public List<Unit> extend(final List<Unit> inContext, final Unit u) {
			final int newMax = Math.max(inContext.size(),  getMaxKForSite(inContext, u));
			if(AnalysisConfiguration.RECORD_MAX_K) {
				if(newMax > maxKCount.get()) {
					while(true) {
						final int a = maxKCount.get();
						if(a >= newMax) {
							break;
						}
						maxKCount.compareAndSet(a, newMax);
					}
				}
			}
			final List<Unit> newContext = extendBy(inContext, u, newMax);
			return newContext;
		}
		
		private final Table<List<Unit>, Unit, Integer> maxForSite = HashBasedTable.create();
		
		public int getMaxKForSite(final List<Unit> context, final Unit u) {
			if(!maxForSite.contains(context, u)) {
				return 1;
			} else {
				return maxForSite.get(context, u);
			}
		}
		
		public void increaseSensitivityForSite(final List<Unit> context, final Unit u, final int newK) {
			final int newMax = Math.max(getMaxKForSite(context, u), newK);
			maxForSite.put(context, u, newMax);
		}
		
		private final ImmutableConsList initialContext; 
		
		public AdaptableKCFAManager(final Unit initialUnit) {
			this.initialContext = new ImmutableConsList(Collections.singletonList(initialUnit));
		}
		
		public List<Unit> getInitialContext() {
			return initialContext;
		}
	}
	
	private InconsistentReadSolver(final AtMostOnceProblem problem, final AnalysisConfiguration config, final AdaptableKCFAManager kcfaManager) {
		super(problem, new ContextResolution<List<Unit>, Unit, SootMethod>() {

			@Override
			public List<Unit> initialContext() {
				return kcfaManager.getInitialContext();
			}

			@Override
			public List<Unit> extendContext(final List<Unit> inputContext, final Unit callSite, final SootMethod destMethod) {
				return kcfaManager.extend(inputContext, callSite);
			}
		});
		this.ignored = config.ignoredMethods;
		this.problem = problem;
		this.ar = config.aliasResolver;
		errorHandler = config.reporter;
		haltOnFirstError = config.haltOnFirstError;
		trackSites = config.trackSites;
		sun.misc.Signal.handle(new sun.misc.Signal("USR2"), new sun.misc.SignalHandler() {
			@Override
			public void handle(final Signal arg0) {
				problem.dumpDebugStats();
				System.out.println("Static time: " + staticTime.get());
				System.out.println("Non-static time: " + nonStaticTime.get());
				debugIncoming = !debugIncoming;
			}
		});
		this.errorHandler.setInitialContext(kcfaManager.getInitialContext());
		this.kcfaManager = kcfaManager;
		this.singletonStaticTypes = problem.getSingletonTypeAnalysis();
		this.extension = config.extension;	
	}
	
	public InconsistentReadSolver(final AtMostOnceProblem problem, final AnalysisConfiguration config) {
		this(problem, config, new AdaptableKCFAManager(new JNopStmt() {
			@Override
			public String toString() {
				return "[INITIAL]";
			}
		}));
	}	
	
	private static final EffectNarrowingFunction idEffectNarrow = new EffectNarrowingFunction() {
		@Override
		public EdgeFunctionEffect narrowEffect(final EdgeFunctionEffect e) {
			return e;
		}
	};
	
	@Override
	protected Set<AccessGraph> computeCallFlowFunction(final FlowFunction<AccessGraph> callFlowFunction,
			final AccessGraph d1, final AccessGraph d2, final SootMethod m) {
		if(ignored.contains(m)) {
			return Collections.emptySet();
		}
		if(d2 != zeroValue) {
			return super.computeCallFlowFunction(callFlowFunction, d1, d2, m); 
		}
		if(problem.resourceResolver.isResourceMethod(m)) {
			return Collections.emptySet();
		}
		final Set<AccessGraph> f = callFlowFunction.computeTargets(d2);
		if(f.size() != 1 || !f.contains(zeroValue)) {
			return f;
		}
		if(problem.methodAccessesResource(m)) {
			return f;
		} else {
			return Collections.emptySet();
		}
	}
	
	private static final EffectNarrowingFunction normalEffectNarrow = new EffectNarrowingFunction() {
		@Override
		public EdgeFunctionEffect narrowEffect(final EdgeFunctionEffect e) {
			switch(e) {
			case NONE:
			case PROPAGATE:
				return e;
			case WRITE:
				return EdgeFunctionEffect.NONE;
			case WRITE_AND_PROPAGATE:
				return EdgeFunctionEffect.PROPAGATE;
			}
			throw new RuntimeException("Unhandled enum: " + e);
		}
	};
	
	private final LoadingCache<SootMethod, LiveLocals> livenessCache = CacheBuilder.newBuilder().build(new CacheLoader<SootMethod, LiveLocals>() {
		@Override
		public LiveLocals load(final SootMethod key) throws Exception {
			return new SimpleLiveLocals((UnitGraph) icfg.getOrCreateUnitGraph(key));
		}
	});
	
	private static class PathFunction {
		final AccessGraph start, end;
		final Unit target;
		final EdgeFunction<RecTreeDomain> f;
		public PathFunction(final AccessGraph start, final Unit target, final AccessGraph end, final EdgeFunction<RecTreeDomain> f) {
			this.start = start;
			this.end = end;
			this.target = target;
			this.f = f;
		}
		@Override
		public String toString() {
			return "PathFunction [start=" + start + ", end=" + end + ", target=" + target + ", f=" + f + "]";
		}
		
	}
	
	// gross
	private class StaticValve {
		boolean hasStaticPropagation = false;
		List<PathFunction> queued = new ArrayList<>();
		public StaticValve() {
		}
		private void queuedPropagateInternal(final AccessGraph ag, final Unit target, final AccessGraph targetVal, final EdgeFunction<RecTreeDomain> f) {
			if(isLostStaticField(targetVal)) {
				hasStaticPropagation = true;
				for(final AccessGraph toReport : InconsistentReadSolver.this.transformStaticField(targetVal)) {
					InconsistentReadSolver.this.propagateInternal(ag, target, toReport, f);
				}
			} else {
				queued.add(new PathFunction(ag, target, targetVal, f));
			}
		}
		private void flush() {
			if(hasStaticPropagation) {
				return;
			}
			for(final PathFunction pf : queued) {
				InconsistentReadSolver.this.propagateInternal(pf.start, pf.target, pf.end, pf.f);
			}
		}
	}

	@Override
	protected void propagateAtReturn(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal, EdgeFunction<RecTreeDomain> f,
			final Unit relatedCallSite, final boolean isUnbalancedReturn, final EdgeFunction<RecTreeDomain> callSummary,
			final EdgeFunction<RecTreeDomain> contextFn, final PathEdge<Unit, AccessGraph> calleeEdge) {
		if(sourceVal == zeroValue && targetVal == zeroValue) {
			super.propagate(sourceVal, target, targetVal, f, relatedCallSite, isUnbalancedReturn);
			return;
		}
		f = narrow(f, idEffectNarrow);
		assert callSummary instanceof EffectTrackingFunction;
		final EdgeFunctionEffect effect = ((EffectTrackingFunction)callSummary).getEffect();
		final StaticValve sv = new StaticValve();
		if(effect == EdgeFunctionEffect.PROPAGATE || effect == EdgeFunctionEffect.WRITE_AND_PROPAGATE) {
			final Set<AccessGraph> mayAliasSet = findAliases(sourceVal, targetVal, target, effect, calleeEdge, relatedCallSite, targetVal);
			for(final AccessGraph ag : mayAliasSet) {
				sv.queuedPropagateInternal(sourceVal, target, ag, f);
			}
		}
		if((effect == EdgeFunctionEffect.WRITE || effect == EdgeFunctionEffect.WRITE_AND_PROPAGATE) && 
				((!targetVal.isStatic() && targetVal.getFieldCount() != 0) || (targetVal.isStatic() && targetVal.getFieldCount() > 1))
			)  {
			final WrappedSootField wsf = targetVal.getLastField();
			for(final AccessGraph ag : targetVal.popLastField()) {
				final Set<AccessGraph> mayAliasSet = findAliases(sourceVal, ag, target, effect, calleeEdge, relatedCallSite, targetVal);
				for(final AccessGraph aliasPrefix : mayAliasSet) {
					if(AliasResults.canAppend(aliasPrefix, wsf, ar.getOrMakeContext())) {
						sv.queuedPropagateInternal(sourceVal, target, aliasPrefix.appendFields(new WrappedSootField[]{wsf}), f);
					}
				}
			}
		} else {
			// read case I guess?
		}
		sv.queuedPropagateInternal(sourceVal, target, targetVal, f);
		sv.flush();
	}
	
	private final ConcurrentHashMap<Pair<AccessGraph, Unit>, Set<AccessGraph>> aliasCache = new ConcurrentHashMap<>();
	private static class FieldAliasState {
		private int timeouts = 0;
		private int success = 0;
		public synchronized void reportTimeout() {
			timeouts++;
		}
		
		public synchronized void reportSuccess() {
			success++;
		}
		
		public synchronized boolean isLost() {
			return timeouts > 2;
		}
	}
	
	private static class LostFieldTracker {
		private final ConcurrentHashMap<SootField, FieldAliasState> m = new ConcurrentHashMap<>();
		
		@SuppressWarnings("unused")
		public void reportSuccess(final SootField f) {
			if(!m.containsKey(f)) {
				m.putIfAbsent(f, new FieldAliasState());
			}
			m.get(f).reportSuccess();
		}
		
		public void reportTimeout(final SootField f) {
			if(!m.containsKey(f)) {
				m.putIfAbsent(f, new FieldAliasState());
			}
			m.get(f).reportTimeout();
		}
		
		public boolean isLost(final SootField f) {
			if(!m.containsKey(f)) {
				return false;
			}
			return m.get(f).isLost();
		}
		
		public void dumpStats() {
			for(final SootField f : m.keySet()) {
				final FieldAliasState fas = m.get(f);
				System.out.println("* " + f);
				System.out.println("  Success: " + fas.success);
				System.out.println("  Timeout: " + fas.timeouts);
			}
		}
	}
	
	private final LostFieldTracker lft = new LostFieldTracker();
	private final SootClass objectClass = Scene.v().getSootClass("java.lang.Object");
	
	private final AtomicLong staticTime = new AtomicLong();
	private final AtomicLong nonStaticTime = new AtomicLong();
	
	public Set<AccessGraph> findAliases(final AccessGraph source, final AccessGraph ag, final Unit target,
			final EdgeFunctionEffect ef, final PathEdge<Unit, AccessGraph> calleeEdge,
			final Unit relatedCallSite, final AccessGraph value) {
		if(!AtMostOnceProblem.mayAlias(ag)) {
			return Collections.emptySet();
		}
		if((ag.isStatic() && extension.isManagedStatic(ag.getFirstField().getField())) || 
				(!ag.isStatic() && extension.isManagedLocal(ag.getBase()))) {
			return Collections.emptySet();
		}
		if(containsLostField(value)) {
			this.reportHeapTimeout(target, ag);
			return Collections.emptySet();
		}
		final Pair<AccessGraph, Unit> p = new Pair<>(ag, target);
		final Set<AccessGraph> res = aliasCache.get(p);
		if(res != null) {
			return res;
		}
		final long start = System.currentTimeMillis();
		Set<AccessGraph> mas;
		try {
			mas = ar.doAliasSearch(ag, target).mayAliasSet();
		} catch(final BoomerangTimeoutException e) {
			final SootField toReportField = reportFieldAliases(value);
			if(toReportField != null) {
				lft.reportTimeout(toReportField);
			}
			this.reportHeapTimeout(target, value);
			mas = Collections.emptySet();
		}
		
		final long end = System.currentTimeMillis();
		if(end - start > 200 && LOG_LONG_QUERIES) {
			System.out.println("Long query: " + p + " (" + (end - start) + ") in " + icfg.getMethodOf(target) +
					" because: " + ef + " with edge: " + calleeEdge + " from " + relatedCallSite);
		}
		if(ag.isStatic()) {
			staticTime.addAndGet(end - start);
		} else {
			nonStaticTime.addAndGet(end - start);
		}
		aliasCache.putIfAbsent(p, mas);
		return mas;
	}

	private SootField reportFieldAliases(final AccessGraph ag) {
		if(ag.getLastField() == null) {
			return null;
		}
		final SootField sf = ag.getLastField().getField();
		if(sf == AliasFinder.ARRAY_FIELD || sf.getDeclaringClass().equals(objectClass)) {
			if(ag.getFirstField().equals(ag.getLastField())) {
				return null;
			}
			final WrappedSootField[] repr = ag.getRepresentative();
			for(int i = repr.length - 1; i >= 0; i--) {
				final SootField f = repr[i].getField();
				if(f == AliasFinder.ARRAY_FIELD || f.getDeclaringClass().equals(objectClass)) {
					continue;
				}
				return f;
			}
			return null;
		}
		return sf;
	}
	
	private EdgeFunction<RecTreeDomain> narrow(final EdgeFunction<RecTreeDomain> f, final EffectNarrowingFunction enf) {
		if(f instanceof EdgeIdentity) {
			return f;
		}
		assert f instanceof EffectTrackingFunction;
		final EffectTrackingFunction etf = (EffectTrackingFunction) f;
		final EdgeFunctionEffect ef = etf.getEffect();
		if(etf instanceof EffectEdgeIdentity) {
			switch(enf.narrowEffect(ef)) {
			case NONE:
				return EffectEdgeIdentity.id();
			case PROPAGATE:
				return EffectEdgeIdentity.propagate();
			case WRITE:
				return EffectEdgeIdentity.write();
			case WRITE_AND_PROPAGATE:
				return EffectEdgeIdentity.propagate_and_write();
			default:
				throw new RuntimeException("impossible");
			}
		} else if(etf instanceof PrependFunction) {
			final PrependFunction pf = (PrependFunction) etf;
			return new PrependFunction(pf.paramTree, enf.narrowEffect(ef));
		} else if(etf instanceof TreeFunction) {
			final TreeFunction tf = (TreeFunction) etf;
			if(tf.isBottom()) {
				return f;
			} else {
				return new TreeFunction(tf.tree, enf.narrowEffect(ef));
			}
		} else {
			throw new RuntimeException("Unhandled function type: " + f + " " + f.getClass());
		}
	}
	
	@Override
	protected void propagate(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal, EdgeFunction<RecTreeDomain> f,
			final Unit relatedCallSite, final boolean isUnbalancedReturn) {
		if(targetVal == zeroValue && sourceVal == zeroValue) {
			super.propagate(sourceVal, target, targetVal, f, relatedCallSite, isUnbalancedReturn);
			return;
		}
		EffectNarrowingFunction enf;
		if(targetVal.getFieldCount() == 0) {
			enf = normalEffectNarrow;
		} else {
			enf = idEffectNarrow;
		}
		f = narrow(f, enf);
		propagateInternal(sourceVal, target, targetVal, f);
	}
	
	private static void recordPrimeCounts(final RecTreeDomain d) {
		final int maxPrimes = d.getMaxPrimes();
		while(true) {
			final int currCount = primeCounts.get();
			if(maxPrimes <= currCount) {
				break;
			}
			if(primeCounts.compareAndSet(currCount, maxPrimes)) {
				break;
			}
		}
	}
	
	private final Multimap<Unit, AccessGraph> lostStaticFlows = HashMultimap.create();
	private final Multimap<Unit, AccessGraph> lostHeapTimeout = HashMultimap.create();
	
	private void propagateInternal(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal, final EdgeFunction<RecTreeDomain> f) {
		if(AnalysisConfiguration.RECORD_MAX_PRIMES) {
			if(f instanceof TreeEncapsulatingFunction) {
				recordPrimeCounts(((TreeEncapsulatingFunction) f).getTree());
			}
		}
		final boolean isLost = isLostStaticField(targetVal);
		if(isLost) {
			synchronized(lostStaticFlows) {
				for(final AccessGraph reportedStatic : transformStaticField(targetVal)) {
					lostStaticFlows.put(target, reportedStatic);
				}
			}
		}
		if(AnalysisConfiguration.CONSERVATIVE_HEAP && targetVal.getFieldCount() > 0) {
			lostHeapTimeout.put(target, targetVal);
		}
		if(containsLostField(targetVal)) {
			lostHeapTimeout.put(target, targetVal);
		}
		EdgeFunction<RecTreeDomain> jumpFnE;
		final EdgeFunction<RecTreeDomain> fPrime;
		final boolean newFunction;
		final boolean wasBottom;
		final boolean inputBottom = isBottomFunction(f);
		synchronized (jumpFn) {
			jumpFnE = jumpFn.reverseLookup(target, targetVal).get(sourceVal);
			if(jumpFnE==null) {
				jumpFnE = allTop; //JumpFn is initialized to all-top (see line [2] in SRH96 paper)
				wasBottom = false;
			} else {
				wasBottom = isBottomFunction(jumpFnE); 
			}
			fPrime = jumpFnE.joinWith(f);
			assert saneFunction(sourceVal, fPrime) : new PathEdge<Unit, AccessGraph>(sourceVal, target, targetVal) + " " 
					+ jumpFnE + " " + f + " " + icfg.getMethodOf(target) + " " + fPrime;
			newFunction = !fPrime.equalTo(jumpFnE);
			if(newFunction) {
				if(debugIncoming && sourceVal == targetVal && icfg.getStartPointsOf(icfg.getMethodOf(target)).contains(target)) {
					System.out.println("New incoming to: " + icfg.getMethodOf(target) + " " + sourceVal);
				}
				jumpFn.addFunction(sourceVal, target, targetVal, fPrime);
			}
		}
		if(fPrime instanceof TreeFunction && ((TreeFunction)fPrime).isBottom() && !wasBottom && !inputBottom) {
			if(isLiveReport(target, targetVal)) {
				synchronized(inconsistentFlows) {
					inconsistentFlows.put(new PathEdge<>(sourceVal, target, targetVal), new EdgeFunctionPair(f, jumpFnE));
				}
				if(haltOnFirstError) {
					this.executor.shutdown();
				}
			}
		}
		if(newFunction && !isLost) {
			final PathEdge<Unit, AccessGraph> edge = new PathEdge<>(sourceVal, target, targetVal);
			scheduleEdgeProcessing(edge);
			if(targetVal != zeroValue && debugIncoming) {
				logger.info("{} - EDGE: <{},{}> -> <{},{}> - {} (input: {} {})", getDebugName(),
						icfg.getMethodOf(target), sourceVal, target, targetVal, fPrime, f, jumpFnE);
			}
		}
	}

	public boolean containsLostField(final AccessGraph targetVal) {
		final WrappedSootField[] repr = targetVal.getRepresentative();
		if(repr != null && repr.length != 0) {
			for(final WrappedSootField wsf : repr) {
				if(lft.isLost(wsf.getField())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isBottomFunction(final EdgeFunction<RecTreeDomain> f) {
		return f instanceof TreeFunction && ((TreeFunction)f).isBottom();
	}
	
	public void reportHeapTimeout(final Unit target, final AccessGraph value) {
		synchronized(lostHeapTimeout) {
			lostHeapTimeout.put(target, value);
		}
	}
	
	private boolean isLostStaticField(final AccessGraph graph) {
		if(!graph.isStatic()) {
			final Set<RefType> singletonTypes = singletonStaticTypes.getSingletonTypes();
			if(singletonTypes.contains(graph.getBaseType())) {
				return true;
			}
			if(graph.getFieldCount() > 0) {
				for(final WrappedSootField wsf : graph.getRepresentative()) {
					if(singletonTypes.contains(wsf.getType())) {
						return true;
					}
				}
			}
			return false;
		}
		assert graph.isStatic();
		if(graph.isStatic() && extension.isManagedStatic(graph.getFirstField().getField())) {
			return false;
		}
		final Type fieldType = graph.getFirstField().getType();
		if(!AtMostOnceProblem.mayAliasType(fieldType)) {
			return false;
		}
		return true;
	}
	
	private Set<AccessGraph> transformStaticField(final AccessGraph graph) {
		if(!graph.isStatic()) {
			final Set<RefType> singletonTypes = singletonStaticTypes.getSingletonTypes();
			if(singletonTypes.contains(graph.getBaseType())) {
				assert graph.getBaseType() instanceof RefType;
				final Set<SootField> fields = singletonStaticTypes.getFieldForType((RefType) graph.getBaseType());
				final HashSet<AccessGraph> toReturn = new HashSet<>();
				for(final SootField f : fields) {
					toReturn.add(graph.makeStatic().prependField(new WrappedSootField(f, f.getType(), null)));
				}
				return toReturn;
			}
			if(graph.getFieldCount() > 0) {
				final WrappedSootField[] representative = graph.getRepresentative();
				for(int i = 0; i < representative.length; i++) {
					final WrappedSootField wsf = representative[i];
					if(singletonTypes.contains(wsf.getType())) {
						final Set<SootField> fields = singletonStaticTypes.getFieldForType((RefType) wsf.getType());
						final Set<AccessGraph> toReturn = new HashSet<>();
						if(i == representative.length - 1) {
							for(final SootField f : fields) {
								final AccessGraph toAdd = new AccessGraph(null, null, new WrappedSootField(f, f.getType(), null));
								toReturn.add(toAdd);
							}
						} else {
							final WrappedSootField[] appendedFields = Arrays.copyOfRange(representative, i+1, representative.length);
							for(final SootField f : fields) {
								final AccessGraph toAdd = new AccessGraph(null, null, new WrappedSootField(f, f.getType(), null)).appendFields(appendedFields);
								toReturn.add(toAdd);
							}
						}
						return toReturn;
					}
				}
			}
			return null;
		}
		assert graph.isStatic();
		if(graph.isStatic() && extension.isManagedStatic(graph.getFirstField().getField())) {
			return null;
		}
		final Type fieldType = graph.getFirstField().getType();
		if(!AtMostOnceProblem.mayAliasType(fieldType)) {
			return null;
		}
		return Collections.singleton(graph);
	}
	
	private boolean saneFunction(final AccessGraph sourceVal, final EdgeFunction<RecTreeDomain> fPrime) {
		if(sourceVal == zeroValue) {
			return fPrime instanceof TreeFunction;
		} else {
			return fPrime instanceof PrependFunction || fPrime instanceof EdgeIdentity || fPrime instanceof EffectEdgeIdentity 
					|| isBottomFunction(fPrime);
		}
	}
	
	@Override
	protected void setVal(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) {
		if(val != RecTreeDomain.BOTTOM) {
			if(lostHeapTimeout.containsEntry(unit, fact) && val != RecTreeDomain.TOP) {
				errorGraphs.put(context, unit, fact);
			} else if(lostStaticFlows.containsEntry(unit, fact) && val != RecTreeDomain.TOP) {
				staticGraphs.put(context, unit, fact);
			}
		}
		super.setVal(context, unit, fact, val);
	}
	
	@SuppressWarnings("unused")
	private void debugTraceIncoming(final Unit unit, final AccessGraph fact) {
		final Map<AccessGraph, EdgeFunction<RecTreeDomain>> reverseLookup = this.jumpFn.reverseLookup(unit, fact);
		System.out.println("Originating from: " + reverseLookup);
		final HashSet<Pair<SootMethod, AccessGraph>> visited = new HashSet<>();
		final SootMethod m = icfg.getMethodOf(unit);
		final LinkedList<Pair<SootMethod, AccessGraph>> worklist = new LinkedList<>();
		for(final AccessGraph ag : reverseLookup.keySet()) {
			if(ag == zeroValue) {
				continue;
			}
			worklist.add(new Pair<>(m, ag));
		}
		while(!worklist.isEmpty()) {
			final Pair<SootMethod, AccessGraph> inputFact = worklist.removeFirst();
			if(!visited.add(inputFact)) {
				continue;
			}
			System.out.println(">>> Tracing inputs for: " + inputFact);
			final Map<Unit, Set<AccessGraph>> incoming = this.incoming(inputFact.getO2(), icfg.getStartPointsOf(inputFact.getO1()).iterator().next());
			for(final Map.Entry<Unit, Set<AccessGraph>> kv : incoming.entrySet()) {
				final Unit callerUnit = kv.getKey();
				final SootMethod callerMethod = icfg.getMethodOf(callerUnit);
				System.out.println("Found call: " + callerUnit + " in " + callerMethod);
				System.out.println("with inputs: " + kv.getValue());
				for(final AccessGraph callerFact : kv.getValue()) {
					final Map<AccessGraph, EdgeFunction<RecTreeDomain>> reversedFlow = this.jumpFn.reverseLookup(callerUnit, callerFact);
					System.out.println("++ Input fact: " + callerFact + " originated from: " + reversedFlow);
					for(final AccessGraph callerInputFact : reversedFlow.keySet()) {
						if(callerInputFact == zeroValue) {
							continue;
						}
						worklist.add(new Pair<>(callerMethod, callerInputFact));
					}
				}
			}
		}
	}
	
	private final MultiTable<List<Unit>, Unit, AccessGraph> potentialCSSites = new MultiTable<>();
	private final MultiTable<List<Unit>, Unit, AccessGraph> errorGraphs = new MultiTable<>();
	private final MultiTable<List<Unit>, Unit, AccessGraph> staticGraphs = new MultiTable<>();
	
	private final Map<PathEdge<Unit, AccessGraph>, EdgeFunctionPair> inconsistentFlows = new HashMap<>();
	
	@Override
	protected RecTreeDomain joinValueAt(final List<Unit> callerContext, final Unit unit, final AccessGraph fact, final RecTreeDomain curr, final RecTreeDomain newVal) {
		if(fact == zeroValue) {
			return super.joinValueAt(callerContext, unit, fact, curr, newVal);
		}
		if(AnalysisConfiguration.RECORD_MAX_PRIMES) {
			recordPrimeCounts(newVal);
		}
		final boolean currBottom = curr == RecTreeDomain.BOTTOM;
		final boolean newBottom = newVal == RecTreeDomain.BOTTOM;
		final RecTreeDomain toReturn = super.joinValueAt(callerContext, unit, fact, curr, newVal);
		if(toReturn == RecTreeDomain.BOTTOM && !currBottom && !newBottom) {
			if(icfg.getStartPointsOf(icfg.getMethodOf(unit)).contains(unit)) {
				potentialCSSites.put(callerContext, unit, fact);
			}
			errorGraphs.put(callerContext, unit, fact);
		}
		return toReturn;
	}
	
	@Override
	protected void resetValueState() {
		super.resetValueState();
		
		potentialCSSites.clear();
		errorGraphs.clear();
		staticGraphs.clear();
	}

	public void dumpIncoming(final PrintWriter pw) {
		final Set<SootMethod> printed = new HashSet<>();
		for(final Cell<Unit, AccessGraph, Map<Unit, Set<AccessGraph>>> cell : incoming.cellSet()) {
			final Unit sp = cell.getRowKey();
			final AccessGraph startFact = cell.getColumnKey();
			if(startFact == zeroValue) {
				continue;
			}
			final SootMethod methodOf = icfg.getMethodOf(sp);
			printed.add(methodOf);
			pw.println(">>>> Incoming of " + startFact + " of method " + methodOf);
			for(final Map.Entry<Unit, Set<AccessGraph>> kv : cell.getValue().entrySet()) {
				pw.println(kv.getKey() + " in " + icfg.getMethodOf(kv.getKey()) + " --> " + kv.getValue());
			}
		}
	}
	
	public void dumpEdgesAdHoc(final PrintWriter w, final Collection<SootMethod> ms) {
		for(final SootMethod m : ms) {
			dumpEdges(w, m);
		}
	}
	
	private void dumpEdges(final PrintWriter w, final SootMethod m) {
		w.println("[[[[ " + m + " ]]]]");
		for(final Unit u : m.getActiveBody().getUnits()) {
			final Map<Unit, Map<AccessGraph, Set<AccessGraph>>> row = computedIntraPEdges.row(u);
			w.println(">>>>>> " + u);
			for(final Map.Entry<Unit, Map<AccessGraph, Set<AccessGraph>>> kv1 : row.entrySet()) {
				for(final Map.Entry<AccessGraph, Set<AccessGraph>> kv2 : kv1.getValue().entrySet()) {
					w.println(">>> " + kv2.getKey() + " ==> " + kv1.getKey());
					w.println(kv2.getValue());
				}
			}
		}
	}
	
	@Override
	public void solve() {
		super.solve();
		this.postProcessErrors();
		this.printStats();
	}
	
	private final LoadingCache<SootMethod, Collection<Loop>> loopCache = CacheBuilder.newBuilder().build(new CacheLoader<SootMethod, Collection<Loop>>() {
		@Override
		public Collection<Loop> load(final SootMethod key) throws Exception {
			final LoopFinder lf = new LoopFinder();
			lf.transform(key.getActiveBody());
			return lf.loops();
		}
	});
	
	int increaseCount = 0;
	
	@Override
	protected ContextResolution<List<Unit>,Unit,SootMethod> tryWithNewContextStrategy() {
		if(increaseCount == MAX_CONTEXT_RETRIES) {
			return null;
		}
		boolean increased = false;
		final HashSet<List<Unit>> extendedContexts = new HashSet<>();
		for(final Cell<List<Unit>, Unit, AccessGraph> kv : potentialCSSites.cellSet()) {
			final List<Unit> context = kv.getRowKey();
			final Unit targetUnit = kv.getColumnKey();
			final AccessGraph target = kv.getValue();
			final Table<List<Unit>, AccessGraph, RecTreeDomain> reachingValues = computeReachingValues(context, targetUnit, target);
			if(this.insufficientContextError(reachingValues) && extendedContexts.add(context)) {
				final Unit lastCallSite = context.get(context.size() - 1);
				for(final List<Unit> incomingContext : reachingValues.rowKeySet()) {
					if(kcfaManager.getMaxKForSite(incomingContext, lastCallSite) == 5) {
						// lololol
						continue;
					}
					if(!AnalysisConfiguration.VERY_QUIET) {
						//System.out.println("Adaptively increasing context sensitivity for call: " + lastCallSite + " under context " + incomingContext);
					}
					kcfaManager.increaseSensitivityForSite(incomingContext, lastCallSite, incomingContext.size() + 1);
					increased = true;
				}
			}
		}
		increaseCount++;
		if(increased) {
			return this.contextResolver;
		} else {
			return null;
		}
	};

	private boolean insufficientContextError(final Table<List<Unit>, AccessGraph, RecTreeDomain> reachingValues) {
		final Map<List<Unit>, RecTreeDomain> byContextGraph = new HashMap<>();
		boolean allContextConsistent = true;
		for(final Cell<List<Unit>, AccessGraph, RecTreeDomain> c : reachingValues.cellSet()) {
			// One of our incoming arguments is bottom, and it didn't possibly come from us!
			if(c.getValue() == RecTreeDomain.BOTTOM) {
				return false;
			}
			final RecTreeDomain d = byContextGraph.containsKey(c.getRowKey()) ? byContextGraph.get(c.getRowKey()) : RecTreeDomain.TOP;
			final RecTreeDomain incomingFrom = d.joinWith(c.getValue());
			if(incomingFrom == RecTreeDomain.BOTTOM) {
				allContextConsistent = false;
			}
			byContextGraph.put(c.getRowKey(), incomingFrom);
		}
		return allContextConsistent;
	}
	
	private final static class ValueReport {
		public final Unit target;
		public final AccessGraph targetVal;
		public final Set<Pair<AccessGraph, RecTreeDomain>> incomingValues;
		private final boolean allPredConsistent;
		
		public ValueReport(final Unit target, final AccessGraph targetVal, final Set<Pair<AccessGraph, RecTreeDomain>> incoming, final boolean allPredConsistent) {
			this.target = target;
			this.targetVal = targetVal;
			this.incomingValues = incoming;
			this.allPredConsistent = allPredConsistent;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((incomingValues == null) ? 0 : incomingValues.hashCode());
			result = prime * result + ((target == null) ? 0 : target.hashCode());
			result = prime * result + ((targetVal == null) ? 0 : targetVal.hashCode());
			result = allPredConsistent ? 1231 : 71;
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null) {
				return false;
			}
			if(getClass() != obj.getClass()) {
				return false;
			}
			final ValueReport other = (ValueReport) obj;
			if(!incomingValues.equals(other.incomingValues)) {
				return false;
			}
			if(!target.equals(other.target)) {
				return false;
			}
			if(other.allPredConsistent != this.allPredConsistent) {
				return false;
			}
			return targetVal.equals(other.targetVal);
		}

		@Override
		public String toString() {
			return "ValueReport [target=" + target + ", targetVal=" + targetVal + ", incomingValues=" + incomingValues + ", allPredConsistent=" + allPredConsistent + "]";
		}
	}

	private void postProcessErrors() {
		int suppressed = 0;
		int pathSensitivity = 0;
		for(final Map.Entry<PathEdge<Unit, AccessGraph>, EdgeFunctionPair> kv : inconsistentFlows.entrySet()) {
			final AccessGraph sourceVal = kv.getKey().factAtSource();
			final Unit target = kv.getKey().getTarget();
			final AccessGraph targetVal = kv.getKey().factAtTarget();
			final EdgeFunction<RecTreeDomain> f = kv.getValue().getO1();
			final EdgeFunction<RecTreeDomain> jumpFnE = kv.getValue().getO2();
			if(isRecursiveCycle(kv.getValue())) {
				errorHandler.handleInconsistentFlow(sourceVal, target, targetVal, f, jumpFnE, false);
				recordSites(jumpFnE); recordSites(f);
				recordSite(target);
				reportCount.incrementAndGet();
				continue;
			}
			final Map<Unit, EdgeFunction<RecTreeDomain>> unitAccum = new HashMap<>();
			boolean allConsistent = true;
			final Collection<IncomingEdge> reversed = this.getReversedFlow(sourceVal, target, targetVal);
			for(final IncomingEdge ie : reversed) {
				final Unit src = ie.src;
				final EdgeFunction<RecTreeDomain> accum = unitAccum.containsKey(src) ? unitAccum.get(src) : this.allTop;
				final EdgeFunction<RecTreeDomain> joined = accum.joinWith(ie.incoming);
				if(isBottomFunction(joined)) {
					allConsistent = false;
					break;
				}
				unitAccum.put(src, joined);
			}
			if(allConsistent) {
				pathSensitivity++;
			}
			if(!isErrorCarriedForward(reversed, target)) {
				errorHandler.handleInconsistentFlow(sourceVal, target, targetVal, f, jumpFnE, allConsistent); 
				recordSites(jumpFnE); recordSites(f);
				recordSite(target);
				reportCount.incrementAndGet();	
			} else {
				suppressed++;
			}
		}
		System.out.println("path sensitivity errors: " + pathSensitivity);
		if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println("Suppressed " + suppressed + " error carried forward flows");
			System.out.println("Suppressing unrealizeable value errors...");
		}
		int suppressedUnrealizable = 0;
		final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		final Map<Set<AccessGraph>, Boolean> realizeableCache = new HashMap<>();
		final MultiTable<List<Unit>, Unit, AccessGraph> toSuppress = new MultiTable<>();
		unit_loop: for(final Cell<List<Unit>, Unit, AccessGraph> kv : errorGraphs.cellSet()) {
			final Unit u = kv.getColumnKey();
			final SootMethod method = icfg.getMethodOf(u);
			final Unit startPoint = icfg.getStartPointsOf(method).iterator().next();
			final List<Unit> context = kv.getRowKey();
			final AccessGraph targetGraph = kv.getValue();
			final Map<AccessGraph, EdgeFunction<RecTreeDomain>> reverseLookup = jumpFn.reverseLookup(u, targetGraph);
			final Multimap<String, AccessGraph> pointwiseGraphs = HashMultimap.create();
			// compute our incoming functions, grouped by facts
			for(final Map.Entry<AccessGraph, EdgeFunction<RecTreeDomain>> f : reverseLookup.entrySet()) {
				final RecTreeDomain inputValue = this.resultsAt(context, startPoint, f.getKey());
				if(inputValue == null) {
					continue;
				}
				final RecTreeDomain valueAt = f.getValue().computeTarget(inputValue);
				if(valueAt == RecTreeDomain.TOP) {
					continue;
				}
				// We have a bottom value? This seems fishy, but skip
				if(valueAt == RecTreeDomain.BOTTOM) {
					continue unit_loop;
				}
				if(valueAt.restRoot != null) {
					pointwiseGraphs.put("*", f.getKey());
				}
				if(valueAt.pointwiseRoots != null) {
					for(final String k : valueAt.pointwiseRoots.keySet()) {
						pointwiseGraphs.put(k, f.getKey());
					}
				}
			}
			// For each point wise set of source graphs
			for(final Map.Entry<String, Collection<AccessGraph>> pKv : pointwiseGraphs.asMap().entrySet()) {
				final Set<AccessGraph> inputGraphs = new HashSet<>(pKv.getValue());
				// If this is the only input graph, it can't have resulted in an error
				if(inputGraphs.size() == 1) {
					continue;
				}
				if(realizeableCache.containsKey(inputGraphs)) {
					// if this is a realizeable input, then we can't filer
					if(realizeableCache.get(inputGraphs)) {
						continue unit_loop;
					}
					// This is unrealizeable, keep looking
					continue;
				}
				final Iterator<AccessGraph> iterator = inputGraphs.iterator();
				final AccessGraph firstGraph = iterator.next();
				if(firstGraph.isStatic()) {
					realizeableCache.put(inputGraphs, false);
					continue unit_loop;
				}
				final Set<Type> typeAccumulator = new HashSet<>();
				typeAccumulator.add(firstGraph.getBaseType());
				final Local base = firstGraph.getBase();
				final FieldGraph fg = firstGraph.getFieldGraph();
				boolean realizeable = false;
				/* Now loop over the incoming facts: if they all have incompatible base types and 
				 * match the fields and base variable
				 * then this flow is unrealizeable
				 */
				input_loop: while(iterator.hasNext()) {
					final AccessGraph curr = iterator.next();
					if(curr.isStatic()) {
						realizeable = true;
						break;
					}
					final Local input = curr.getBase();
					if(input != base) {
						realizeable = true;
						break;
					}
					if(!Objects.equals(fg, curr.getFieldGraph())) {
						realizeable = true;
						break;
					}
					final Type t = curr.getBaseType();
					for(final Type other : typeAccumulator) {
						if(fh.canStoreType(other, t) || fh.canStoreType(t, other)) {
							realizeable = true;
							break input_loop;
						}
					}
					typeAccumulator.add(t);
				}
				if(!realizeable) {
					if(!AnalysisConfiguration.VERY_QUIET) {
						System.out.println("Input " + inputGraphs + " is unrealizeable");
					}
				}
				// Cache the result
				realizeableCache.put(inputGraphs, realizeable);
				// This input is realizeabe
				if(realizeable) {
					continue unit_loop;
				}
			}
			// If we get there, all non-unary input graphs are unrealizeable
			suppressedUnrealizable++;
			toSuppress.put(context, u, targetGraph);
		}
		if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println("... done (" + suppressedUnrealizable + " suppressed)");
		}
		for(final Cell<List<Unit>, Unit, AccessGraph> kv : toSuppress.cellSet()) {
			errorGraphs.remove(kv.getRowKey(), kv.getColumnKey(), kv.getValue());
		}
		int suppressedECF = 0;
		if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println("Filtering errors carried forward...");
		}
		
		final HashSet<SootMethod> recursiveMethods = new HashSet<>();
		{
			final StronglyConnectedComponentsFast<SootMethod> scc = new StronglyConnectedComponentsFast<>(AtMostOnceProblem.makeDirectedCallGraph(icfg));
			for(final List<SootMethod> recCycle : scc.getTrueComponents()) {
				recursiveMethods.addAll(recCycle);
			}
		}
		final HashMultimap<ValueReport, List<Unit>> reportToContext = HashMultimap.create();
		error_loop: for(final Cell<List<Unit>, Unit, AccessGraph> kv : errorGraphs.cellSet()) {
			final List<Unit> context = kv.getRowKey();
			final Unit targetUnit = kv.getColumnKey();
			final AccessGraph target = kv.getValue();
			final SootMethod m = icfg.getMethodOf(targetUnit);
			final Unit sP = icfg.getStartPointsOf(m).iterator().next();
			if(sP == targetUnit) {
				// handle this specially (detect context sensitivity fp)
				final Table<List<Unit>, AccessGraph, RecTreeDomain> reachingValues = computeReachingValues(context, targetUnit, target);
				final Map<List<Unit>, RecTreeDomain> byContextGraph = new HashMap<>();
				boolean allContextConsistent = true;
				for(final Cell<List<Unit>, AccessGraph, RecTreeDomain> c : reachingValues.cellSet()) {
					// One of our incoming arguments is bottom, and it didn't possibly come from us!
					if(c.getValue() == RecTreeDomain.BOTTOM && !recursiveMethods.contains(m)) {
						suppressedECF++;
						continue error_loop;
					}
					final RecTreeDomain d = byContextGraph.containsKey(c.getRowKey()) ? byContextGraph.get(c.getRowKey()) : RecTreeDomain.TOP;
					final RecTreeDomain incomingFrom = d.joinWith(c.getValue());
					if(incomingFrom == RecTreeDomain.BOTTOM) {
						allContextConsistent = false;
					}
					byContextGraph.put(c.getRowKey(), incomingFrom);
				}
				// If this incoming value would have been consistent under a 2-CFA, flag it
				this.handleValueReport(context, targetUnit, target, allContextConsistent, Collections.<List<Unit>>emptyList());
				continue;
			}
			for(final Unit pred : icfg.getPredsOf(targetUnit)) {
				if(icfg.isCallStmt(pred)) {
					for(final SootMethod callee : icfg.getCalleesOfCallAt(pred)) {
						for(final Unit returnSite : icfg.getEndPointsOf(callee)) {
							final Set<AccessGraph> returnSiteFacts = new HashSet<>();
							for(final Cell<?, AccessGraph, ?> c : jumpFn.lookupByTarget(returnSite)) {
								if(c.getColumnKey().equals(zeroValue)) {
									continue;
								}
								returnSiteFacts.add(c.getColumnKey());
							}
							final FlowFunction<AccessGraph> returnFlow = ffCache.getReturnFlowFunction(pred, callee, targetUnit, returnSite);
							for(final AccessGraph exitFact : returnSiteFacts) {
								if(returnFlow.computeTargets(exitFact).contains(target)) {
									final List<Unit> calleeContext = contextResolver.extendContext(context, pred, callee);
									if(this.val(calleeContext, returnSite, exitFact) == RecTreeDomain.BOTTOM) {
										suppressedECF++;
										continue error_loop;
									}
								}
							}
						}
					}
				}
			}
			
			final Collection<AccessGraph> inputValues = this.jumpFn.reverseLookup(targetUnit, target).keySet();
			// Find values that flow into this one
			final Map<Unit, RecTreeDomain> byPredAccum = new HashMap<>();
			boolean allConsistent = true, allPredConsistent = true;
			for(final AccessGraph input : inputValues) {
				final RecTreeDomain startValue = val(context, sP, input);
				if(startValue == RecTreeDomain.BOTTOM && input != zeroValue) {
					// by definition an ECF
					suppressedECF++;
					continue error_loop;
				}
				if(startValue == null || startValue == RecTreeDomain.TOP) {
					continue;
				}
				final Collection<IncomingEdge> reversedFlow = this.getReversedFlow(input, targetUnit, target);
				for(final IncomingEdge ie : reversedFlow) {
					final RecTreeDomain prevValue = val(context, ie.src, ie.srcFact);
					final RecTreeDomain incoming = ie.stepFunction.computeTarget(prevValue);
					if(incoming == RecTreeDomain.BOTTOM) {
						allPredConsistent = allConsistent = false;
						break;
					}
					final RecTreeDomain predAccum = byPredAccum.containsKey(ie.src) ? byPredAccum.get(ie.src) : RecTreeDomain.TOP;
					final RecTreeDomain joined = predAccum.joinWith(incoming);
					if(joined == RecTreeDomain.BOTTOM) {
						allPredConsistent = false;
					}
					byPredAccum.put(ie.src, joined);
				}
				if(!allConsistent) {
					break;
				}
			}
			if(!allConsistent && !isLoopHead(targetUnit, m)) {
				suppressedECF++;
				continue error_loop;
			}
			final Table<List<Unit>, AccessGraph, RecTreeDomain> reach = computeReachingValues(context, targetUnit, target);
			assert reach.rowKeySet().size() == 1;
			final HashSet<String> failing = new HashSet<>(findFailingProps(reach.values()));
			final Set<Pair<AccessGraph, RecTreeDomain>> vals = new HashSet<>();
			for(final Cell<List<Unit>, AccessGraph, RecTreeDomain> c : reach.cellSet()) {
				final RecTreeDomain rtd = filterFailing(c.getValue(), failing);
				if(rtd == null) {
					continue;
				}
				vals.add(new Pair<>(c.getColumnKey(), rtd));
			}
			final ValueReport vr = new ValueReport(targetUnit, target, vals, allPredConsistent);
			// This is a true report, save it for context grouping
			reportToContext.put(vr, context);
		}
		if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println("... done (" + suppressedECF + " filtered)");
		}
		for(final Entry<ValueReport, Collection<List<Unit>>> report : reportToContext.asMap().entrySet()) {
			final Collection<List<Unit>> contexts = report.getValue();
			final ValueReport vr = report.getKey();
			if(contexts.size() == 1) {
				this.handleValueReport(contexts.iterator().next(), vr.target, vr.targetVal, vr.allPredConsistent, Collections.<List<Unit>>emptyList());
			}
			final Pair<List<Unit>, List<List<Unit>>> repr = selectRepresentativeContext(contexts);
			this.handleValueReport(repr.getO1(), vr.target, vr.targetVal, vr.allPredConsistent, repr.getO2());
		}
		
		for(final Cell<List<Unit>, Unit, AccessGraph> kv : staticGraphs.cellSet()) {
			final List<Unit> context = kv.getRowKey();
			final Unit target = kv.getColumnKey();
			final AccessGraph targetGraph = kv.getValue();
			final RecTreeDomain result = this.resultsAt(context, target, targetGraph);
			if(result != RecTreeDomain.BOTTOM && result != null) {
				this.reportCount.incrementAndGet();
				saveValueReportSites(context, target, result);
				this.errorHandler.handleLostStaticFlow(context, target, targetGraph, result);
			}
		}
	}
	
	public RecTreeDomain filterFailing(final RecTreeDomain rd, final HashSet<String> failingProps) {
		final Node restRoot = failingProps.contains("*") ? rd.restRoot : null;
		
		final Map<String, Node> roots = new HashMap<>();
		for(final String prop : failingProps) {
			if(rd.pointwiseRoots != null && rd.pointwiseRoots.containsKey(prop) && !prop.equals("*")) {
				roots.put(prop, rd.pointwiseRoots.get(prop));
			}
		}
		if(roots.size() == 0 && restRoot == null) {
			return null;
		}
		return new RecTreeDomain(roots, restRoot);
	}
	
	private List<String> findFailingProps(final Collection<RecTreeDomain> rd) {
		final MultiMap<String, Node> props = new HashMultiMap<>();
		final List<Node> restNodes = new ArrayList<>();
		boolean hasPoint = false, hasRest = false;
		{
			for(final RecTreeDomain trd : rd) {
				if(trd == RecTreeDomain.BOTTOM) {
					continue;
				}
				if(trd.restRoot != null) {
					restNodes.add(trd.restRoot);
					hasRest = true;
				}
				if(trd.pointwiseRoots != null) {
					hasPoint = true;
					for(final Map.Entry<String, Node> kv : trd.pointwiseRoots.entrySet()) {
						props.put(kv.getKey(), kv.getValue());
					}
				}
			}
		}
		if(hasRest && hasPoint) {
			final HashSet<String> s = new HashSet<>();
			s.addAll(props.keySet());
			s.add("*");
			return new ArrayList<>(s);
		} else if(hasPoint) {
			final ArrayList<String> toRet = new ArrayList<>();
			outer: for(final String p : props.keySet()) {
				Node node = null;
				for(final Node rtd : props.get(p)) {
					if(node == null) {
						node = rtd;
					} else {
						node = node.joinWith(rtd);
					}
					if(node == null) {
						toRet.add(p);
						continue outer;
					}
				}
			}
			return toRet;
		} else if(hasRest) {
			return Collections.singletonList("*");
		} else {
			return null;
		}
	}

	private static <A> int lexiCompare(final List<? extends A> l1, final List<? extends A> l2, final Comparator<? super A> cmp) {
		final int m = Math.min(l1.size(), l2.size());
		for(int i = 0; i < m; i++) {
			final A e1 = l1.get(i);
			final A e2 = l2.get(i);
			final int c = cmp.compare(e1, e2);
			if(c != 0) {
				return c;
			}
		}
		return l1.size() - l2.size();
	}

	private int unitTotalOrder(final Unit u1, final Unit u2) {
		if(u1 == u2) {
			return 0;
		}
		final SootMethod m1 = icfg.getMethodOf(u1);
		final SootMethod m2 = icfg.getMethodOf(u2);
		if(m1 != m2) {
			return m1.getSignature().compareTo(m2.getSignature());
		}
		assert m1.getSignature().equals(m2.getSignature()) : m1 + " " + m2;
		final String s1 = u1.toString();
		final String s2 = u2.toString();
		final int c = s1.compareTo(s2);
		if(c != 0) {
			return c;
		}
		for(final Unit u : m1.getActiveBody().getUnits()) {
			if(u == u1) {
				return -1;
			} else if(u == u2) {
				return 1;
			}
		}
		throw new RuntimeException();
	}
	
	private final Comparator<Unit> unitComparator = new Comparator<Unit>() {
		@Override
		public int compare(final Unit u1, final Unit u2) {
			return unitTotalOrder(u1, u2);
		}
	};
	
	Comparator<List<Unit>> contextComparator = new Comparator<List<Unit>>() {
		@Override
		public int compare(final List<Unit> l1, final List<Unit> l2) {
			return lexiCompare(l1, l2, unitComparator);
		}
	};

	private Pair<List<Unit>, List<List<Unit>>> selectRepresentativeContext(final Collection<List<Unit>> contexts) {
		final List<List<Unit>> contextList = new ArrayList<>(contexts);
		Collections.sort(contextList, contextComparator);
		return new Pair<>(contextList.get(0), contextList.subList(1, contextList.size()));
	}

	private boolean isRecursiveCycle(final EdgeFunctionPair value) {
		final EdgeFunction<RecTreeDomain> f1 = value.getO1();
		final EdgeFunction<RecTreeDomain> f2 = value.getO2();
		
		final Map<String, List<List<Symbol>>> p1 = enumeratePaths(f1);
		final Map<String, List<List<Symbol>>> p2 = enumeratePaths(f2);

		if(p1 == null) {
			return true;
		}
		if(p2 == null) {
			return true;
		}
		final Set<String> commonKeys = new HashSet<>(p1.keySet());
		commonKeys.retainAll(p2.keySet());
		for(final String commonKey : commonKeys) {
			if(hasRepeatedPrefix(p1.get(commonKey), p2.get(commonKey))) {
				return true;
			}
		}
		return false;
	}
	
	private static class WorklistNode {
		private final List<List<Symbol>> suff1;
		private final List<List<Symbol>> suff2;
		final int idx;
		
		public WorklistNode(final List<List<Symbol>> suff1, final List<List<Symbol>> suff2, final int idx) {
			this.idx = idx;
			this.suff1 = suff1;
			this.suff2 = suff2;
		}
		
		public List<List<Symbol>> get(final int i) {
			if(i == 0) {
				return suff1;
			} else {
				assert i == 1;
				return suff2;
			}
		}
	}

	private boolean hasRepeatedPrefix(final List<List<Symbol>> in1, final List<List<Symbol>> in2) {
		final LinkedList<WorklistNode> worklist = new LinkedList<>();
		worklist.add(new WorklistNode(in1, in2, -1));
		while(!worklist.isEmpty()) {
			final WorklistNode wn = worklist.removeFirst();
			final int nextIdx = wn.idx + 1;
			
			final List<List<Symbol>> l1 = wn.suff1;
			final List<List<Symbol>> l2 = wn.suff2;
			
			final Map<String, WorklistNode> split = new HashMap<>();

			if(splitAndCheck(nextIdx, l1, l2, split, 0)) {
				return true;
			}
			
			if(splitAndCheck(nextIdx, l2, l1, split, 1)) {
				return true;
			}
			worklist.addAll(split.values());
		}
		return false;
	}

	public boolean splitAndCheck(final int nextIdx, final List<List<Symbol>> curr, final List<List<Symbol>> other,
			final Map<String, WorklistNode> split, final int i) {
		for(final List<Symbol> c : curr) {
			final int nextSymbol = translateIdx(nextIdx, c);
			if(nextSymbol < 0 && checkRepeat(c, other, nextIdx)) {
				return true;
			}
			if(nextSymbol < 0) {
				continue;
			}
			final String key = Objects.toString(c.get(nextSymbol));
			if(!split.containsKey(key)) {
				split.put(key, new WorklistNode(new ArrayList<List<Symbol>>(), new ArrayList<List<Symbol>>(), nextIdx));
			}
			split.get(key).get(i).add(c);
		}
		return false;
	}

	public int translateIdx(final int fromRight, final List<Symbol> c) {
		return (c.size() - 1) - fromRight;
	}

	private boolean checkRepeat(final List<Symbol> c, final List<List<Symbol>> l2, final int nextIdx) {
		for(final List<Symbol> other : l2) {
			final int idx = translateIdx(nextIdx, other);
			if(idx < 0) {
				continue;
			}
			final int numRemaining = idx + 1;
			if(numRemaining > c.size()) {
				continue;
			}
			final List<Symbol> restPrefix = other.subList(0, numRemaining);
			if(listStartsWith(c, restPrefix)) {
				return true;
			}
		}
		return false;
	}

	private boolean listStartsWith(final List<Symbol> c, final List<Symbol> restPrefix) {
		for(int i = 0; i < restPrefix.size(); i++) {
			if(!Objects.equals(c.get(i), restPrefix.get(i))) {
				return false;
			}
		}
		return true;
	}

	private Map<String, List<List<Symbol>>> enumeratePaths(final EdgeFunction<RecTreeDomain> o1) {
		if(o1 instanceof AllBottom) {
			return null;
		}
		if(o1 instanceof AllTop) {
			return null;
		}
		RecTreeDomain toEnum;
		if(o1 instanceof PrependFunction) {
			toEnum = ((PrependFunction) o1).paramTree;
		} else if(o1 instanceof TreeFunction) {
			if(((TreeFunction) o1).isBottom()) {
				return null;
			}
			toEnum = ((TreeFunction) o1).tree;
		} else if(o1 instanceof Identity || o1 instanceof EffectEdgeIdentity) {
			final Map<String, List<List<Symbol>>> ret = new HashMap<>();
			ret.put("*", Collections.singletonList(Collections.<Symbol>singletonList(null)));
			return ret;
		} else {
			throw new RuntimeException("Unexpected function type " + o1);
		}
		toEnum = toEnum.narrowForSerialize();
		final Map<String, List<List<Symbol>>> toReturn = new HashMap<>();
		if(toEnum.restRoot != null) {
			toReturn.put("*", enumeratePaths(toEnum.restRoot));
		}
		if(toEnum.pointwiseRoots != null) {
			for(final Map.Entry<String, Node> kv : toEnum.pointwiseRoots.entrySet()) {
				toReturn.put(kv.getKey(), enumeratePaths(kv.getValue()));
			}
		}
		return toReturn;
	}
	
	private List<List<Symbol>> enumeratePaths(final Node root) {
		final List<List<Symbol>> toReturn = new ArrayList<>();
		final List<Symbol> curr = new ArrayList<>();
		final TreeVisitor tv[] = new TreeVisitor[1];
		tv[0] = new TreeVisitor() {
			@Override
			public void visitTransitionNode(final TransitiveNode transitiveNode) {
				for(int i = 0; i < transitiveNode.transitions.length; i++) {
					if(transitiveNode.transitions[i] != null) {
						curr.add(transitiveNode.transitionSymbol(i));
						transitiveNode.transitions[i].visit(tv[0]);
						removeLast();
					}
				}
			}
			
			@Override
			public void visitPrime(final PrimingNode primingNode) {
				curr.add(null);
				addToResult();
			}
			
			@Override
			public void visitParamNode(final ParamNode paramNode) {
				curr.add(null);
				addToResult();
			}
			
			@Override
			public void visitCompressedNode(final CompressedTransitiveNode compressedTransitiveNode) {
				compressedTransitiveNode.dedup().visit(tv[0]);
			}
			
			@Override
			public void visitCallNode(final CallNode callNode) {
				final CallSymbol cs = new CallSymbol(callNode.callId, callNode.prime, callNode.nodeRole);
				curr.add(cs);
				if(callNode.next == null) {
					addToResult();
					return;
				}
				callNode.next.visit(tv[0]);
				removeLast();
			}
			
			private void addToResult() {
				toReturn.add(new ArrayList<Symbol>(curr));
				removeLast();
			}

			public void removeLast() {
				curr.remove(curr.size() - 1);
			}
		};
		root.visit(tv[0]);
		return toReturn;
	}

	private boolean isLoopHead(final Unit target, final SootMethod m) {
		for(final Loop l : loopCache.getUnchecked(m)) {
			if(!l.getLoopStatements().contains(target)) {
				continue;
			}
			if(target.equals(l.getHead())) {
				return true;
			}
		}
		return false;
	}

	private boolean isErrorCarriedForward(final Collection<IncomingEdge> incomingEdges, final Unit target) {
		boolean allConsistent = true;
		for(final IncomingEdge ie : incomingEdges) {
			if(isBottomFunction(ie.incoming)) {
				allConsistent = false;
				break;
			}
		}
		if(allConsistent) {
			return false;
		}
		final LoopFinder lf = new LoopFinder();
		lf.transform(icfg.getMethodOf(target).getActiveBody());
		for(final Loop l : lf.loops()) {
			if(l.getLoopStatements().contains(target)) {
				return false;
			}
		}
		return true;
	}
	
	private static class IncomingEdge {
		public final Unit src;
		public final AccessGraph srcFact;
		public final EdgeFunction<RecTreeDomain> incoming;
		public final EdgeFunction<RecTreeDomain> stepFunction;
		
		public IncomingEdge(final Unit src, final AccessGraph srcFact, final EdgeFunction<RecTreeDomain> incoming,
				final EdgeFunction<RecTreeDomain> stepFunction) {
			this.src = src;
			this.srcFact = srcFact;
			this.incoming = incoming;
			this.stepFunction = stepFunction;
		}

		@Override
		public String toString() {
			return "IncomingEdge [src=" + src + ", srcFact=" + srcFact + ", incoming=" + incoming + "]";
		}
	}

	private Collection<IncomingEdge> getReversedFlow(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal) {
		final List<IncomingEdge> toReturn = new ArrayList<>();
		for(final Unit pred : icfg.getPredsOf(target)) {
			final Map<AccessGraph, EdgeFunction<RecTreeDomain>> fn = jumpFn.forwardLookup(sourceVal, pred);
			for(final Map.Entry<AccessGraph, EdgeFunction<RecTreeDomain>> kv : fn.entrySet()) {
				final AccessGraph factAtPred = kv.getKey();
				final EdgeFunction<RecTreeDomain> predecessorFunction = kv.getValue();
				if(icfg.isCallStmt(pred)) {
					for(final SootMethod dest : icfg.getCalleesOfCallAt(pred)) {
						final Unit sP = icfg.getStartPointsOf(dest).iterator().next();
						for(final AccessGraph startFact : this.ffCache.getCallFlowFunction(pred, dest).computeTargets(factAtPred)) {
							final Set<Cell<Unit, AccessGraph, EdgeFunction<RecTreeDomain>>> endSumm = endSummary(sP, startFact);
							for(final Cell<Unit, AccessGraph, EdgeFunction<RecTreeDomain>> summ : endSumm) {
								final AccessGraph exitFact = summ.getColumnKey();
								final Unit exitSite = summ.getRowKey();
								final Set<AccessGraph> flowThroughFacts = this.ffCache.getReturnFlowFunction(pred, dest, exitSite, target).computeTargets(exitFact);
								if(flowThroughFacts.contains(targetVal)) {
									final EdgeFunction<RecTreeDomain> callFunction = this.efCache.getCallEdgeFunction(pred, factAtPred, dest, startFact);
									final EdgeFunction<RecTreeDomain> returnFunction = this.efCache.getReturnEdgeFunction(pred, dest, exitSite, exitFact, target, targetVal);
									final EdgeFunction<RecTreeDomain> incomingFunction = predecessorFunction.composeWith(callFunction).composeWith(summ.getValue()).composeWith(returnFunction);
									final EdgeFunction<RecTreeDomain> stepFn = callFunction.composeWith(summ.getValue()).composeWith(returnFunction);
									toReturn.add(new IncomingEdge(pred, factAtPred, incomingFunction, stepFn));
								}
							}
						}
					}
					final Set<AccessGraph> flowOverFacts = this.ffCache.getCallToReturnFlowFunction(pred, target).computeTargets(factAtPred);
					if(flowOverFacts.contains(targetVal)) {
						final EdgeFunction<RecTreeDomain> flowOverFunction = this.efCache.getCallToReturnEdgeFunction(pred, factAtPred, target, targetVal);
						final EdgeFunction<RecTreeDomain> total = predecessorFunction.composeWith(flowOverFunction);
						toReturn.add(new IncomingEdge(pred, factAtPred, total, flowOverFunction));
					}
				} else {
					if(this.ffCache.getNormalFlowFunction(pred, target).computeTargets(factAtPred).contains(targetVal)) {
						final EdgeFunction<RecTreeDomain> normalFlowFunction = this.efCache.getNormalEdgeFunction(pred, factAtPred, target, targetVal);
						toReturn.add(new IncomingEdge(pred, factAtPred, predecessorFunction.composeWith(normalFlowFunction), normalFlowFunction));
					}
				}
			}
		}
		return toReturn;
	}
	
	private void handleValueReport(final List<Unit> context, final Unit u, final AccessGraph ag, final boolean allPredConsistent, final List<List<Unit>> dupContexts) {
		final RecTreeDomain result = this.resultsAt(context, u, ag);
		if(result == RecTreeDomain.BOTTOM) {
			reportValueError(context, ag, u, allPredConsistent, dupContexts);
		} else {
			reportTimeoutError(context, ag, u, result, dupContexts);
		}
	}
	
	private boolean isLiveReport(final Unit target, final AccessGraph targetVal) {
		return targetVal.isStatic() ||
				isInitialFlow(target, targetVal) ||
				isParamField(target, targetVal) ||
				livenessCache.getUnchecked(icfg.getMethodOf(target)).getLiveLocalsBefore(target).contains(targetVal.getBase());
	}
	
	private boolean isInitialFlow(final Unit target, final AccessGraph targetVal) {
		final SootMethod m = icfg.getMethodOf(target);
		return icfg.getStartPointsOf(m).contains(target);
	}

	private boolean isParamField(final Unit target, final AccessGraph targetVal) {
		final SootMethod m = icfg.getMethodOf(target);
		return m.getActiveBody().getParameterLocals().contains(targetVal.getBase()) && AtMostOnceProblem.propagateThroughCall(targetVal);
	}
	
	private Table<List<Unit>, AccessGraph, RecTreeDomain> computeReachingValues(final List<Unit> context, final Unit u, final AccessGraph ag) {
		final SootMethod containingMethod = icfg.getMethodOf(u);
		if(icfg.getStartPointsOf(containingMethod).contains(u)) {
			return computeIncomingValues(context, u, ag);
		}
		final Map<AccessGraph, EdgeFunction<RecTreeDomain>> rv = jumpFn.reverseLookup(u, ag);
		final Unit sp = icfg.getStartPointsOf(icfg.getMethodOf(u)).iterator().next();
		final Table<List<Unit>, AccessGraph, RecTreeDomain> joinedValues = HashBasedTable.create();
		for(final Map.Entry<AccessGraph, EdgeFunction<RecTreeDomain>> f : rv.entrySet()) {
			joinedValues.put(context, f.getKey(), f.getValue().computeTarget(val(context, sp, f.getKey())));
		}
		return joinedValues;

	}

	private Table<List<Unit>, AccessGraph, RecTreeDomain> computeIncomingValues(final List<Unit> context, final Unit u, final AccessGraph ag) {
		final SootMethod destMethod = icfg.getMethodOf(u);
		final Unit callSite = context.get(context.size() - 1);
		final SootMethod calleeMethod = icfg.getMethodOf(callSite);
		
		final Map<Unit, Set<AccessGraph>> s = this.incoming(ag, u);
		assert s.containsKey(callSite);
		final Set<AccessGraph> callerFacts = s.get(callSite);
		final Table<List<Unit>, AccessGraph, RecTreeDomain> toReturn = HashBasedTable.create();
		for(final List<Unit> calleeContext : this.getContextsOfMethod(calleeMethod)) {
			if(kcfaManager.extend(calleeContext, callSite) != context) {
				continue;
			}
			for(final AccessGraph argFact : callerFacts) {
				final RecTreeDomain valueAtCall = val(calleeContext, callSite, argFact);
				final EdgeFunction<RecTreeDomain> callFunction = this.efCache.getCallEdgeFunction(callSite, argFact, destMethod, ag);
				final RecTreeDomain enteringCall = callFunction.computeTarget(valueAtCall);
				toReturn.put(calleeContext, argFact, enteringCall);
			}			
		}
		return toReturn;
	}

	private void reportValueError(final List<Unit> context, final AccessGraph ag, final Unit u, final boolean allPredConsistent, final List<List<Unit>> dupContexts) {
		if(isLiveReport(u, ag)) {
			final Table<List<Unit>, AccessGraph, RecTreeDomain> inputValues = computeReachingValues(context, u, ag);
			for(final RecTreeDomain rd : inputValues.values()) {
				recordSites(rd);
			}
			recordSite(u); recordSite(context);
			saveDupContexts(dupContexts);
			assert resultsAt(context, u, ag) == RecTreeDomain.BOTTOM : 
				errorGraphs.get(context, u) + " " + context + " " + ag + " " + u + " " + resultsAt(context, u, ag) + " " + icfg.getMethodOf(u);
			errorHandler.handleInconsistentValue(context, u, ag, inputValues, allPredConsistent, dupContexts);
			reportCount.incrementAndGet();
		}
	}

	private void saveDupContexts(final List<List<Unit>> dupContexts) {
		for(final List<Unit> otherContext : dupContexts) {
			recordSite(otherContext);
		}
	}

	private void recordSite(final Unit u) {
		if(u == this.contextResolver.initialContext().get(0)) {
			return;
		}
		synchronized(recordedSites) {
			recordedSites.add(AtMostOnceProblem.getUnitNumber(u));
		}
	}
	
	private void saveValueReportSites(final List<Unit> context, final Unit target, final RecTreeDomain result) {
		recordSite(target); recordSite(context); recordSites(result);
	}
	
	private void recordSite(final List<Unit> context) {
		for(final Unit u : context) {
			recordSite(u);
		}
	}

	private void reportTimeoutError(final List<Unit> context, final AccessGraph ag, final Unit u, final RecTreeDomain result, final List<List<Unit>> dupContexts) {
		if(isLiveReport(u, ag)) {
			saveValueReportSites(context, u, result);
			saveDupContexts(dupContexts);
			errorHandler.handleHeapTimeout(context, u, ag, result);
			reportCount.incrementAndGet();
		}
	}

	
	private final TIntHashSet recordedSites = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
	
	private void recordSites(final RecTreeDomain d) {
		if(!trackSites) {
			return;
		}
		if(d == null) {
			return;
		}
		d.walk(new AbstractTreeVisitor() { 
			@Override
			public void visitTransitionNode(final TransitiveNode transitiveNode) {
				synchronized(recordedSites) {
					recordedSites.add(transitiveNode.callId);
				}
			}
			
			@Override
			public void visitCompressedNode(final CompressedTransitiveNode compressedTransitiveNode) {
				synchronized(recordedSites) {
					recordedSites.add(compressedTransitiveNode.callId);
				}
			}
			
			@Override
			public void visitCallNode(final CallNode callNode) {
				synchronized(recordedSites) {
					recordedSites.add(callNode.callId);
				}
			}
		});
	}
	
	private void recordSites(final EdgeFunction<RecTreeDomain> f) {
		if(!trackSites) {
			return;
		}
		if(f instanceof TreeEncapsulatingFunction) {
			recordSites(((TreeEncapsulatingFunction) f).getTree());
		}
	}

	public int numReports() {
		return reportCount.get();
	}

	public void finishHandler() {
		if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println(nonStaticTime.get());
			System.out.println(staticTime.get());
		}
		lft.dumpStats();
		errorHandler.finish();
	}
	
	public void dumpRelevantSites(final PrintWriter pw) {
		final Numberer<Unit> un = Scene.v().getUnitNumberer();
		final TIntIterator it = recordedSites.iterator();
		final List<Object> l = new ArrayList<>();
		while(it.hasNext()) {
			final int sId = it.next();
			final Stmt s = (Stmt) un.get(sId);
			if(s == contextResolver.initialContext().get(0)) {
				continue;
			}
			final Map<String, Object> p = new HashMap<>();
			p.put("tag", sId);
			final SootMethod containingMethod = icfg.getMethodOf(s);
			if(!s.hasTag("LineNumberTag") && s instanceof NopStmt) {
				for(final Unit u : containingMethod.getActiveBody().getUnits()) {
					if(u.hasTag("LineNumberTag")) {
						p.put("line", u.getTag("LineNumberTag").toString());						
					}
				}
			} else {
				p.put("line", s.getTag("LineNumberTag") != null ? s.getTag("LineNumberTag").toString() : null);
			}
			final SootClass declaringClass = containingMethod.getDeclaringClass();
			p.put("containing-type", declaringClass.getName());
			p.put("containing-method", containingMethod.toString());
			p.put("unit-string", s.toString());
			if(declaringClass.hasTag("SourceFileTag")) {
				p.put("source-file", declaringClass.getTag("SourceFileTag").toString());
			}
			if(s.containsInvokeExpr()) {
				p.put("method", s.getInvokeExpr().getMethod().getSignature());
			}
			l.add(p);
		}
		final DumperOptions dOpt = new DumperOptions();
		dOpt.setWidth(Integer.MAX_VALUE);
		final Yaml y = new Yaml(dOpt);
		y.dump(l, pw);
	}
	
}
