package edu.washington.cse.instrumentation.analysis.preanalysis;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.EverythingIsInconsistentException;
import edu.washington.cse.instrumentation.analysis.LegatoEdgePredicate;
import edu.washington.cse.instrumentation.analysis.aliasing.AliasResolver;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.DefinitionType;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.RHSType;
import edu.washington.cse.instrumentation.analysis.functions.WaitCallDecider;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;
import gnu.trove.TIntCollection;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import heros.solver.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Kind;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.ExitMonitorStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NopStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.jimple.toolkits.pointer.StrongLocalMustAliasAnalysis;
import soot.toolkits.exceptions.ThrowableSet;
import soot.toolkits.exceptions.UnitThrowAnalysis;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import soot.util.queue.QueueReader;
import boomerang.AliasFinder;
import boomerang.BoomerangTimeoutException;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.cache.AliasResults;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;

public class SyncPreAnalysis {
	private final Map<SootMethod, MethodSynchronizationInfo> methodSyncInfo = new HashMap<>();
	private final Map<Unit, Set<Object>> havocGraphs = new HashMap<>();
	private final Set<Unit> monitorEnterPoints = new HashSet<>();
	private final TObjectIntHashMap<Unit> unitToSynchMarker = new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
	private final HashMultimap<SootField, RefType> syncWriteFields = HashMultimap.create();

	private final JimpleBasedInterproceduralCFG icfg;
	private final WaitCallDecider wcd = new WaitCallDecider();
	private final FieldPreAnalysis fieldAnalysis;
	private PropagationManager pm;
	private AliasResolver aliasResolver;
	
	public SyncPreAnalysis(final FieldPreAnalysis fieldAnalysis, final JimpleBasedInterproceduralCFG icfg, final PropagationManager pm, final AliasResolver resolver) {
		this.icfg = icfg;
		this.fieldAnalysis = fieldAnalysis;
		this.pm = pm;
		this.aliasResolver = resolver;
		Scene.v().getOrMakeFastHierarchy();
		assert Scene.v().hasFastHierarchy();
		this.synchronizationPreAnalysis();
	}

	protected SyncPreAnalysis() {
		this.icfg = null;
		this.fieldAnalysis = null;
	}

	private void synchronizationPreAnalysis() {
		final DirectedGraph<SootMethod> dcg = AtMostOnceProblem.makeDirectedCallGraph(icfg);
		MethodSynchronizationInfo buffer = new MethodSynchronizationInfo();
		final HashMap<SootMethod, Set<Stmt>> deferredWaitStmts = new HashMap<>();
		for(final SootMethod m : dcg) {
			// can this actually happen?
			if(!m.isConcrete()) {
				continue;
			}
			
			syncPointAnalysis(m, buffer);
			final Set<Stmt> waitStmts = volatileReadAnalysis(m, buffer);
			if(waitStmts != null) {
				deferredWaitStmts.put(m, waitStmts);
			}
			if(buffer.syncPoints != null || buffer.volatileReads != null) {
				methodSyncInfo.put(m, buffer);
				buffer = new MethodSynchronizationInfo();
			}
		}
		final Pair<HashMultimap<SootField, RefType>, Set<SootMethod>> syncWriteFields = computeSyncWriteFields();
		final Set<SootMethod> mayBeCalledSynchronized = syncWriteFields.getO2();
		
		this.syncWriteFields.putAll(syncWriteFields.getO1());
		
		if(deferredWaitStmts.size() > 0) {
			for(final Map.Entry<SootMethod, Set<Stmt>> kv : deferredWaitStmts.entrySet()) {
				final SootMethod m = kv.getKey();
				final Set<Stmt> waitCalls = kv.getValue();
				TIntCollection waitStmtIds;
				try {
					waitStmtIds = waitStatementAnalysis(m, waitCalls);
				} catch(final EverythingIsInconsistentException e) {
					waitStmtIds = null;
				}
				if(mayBeCalledSynchronized.contains(m) || waitStmtIds == null) {
					if(!methodSyncInfo.containsKey(m)) {
						final MethodSynchronizationInfo msi = new MethodSynchronizationInfo();
						msi.failStmts = waitCalls;
						methodSyncInfo.put(m, msi);
					} else {
						methodSyncInfo.get(m).failStmts = waitCalls;
					}
					continue;
				}
				
				if(methodSyncInfo.containsKey(m)) {
					methodSyncInfo.get(m).waitStmts = waitStmtIds;
				} else {
					final MethodSynchronizationInfo msi = new MethodSynchronizationInfo();
					msi.waitStmts = waitStmtIds;
					methodSyncInfo.put(m, msi);
				}
			}
		}	 
	}

	private Pair<HashMultimap<SootField, RefType>, Set<SootMethod>> computeSyncWriteFields() {
		final JimpleBasedInterproceduralCFG icfg = this.icfg;
		final Set<SootMethod> syncMethods = new HashSet<>();
		final HashMultimap<Integer, SootMethod> calledWithin = HashMultimap.create();
		final HashMultimap<SootField, RefType> syncWriteFields = HashMultimap.create(); 
		for(final Unit u : unitToSynchMarker.keySet()) {
			final Stmt s = (Stmt) u;
			final int tag = unitToSynchMarker.get(u);
			if(s.containsInvokeExpr()) {
				calledWithin.putAll(tag, icfg.getCalleesOfCallAt(u));
			}
			tagFieldWrite(syncWriteFields, s, tag);
		}
		final LegatoEdgePredicate pred = new LegatoEdgePredicate();
		final Filter filter = new Filter(new EdgePredicate() {
			@Override
			public boolean want(final Edge e) {
				final Kind k = e.kind();
				if(k == Kind.REFL_CLASS_NEWINSTANCE || k == Kind.REFL_CONSTR_NEWINSTANCE || k == Kind.NEWINSTANCE || k == Kind.REFL_INVOKE) {
					return false;
				}
				return !e.getTgt().method().isStaticInitializer() && pred.want(e);
			}
		});
		for(final Map.Entry<Integer, Collection<SootMethod>> kv : calledWithin.asMap().entrySet()) {
			final int tag = kv.getKey();
			final ReachableMethods rm = new ReachableMethods(Scene.v().getCallGraph(), kv.getValue().iterator(), filter);
			rm.update();
			for(final QueueReader<MethodOrMethodContext> it = rm.listener(); it.hasNext(); ) {
				final SootMethod m = it.next().method();
				if(!m.isConcrete()) {
					continue;
				}
				syncMethods.add(m);
				for(final Unit u : m.getActiveBody().getUnits()) {
					tagFieldWrite(syncWriteFields, (Stmt) u, tag);
				}
			}
		}
		return new Pair<>(syncWriteFields, syncMethods);
	}

	public void tagFieldWrite(final HashMultimap<SootField, RefType> syncWriteFields, final Stmt s, final int tag) {
		if(s.containsInvokeExpr()) {
			final SootMethod m = s.getInvokeExpr().getMethod();
			PropagationSpec ps = null;
			if(pm.isPropagationMethod(m) && (ps = pm.getPropagationSpec(s)) != null) {
				if(ps.getPropagationTarget().isContainerWrite()) { 
					final Local base = (Local) ((InstanceInvokeExpr)s.getInvokeExpr()).getBase();
					try {
						final AliasResults res = this.aliasResolver.doAliasSearch(new AccessGraph(base, base.getType()), s);
						for(final AccessGraph ag : res.mayAliasSet()) {
							final WrappedSootField[] repr = ag.getRepresentative();
							if(repr == null || repr.length == 0) {
								continue;
							}
							int i = repr.length - 1;
							while(i >= 0 && repr[i].getField() == aliasResolver.containerContentField) {
								i--;
							}
							if(i >= 0) {
								syncWriteFields.put(repr[i].getField(), getSynchronizingType(tag));
							}
						}
					} catch(final BoomerangTimeoutException e) { }
				}
			}
		}
		if(s instanceof AssignStmt) {
			final AssignStmt assignStmt = (AssignStmt) s;
			if(assignStmt.getLeftOp() instanceof FieldRef) {
				syncWriteFields.put(assignStmt.getFieldRef().getField(), getSynchronizingType(tag));
			}
		}
	}
	
	private final LoadingCache<Integer, RefType> syncTypeCache = CacheBuilder.newBuilder().build(new CacheLoader<Integer, RefType>() {
		@Override
		public RefType load(final Integer tag) throws Exception {
			final Unit u = Scene.v().getUnitNumberer().get(tag);
			if(u instanceof NopStmt) {
				assert icfg.getStartPointsOf(icfg.getMethodOf(u)).contains(u);
				final SootMethod m = icfg.getMethodOf(u);
				if(m.isStatic()) {
					final String dummy = m.getDeclaringClass().getName() + "$Lock";
					if(Scene.v().containsClass(dummy)) {
						return Scene.v().getSootClass(dummy).getType();
					}
					final SootClass dummyClass = new SootClass(dummy, Modifier.PUBLIC);
					dummyClass.setSuperclass(Scene.v().getObjectType().getSootClass());
					Scene.v().addClass(dummyClass);
					return dummyClass.getType();
				}
				return m.getDeclaringClass().getType();
			}
			assert u instanceof EnterMonitorStmt;
			final EnterMonitorStmt ems = (EnterMonitorStmt) u;
			final RefType rt = (RefType) ems.getOp().getType();
			return rt;
		}
		
	});
	
	public RefType getSynchronizingType(final int tag) {
		return syncTypeCache.getUnchecked(tag);
	}

	private void syncPointAnalysis(final SootMethod m, final MethodSynchronizationInfo ms) {
		final TObjectIntHashMap<Unit> methodSynchMarkers = new TObjectIntHashMap<>();
		final DirectedGraph<Unit> dg = new ExceptionalUnitGraph(m.retrieveActiveBody(), new UnitThrowAnalysis() {
			@Override
			protected UnitSwitch unitSwitch() {
				return new UnitSwitch() {
					@Override
					public void caseExitMonitorStmt(final ExitMonitorStmt s) { }
					@Override
					public void caseEnterMonitorStmt(final EnterMonitorStmt s) { }
				};
			}
			
			@Override
			protected ThrowableSet defaultResult() {
				return ThrowableSet.Manager.v().EMPTY;
			}
		}, true);
		
		final Unit initialLock;
		if(m.isSynchronized()) {
			final Collection<Unit> sp = this.icfg.getStartPointsOf(m);
			if(sp.size() != 1) {
				throw new RuntimeException("pretty sure this shouldn't happen");
			}
			final Unit startUnit = sp.iterator().next();
			methodSynchMarkers.put(startUnit, AtMostOnceProblem.getUnitNumber(startUnit));
			initialLock = startUnit;
		} else {
			initialLock = null;
		}
		class SyncAndStack {
			ArrayList<Unit> mEnter;
			
			public SyncAndStack(final Unit initialLock) {
				mEnter = new ArrayList<>(Collections.singleton(initialLock));
			}
	
			public SyncAndStack() {
				mEnter = null;
			}
	
			@Override
			public int hashCode() {
				final int prime = 31;
				int result = 1;
				result = prime * result
						+ ((mEnter == null) ? 0 : mEnter.hashCode());
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
				if(this.getClass() != obj.getClass()) {
					return false;
				}
				final SyncAndStack other = (SyncAndStack) obj;
				if(mEnter == null) {
					if(other.mEnter != null) {
						return false;
					}
				} else if(!mEnter.equals(other.mEnter)) {
					return false;
				}
				return true;
			}
			
			public int getActiveSync() {
				return AtMostOnceProblem.getUnitNumber(mEnter.get(mEnter.size() - 1));
			}
	
			@Override
			public String toString() {
				return java.util.Objects.toString(mEnter);
			}
		}
		new ForwardFlowAnalysis<Unit, SyncAndStack>(dg) {
			@Override
			protected void flowThrough(final SyncAndStack in, final Unit d, final SyncAndStack out) {
				copy(in, out);
				if(d instanceof EnterMonitorStmt) {
					if(out.mEnter == null) {
						out.mEnter = new ArrayList<>();
					} else {
						out.mEnter = new ArrayList<>(out.mEnter);
					}
					out.mEnter.add(d);
					monitorEnterPoints.add(d);
				} else if(d instanceof ExitMonitorStmt) {
					assert out.mEnter != null : d + " " + dg.getSuccsOf(d);
					if(out.mEnter.size() == 1) {
						out.mEnter = null;
					} else {
						out.mEnter = new ArrayList<>(out.mEnter);
						out.mEnter.remove(out.mEnter.size() - 1);
					}
				} else if(out.mEnter != null) {
					final Stmt s = (Stmt)d;
					if(s.containsInvokeExpr() && wcd.isWaitCall(s.getInvokeExpr().getMethod()) && out.mEnter.size() > 1) {
						throw new EverythingIsInconsistentException();
					}
					methodSynchMarkers.put(d, out.getActiveSync());
				}
			}
	
			@Override
			protected SyncAndStack newInitialFlow() {
				if(initialLock == null) {
					return new SyncAndStack();
				} else {
					return new SyncAndStack(initialLock);
				}
			}
			
			@Override
			protected void merge(final Unit succNode, final SyncAndStack in1, final SyncAndStack in2, final SyncAndStack out) {
				if(in1.mEnter == null) {
					copy(in2, out);
					return;
				} else if(in2.mEnter == null) {
					copy(in1, out);
					return;
				}
				assert in1.equals(in2) :  succNode + " "  + m + " " + in1 + " and " + in2 + " " + m.getActiveBody();
				copy(in1, out);
				if(out.mEnter != null) {
					methodSynchMarkers.put(succNode, out.getActiveSync());
				}
			};
	
			@Override
			protected void merge(final SyncAndStack in1, final SyncAndStack in2,
					final SyncAndStack out) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			protected void copy(final SyncAndStack source, final SyncAndStack dest) {
				dest.mEnter = source.mEnter;
			}
			
			{
				doAnalysis();
			}
		};
		if(methodSynchMarkers.size() > 0) {
			unitToSynchMarker.putAll(methodSynchMarkers);
			ms.syncPoints = methodSynchMarkers.valueCollection();
		}
	}

	private TIntCollection waitStatementAnalysis(final SootMethod m, final Set<Stmt> waitStmts) {
		final TIntCollection toReturn = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		for(final Stmt s: waitStmts) {
			assert s.getInvokeExpr() instanceof InstanceInvokeExpr;
			if(!unitToSynchMarker.contains(s)) {
			 throw new EverythingIsInconsistentException();
			}
			final InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
			final int monitorEnterId = unitToSynchMarker.get(s);
			final Unit monitorEnter = Scene.v().getUnitNumberer().get(monitorEnterId);
			final Value waitBase = iie.getBase();
			if(monitorEnter instanceof NopStmt) {
				assert !m.isStatic() : m;
				final Local thisLocal = m.getActiveBody().getThisLocal();
				if(thisLocal != waitBase) {
					throw new EverythingIsInconsistentException();
				}
			} else if(monitorEnter instanceof EnterMonitorStmt) {
				final Local monitorLocal = (Local) ((EnterMonitorStmt) monitorEnter).getOp();
				if(!simpleMustAlias(m, monitorLocal, (Stmt) monitorEnter, (Local) waitBase, s)) {
					throw new EverythingIsInconsistentException();
				}
			} else {
				throw new RuntimeException("Unexpected monitor enter statement: " + monitorEnter);
			}
			toReturn.add(AtMostOnceProblem.getUnitNumber(s));
			final Set<Object> syncBlockHavocGraphs = syncBlockCache.getUnchecked(unitToSynchMarker.get(s));
			havocGraphs.put(s, syncBlockHavocGraphs);
		}
		return toReturn;
	}

	private Set<Object> computeCoveredGraphs(final int blockId) {
		final Unit monitor = Scene.v().getUnitNumberer().get(blockId);
		final SootMethod containingMethod = this.icfg.getMethodOf(monitor);
		final Set<Object> toReturn = new HashSet<>();
		for(final Unit u : containingMethod.getActiveBody().getUnits()) {
			if(!unitToSynchMarker.containsKey(u)) {
				continue;
			}
			if(unitToSynchMarker.get(u) != blockId) {
				continue;
			}
			final Stmt s = (Stmt) u;
			if(s instanceof AssignStmt) {
				final AssignStmt as = (AssignStmt) s;
				final DefinitionType dt = ClassifyDefinition.classifyDef(as);
				final Value rhs = as.getRightOp();
				if(dt.rhsType == RHSType.ARRAY_READ) {
					final Local arrayBase = (Local) ((ArrayRef)rhs).getBase();
					toReturn.add(new Pair<Local, SootField>(arrayBase, AliasFinder.ARRAY_FIELD));
				} else if(dt.rhsType == RHSType.FIELD_READ) { 
					final InstanceFieldRef fieldRHS = (InstanceFieldRef) dt.extractFieldRHS(rhs);
					toReturn.add(new Pair<Local, SootField>((Local) fieldRHS.getBase(), fieldRHS.getField()));
				} else if(dt.rhsType == RHSType.STATIC_READ) {
					toReturn.add(dt.extractFieldRHS(rhs).getField());
				}
			} else if(s.containsInvokeExpr()) {
				final InvokeExpr ie = s.getInvokeExpr();
				for(final Value v : ie.getArgs()) {
					if(v instanceof Local) {
						toReturn.add(v);
					}
				}
				if(ie instanceof InstanceInvokeExpr) {
					toReturn.add(((InstanceInvokeExpr) ie).getBase());
				}
				for(final SootMethod m : this.icfg.getCalleesOfCallAt(s)) {
					toReturn.addAll(fieldAnalysis.usedStaticFields(m).toList());
				}
			}
		}
		return toReturn;
	}

	private Set<Stmt> volatileReadAnalysis(final SootMethod m, final MethodSynchronizationInfo ms) {
		final TIntCollection volReads = new TIntHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		Set<Stmt> waitStmts = null;
		for(final Unit u : m.getActiveBody().getUnits()) {
			final Stmt s = (Stmt) u;
			if(s.containsInvokeExpr()) {
				final String subSignature = s.getInvokeExpr().getMethod().getSubSignature();
				if(!subSignature.equals("void wait()") && !subSignature.equals("void wait(long)") && !subSignature.equals("void wait(long,int")) {
				 continue;
				}
				if(waitStmts == null) {
					waitStmts = new HashSet<>();
				}
				waitStmts.add(s);
				// ugh
			} else if(u instanceof AssignStmt) {
				final Value rhs = ((AssignStmt)u).getRightOp();
				if(!(rhs instanceof FieldRef)) {
					continue;
				}
				final int mod = ((FieldRef)rhs).getField().getModifiers();
				if(!Modifier.isVolatile(mod)) {
					continue;
				}
				volReads.add(AtMostOnceProblem.getUnitNumber(u));
			}
		}
		if(volReads.size() > 0) {
			ms.volatileReads = volReads;
		}
		return waitStmts;
	}

	private boolean simpleMustAlias(final SootMethod m, final Local monitorLocal, final Stmt monitorEnter, final Local waitBase, final Stmt s) {
		final StrongLocalMustAliasAnalysis lma = new StrongLocalMustAliasAnalysis((UnitGraph) this.icfg.getOrCreateUnitGraph(m));
		return lma.mustAlias(monitorLocal, monitorEnter, waitBase, s);
	}
	
	public Map<SootMethod, MethodSynchronizationInfo> getMethodSyncInfo() {
		return methodSyncInfo;
	}

	public Map<Unit, Set<Object>> getHavocGraphs() {
		return havocGraphs;
	}

	public Set<Unit> getMonitorEnterPoints() {
		return monitorEnterPoints;
	}

	public TObjectIntHashMap<Unit> getUnitToSynchMarker() {
		return unitToSynchMarker;
	}
	
	public HashMultimap<SootField, RefType> getSyncWriteFields() {
		return syncWriteFields;
	}

	private final LoadingCache<Integer, Set<Object>> syncBlockCache = CacheBuilder.newBuilder().build(new CacheLoader<Integer, Set<Object>>() {
		@Override
		public Set<Object> load(final Integer key) throws Exception {
			return computeCoveredGraphs(key);
		}
	});
	
}