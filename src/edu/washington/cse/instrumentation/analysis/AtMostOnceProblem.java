package edu.washington.cse.instrumentation.analysis;

import gnu.trove.TIntCollection;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.hash.TIntHashSet;
import heros.DefaultSeeds;
import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.FlowFunctions;
import heros.JoinLattice;
import heros.edgefunc.AllTop;
import heros.solver.Pair;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import soot.MethodOrMethodContext;
import soot.NullType;
import soot.RefLikeType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JimpleLocal;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.FlowSet;
import soot.util.MapNumberer;
import soot.util.Numberer;
import boomerang.accessgraph.AccessGraph;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.functions.AbstractPathFunctions;
import edu.washington.cse.instrumentation.analysis.functions.CallParamDeciderProvider;
import edu.washington.cse.instrumentation.analysis.functions.ForwardFlowFunctions;
import edu.washington.cse.instrumentation.analysis.functions.LegatoEdgeFunctions;
import edu.washington.cse.instrumentation.analysis.preanalysis.AtMostOnceExecutionAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.DirectedCallGraph;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.MethodSynchronizationInfo;
import edu.washington.cse.instrumentation.analysis.preanalysis.NullSyncPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.SingletonTypeAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.SyncPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.TransitivityAnalysis;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;
import edu.washington.cse.instrumentation.analysis.solver.DefaultLegatoJimpleIDETabulationProblem;
import edu.washington.cse.instrumentation.analysis.solver.SolverAwareFlowFunctions;

public class AtMostOnceProblem extends DefaultLegatoJimpleIDETabulationProblem<Unit, AccessGraph, SootMethod, RecTreeDomain, JimpleBasedInterproceduralCFG> {
	static Numberer<Object> phiNumberer = new MapNumberer<>();
	protected final ResourceResolver resourceResolver;
	protected final PropagationManager propagationManager;
	private final FieldPreAnalysis fieldAnalysis;
	
	private final AtMostOnceExecutionAnalysis atmoFieldAnalysis;
	private final CallParamDeciderProvider paramDeciderCache;
	private final SyncPreAnalysis spa;
	private AnalysisConfiguration config;
	
	private SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> flowFunctions;
	private EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> edgeFunctions;
	private JimpleBasedInterproceduralCFG icfg;
	
	// caches
	private final LoadingCache<SootMethod, TIntCollection> nonTrAccessSiteCache;
	private final LoadingCache<Integer, LabelContainer> labelSiteCache;
	private final LoadingCache<SootMethod, TIntCollection> nonTrTransitiveSiteCache;
	private final LoadingCache<SootMethod, TIntCollection> nonTrSyncSiteCache;
	private TransitivityAnalysis transitivityAnalysis;
	public static enum SummaryMode {
		IGNORE,
		WARN,
		BOTTOM,
		FAIL
	}

	public AtMostOnceProblem(final AnalysisConfiguration c) {
		super(c.icfg);
		this.propagationManager = c.propagationManager;
		assert instance == null : "Double init";
		this.fieldAnalysis = new FieldPreAnalysis(c.icfg, propagationManager, c.aliasResolver.containerContentField);
		if(c.syncHavoc) {
			spa = new SyncPreAnalysis(fieldAnalysis, c.icfg, c.propagationManager, c.aliasResolver);
		} else {
			spa = new NullSyncPreAnalysis();
		}
		this.atmoFieldAnalysis = new AtMostOnceExecutionAnalysis(fieldAnalysis, c.ignoredMethods, c.resourceResolver, c.icfg);
		
		this.resourceResolver = new ResourceResolver() {
			private final ResourceResolver delegate = c.resourceResolver;
			
			@Override
			public boolean isResourceMethod(final SootMethod m) {
				return delegate.isResourceMethod(m);
			}
			
			@Override
			public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
				if(!delegate.isResourceAccess(ie, u)) {
					return false;
				}
				final Set<String> acc = this.getAccessedResources(ie, u);
				if(acc == null) {
					return true;
				}
				return !acc.isEmpty();
			}
			
			@Override
			public Collection<SootMethod> getResourceAccessMethods() {
				return delegate.getResourceAccessMethods();
			}
			
			@Override
			public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
				final Set<String> acc = delegate.getAccessedResources(ie, u);
				if(acc == null) {
					return null;
				}
				if(c.trackAll) {
					return acc;
				}
				return atmoFieldAnalysis.filterResource(acc);
			}
		};
		this.config = c.copyWithResolver(resourceResolver);
		this.transitivityAnalysis = new TransitivityAnalysis(resourceResolver, AtMostOnceProblem.makeDirectedCallGraph(c.icfg), c.icfg, spa);
		
		this.paramDeciderCache = new CallParamDeciderProvider(this.config.icfg, zeroValue(), fieldAnalysis);
		flowFunctions = createFlowFunctionsFactory();
		edgeFunctions = createEdgeFunctionsFactory();
		
		instance = this;
		this.icfg = c.icfg;
		nonTrAccessSiteCache = config.cacheBuilder().recordStats().build(new CacheLoader<SootMethod, TIntCollection>() {
			@Override
			public TIntCollection load(final SootMethod key) throws Exception {
				if(!key.hasActiveBody()) {
					return EMPTY_SET;
				}
				TIntCollection toRet = null;
				for(final Unit u : key.getActiveBody().getUnits()) {
					final Stmt s = (Stmt) u;
					if(!s.containsInvokeExpr()) {
						continue;
					}
					if(AbstractPathFunctions.isPhantomMethodCall(s, s.getInvokeExpr().getMethod(), icfg)) {
						continue;
					}
					if(resourceResolver.isResourceAccess(s.getInvokeExpr(), s)) {
						if(toRet == null) {
							toRet = new TIntHashSet(key.getActiveBody().getUnits().size() / 3, Constants.DEFAULT_LOAD_FACTOR, -1);
						}
						toRet.add(getUnitNumber(s));
					}
				}
				if(toRet == null) {
					return EMPTY_SET;
				} else {
					return toRet;
				}
			}
		});
		
		labelSiteCache = config.cacheBuilder().build(new CacheLoader<Integer, LabelContainer> () {
			@Override
			public LabelContainer load(final Integer l) throws Exception {
				final Unit u = Scene.v().getUnitNumberer().get(l);
				final TIntCollection trSites = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
				final TIntCollection accessSites = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
				final TIntCollection syncSites = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
				assert u instanceof Stmt;
				accessSites.addAll(getTransitiveAccessesAtCall(u));
				for(final SootMethod m : interproceduralCFG().getCalleesOfCallAt(u)) {
					trSites.addAll(getTransitiveTransitiveSitesOf(m));
				}
				trSites.add(l);
				syncSites.addAll(getTransitiveSyncsAtCall(u));
				final Pair<TIntCollection, TIntCollection> toRet = new Pair<>(trSites, accessSites);
				assert noStaticLabels(toRet) : new RuntimeException() + " and " + l;
				return new LabelContainer(trSites, accessSites, syncSites);
			}
		});
		
		nonTrTransitiveSiteCache = config.cacheBuilder().build(new CacheLoader<SootMethod, TIntCollection>() {
			@Override
			public TIntCollection load(final SootMethod key) throws Exception {
				if(!key.hasActiveBody()) {
					return EMPTY_SET;
				}
				final TIntCollection toReturn = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
				for(final Unit u : key.getActiveBody().getUnits()) {
					final Stmt s = (Stmt) u;
					if(!s.containsInvokeExpr()) {
						continue;
					}
					if(resourceResolver.isResourceAccess(s.getInvokeExpr(), s)) {
						continue;
					}
					if(AbstractPathFunctions.isPhantomMethodCall(s, s.getInvokeExpr().getMethod(), icfg)) {
						continue;
					}
					final Collection<SootMethod> callees = icfg.getCalleesOfCallAt(u);
					if(callees.size() > 1 ||
							(callees.size() == 1 && transitivityAnalysis.methodNeedsLabel(callees.iterator().next()))) {
						toReturn.add(getUnitNumber(u));
					}
				}
				if(toReturn.size() == 0) {
					return EMPTY_SET;
				}
				return toReturn;
			}
		});
		
		nonTrSyncSiteCache = config.cacheBuilder().build(new CacheLoader<SootMethod, TIntCollection>() {
			@Override
			public TIntCollection load(final SootMethod key) throws Exception {
				if(!spa.getMethodSyncInfo().containsKey(key)) {
					return EMPTY_SET;
				}
				final MethodSynchronizationInfo msi = spa.getMethodSyncInfo().get(key);
				final TIntCollection toReturn = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
				if(msi.waitStmts != null) {
					toReturn.addAll(msi.waitStmts);
				}
				if(msi.syncPoints != null) {
					toReturn.addAll(msi.syncPoints);
				}
				if(msi.volatileReads != null) {
					toReturn.addAll(msi.volatileReads);
				}
				return toReturn;
			}
		});
	}
	
	public boolean methodAccessesResource(final SootMethod m) {
		return transitivityAnalysis.methodAccessesResource(m);
	}

	public static DirectedGraph<SootMethod> makeDirectedCallGraph(final JimpleBasedInterproceduralCFG icfg) {
		final DirectedGraph<SootMethod> callGraph = new DirectedCallGraph(icfg, new LegatoEdgePredicate(), Scene.v().getEntryPoints().iterator());
		return callGraph;
	}

	private final EdgePredicate synthEdgePredicate = new LegatoEdgePredicate();
	
	FlowSet<SootField> usedStaticFields(final SootMethod m) {
		return fieldAnalysis.usedStaticFields(m);
	}
	
	public static AtMostOnceProblem instance = null;
	
	static {
		grph.GrphWebNotifications.enabled = false;
	}
	
	@Override
	public Map<Unit, Set<AccessGraph>> initialSeeds() {
		final List<SootMethod> m = Scene.v().getEntryPoints();
		return DefaultSeeds.make(interproceduralCFG().getStartPointsOf(m.get(0)), zeroValue());
	}

	@Override
	protected EdgeFunction<RecTreeDomain> createAllTopFunction() {
		return new AllTop<RecTreeDomain>(RecTreeDomain.TOP);
	}

	@Override
	protected JoinLattice<RecTreeDomain> createJoinLattice() {
		return new JoinLattice<RecTreeDomain>() {

			@Override
			public RecTreeDomain topElement() {
				return RecTreeDomain.TOP;
			}

			@Override
			public RecTreeDomain bottomElement() {
				return RecTreeDomain.BOTTOM;
			}

			@Override
			public RecTreeDomain join(final RecTreeDomain left,
					final RecTreeDomain right) {
				return left.joinWith(right);
			}
		};
	}
	
	protected boolean isUntaggedGraph(final AccessGraph ag) {
		if(!ag.isStatic()) {
			return false;
		}
		if(ag.getFieldCount() > 1) {
			return false;
		}
		return atmoFieldAnalysis.isAtMostOnceField(ag.getFirstField().getField());
	}
		
	public static int getUnitNumber(final Unit u) {
		final Numberer<Unit> nu = Scene.v().getUnitNumberer();
		synchronized(nu) {
			nu.add(u);
			return (int) nu.get(u);	
		}
	}
	
	private final Table<Unit, AccessGraph, Set<AccessGraph>> synchReadLookup = HashBasedTable.create();

	protected EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> createEdgeFunctionsFactory() {
		return new LegatoEdgeFunctions(config, zeroValue(), spa, fieldAnalysis, synchReadLookup, paramDeciderCache, atmoFieldAnalysis, transitivityAnalysis);
	}

	/**
	 * Returns a collection of transitively reachable labels of transitive access sites from within m.
	 * A transitive access site is not an access site, but a method call that EVENTUALLY
	 * can reach a true access site 
	 */
	public TIntCollection getTransitiveTransitiveSitesOf(final SootMethod m) {
		final TransitiveTargets tt = ttCache.get();
		final Iterator<MethodOrMethodContext> it = tt.iterator(m);
		final TIntHashSet callSites = new TIntHashSet(11, 0.8f, -1);
		while(it.hasNext()) {
			final SootMethod im = it.next().method();
			if(im.isStaticInitializer()) {
				continue;
			}
			callSites.addAll(nonTrTransitiveSites(im));
		}
		callSites.addAll(nonTrTransitiveSites(m));
		return callSites;
	}
	
	private TIntCollection nonTrTransitiveSites(final SootMethod m) {
		return nonTrTransitiveSiteCache.getUnchecked(m);
	}

	/**
	 * Returns a collection of transitively reachable labels of access sites from within m.
	 * An access site is when the specified resource access method is called directly
	 */
	private TIntHashSet getTransitiveAccessesOf(final SootMethod m) {
		final TransitiveTargets tt = ttCache.get();
		final Iterator<MethodOrMethodContext> it = tt.iterator(m);
		final TIntHashSet callSites = new TIntHashSet(11, 0.8f, -1);
		while(it.hasNext()) {
			final SootMethod im = it.next().method();
			if(im.isStaticInitializer()) {
				continue;
			}
			callSites.addAll(nonTrCallSites(im));
		}
		callSites.addAll(nonTrCallSites(m));
		return callSites;
	}
	
	public Stopwatch timer = Stopwatch.createUnstarted();
	
	public LabelContainer getSitesForLabel(final int l) {
		timer.start();
		final LabelContainer toReturn;
		toReturn = labelSiteCache.getUnchecked(l);
		timer.stop();
		return toReturn;
	}
	
	private boolean noStaticLabels(final Pair<TIntCollection, TIntCollection> toRet) {
		final TIntIterator iterator = toRet.getO2().iterator();
		while(iterator.hasNext()) {
			final int v = iterator.next();
			if(interproceduralCFG().getMethodOf(Scene.v().getUnitNumberer().get(v)).isStaticInitializer()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns a collection of transitively reachable labels of access sites. Reachability is
	 * started from the call at u
	 */
	private TIntCollection getTransitiveAccessesAtCall(final Unit u) {
		assert u instanceof Stmt;
		final Stmt s = (Stmt) u;
		final TIntHashSet toRet = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		for(final SootMethod m : interproceduralCFG().getCalleesOfCallAt(u)) {
			if(m.isStaticInitializer()) {
				continue;
			}
			toRet.addAll(getTransitiveAccessesOf(m));
		}
		if(resourceResolver.isResourceAccess(s.getInvokeExpr(), s)) {
			toRet.add(getUnitNumber(u));
		}
		return toRet;
	}
	
	private final static TIntHashSet EMPTY_SET = new TIntHashSet();
	
	public static class LabelContainer {
		public final TIntCollection transitiveTransitiveLabels;
		public final TIntCollection transitiveAccessLabels;
		public final TIntCollection transitiveSynchLabels;
		public LabelContainer(final TIntCollection trTransLabels, final TIntCollection trAccessLabels, final TIntCollection trSyncLabels) {
			this.transitiveTransitiveLabels = trTransLabels;
			this.transitiveAccessLabels = trAccessLabels;
			this.transitiveSynchLabels = trSyncLabels;
		}
		@Override
		public String toString() {
			return "LabelContainer [transitiveTransitiveLabels=" + transitiveTransitiveLabels + ", transitiveAccessLabels=" + transitiveAccessLabels + ", transitiveSynchLabels="
					+ transitiveSynchLabels + "]";
		}
	}
	
	
	private TIntCollection getTransitiveSyncsAtCall(final Unit u) {
		return getTransitiveSyncs(interproceduralCFG().getCalleesOfCallAt(u).iterator());
	}

	// grrr generics
	@SuppressWarnings({"unchecked", "rawtypes"})
	private TIntCollection getTransitiveSyncs(final Iterator seedIterator) {
		final TransitiveTargets tt = ttCache.get();
		final Iterator<MethodOrMethodContext> it = tt.iterator(seedIterator);
		final TIntCollection ret = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		while(it.hasNext()) {
			final SootMethod m = it.next().method();
			ret.addAll(nonTrSynchSites(m));
		}
		return ret;
	}
	
	private TIntCollection nonTrCallSites(final SootMethod m) {
		return nonTrAccessSiteCache.getUnchecked(m);
	}
	
	private TIntCollection nonTrSynchSites(final SootMethod m) {
		if(!spa.getMethodSyncInfo().containsKey(m)) {
			return EMPTY_SET;
		}
		return nonTrSyncSiteCache.getUnchecked(m);
	}

	private final ThreadLocal<TransitiveTargets> ttCache = new ThreadLocal<TransitiveTargets>() {
		@Override
		protected TransitiveTargets initialValue() {
			return new TransitiveTargets(Scene.v().getCallGraph(), new Filter(synthEdgePredicate));
		};
	};
	
	public static boolean mayAliasType(final Type ty) {
		return ty instanceof RefLikeType && ty != Scene.v().getRefType("java.lang.String");
	}
  
	public static boolean mayAlias(final AccessGraph ag) {
		return mayAliasType(ag.getRTType());
	}
	
	protected SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> createFlowFunctionsFactory() {
		final ForwardFlowFunctions delegate = new ForwardFlowFunctions(config, zeroValue(), synchReadLookup, spa, paramDeciderCache, fieldAnalysis);
		return config.extension.extendFunctions(delegate);
	}
	
	@Override
	protected AccessGraph createZeroValue() {
		return new AccessGraph(new JimpleLocal("<<zero>>", NullType.v()), NullType.v());
	}

	@Override
	public int numThreads() {
		return 1;
	}
	
	@Override
	public boolean recordEdges() {
		return false;
	}

	public void dumpResourceSites(final PrintWriter pw) {
		if(edgeFunctions instanceof LegatoEdgeFunctions) {
			((LegatoEdgeFunctions) edgeFunctions).dumpResourceSites(pw);
		}
	}
	
	public void dumpStats() {
//		if(flowFunctions instanceof ForwardFlowFunctions) {
//			((ForwardFlowFunctions)flowFunctions).dumpStats();
//		}
	}
	
	public void printWarnings(final PrintStream out) {
		if(edgeFunctions instanceof LegatoEdgeFunctions) {
			((LegatoEdgeFunctions) edgeFunctions).printWarnings(out);
		}
	}
	
	@Override
	public boolean computeValues() {
		return true;
	}
	
	public void dispose() {
		assert instance == this;
		instance = null;
	}

	public void dumpDebugStats() {
		System.out.println("Label time: " + timer.elapsed(TimeUnit.MILLISECONDS));
		System.out.println("total cache: " + labelSiteCache.stats());
		System.out.println("access cache: " + nonTrAccessSiteCache.stats());
		System.out.println("sync cache: " + nonTrSyncSiteCache.stats());
		System.out.println("transitive cache: " + nonTrTransitiveSiteCache.stats());
	}

	@Override
	public FlowFunctions<Unit, AccessGraph, SootMethod> flowFunctions() {
		return flowFunctions;
	}


	@Override
	public EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> edgeFunctions() {
		return edgeFunctions;
	}

	public static boolean propagateThroughCall(final AccessGraph ag) {
		return ag.getFieldCount() > 0 || mayAlias(ag);
	}

	public void setSolver(final InconsistentReadSolver solver) {
		this.flowFunctions.setSolver(solver);
	}
	
	public SingletonTypeAnalysis getSingletonTypeAnalysis() {
		return new SingletonTypeAnalysis(this.atmoFieldAnalysis, icfg);
	}
}
