package edu.washington.cse.instrumentation.analysis.preanalysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.NopStmt;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.CollectionFlowUniverse;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import soot.toolkits.scalar.Pair;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.queue.QueueReader;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.LegatoEdgePredicate;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis.FlowPair;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;

public class FieldPreAnalysis extends BackwardFlowAnalysis<SootMethod, FlowPair<SootField, SootField>> {
	public static class FlowPair<T1, T2> extends Pair<FlowSet<T1>, FlowSet<T2>> {
		public FlowPair(final FlowSet<T1> o1, final ArrayPackedSet<T2> o2) {
			super(o1,o2);
		}

		public void union(final FlowPair<T1, T2> in2, final FlowPair<T1, T2> out) {
			this.o1.union(in2.o1, out.o1);
			this.o2.union(in2.o2, out.o2);
		}

		public void copy(final FlowPair<T1, T2> dest) {
			this.o1.copy(dest.o1);
			this.o2.copy(dest.o2);
		}
	}
	
	private static final boolean SETTER_TAG = true;
	private static final boolean GETTER_TAG = false;
	private final FlowUniverse<SootField> staticUniverse;
	private final FlowSet<SootField> EMPTY_STATIC_SET;
	private final MultiMap<SootField, SootMethod> assignMethods = new HashMultiMap<>();
	
	private final Map<SootMethod, SootField> accessedFields = new HashMap<>();
	private final Map<SootMethod, Boolean> accessMethods = new HashMap<>();
	private final CollectionFlowUniverse<SootField> instanceUniverse;
	private final ArrayPackedSet<SootField> EMPTY_INSTANCE_SET;
	private final SootField containerField;
	private final PropagationManager propagationManager;
	
	public FieldPreAnalysis(final JimpleBasedInterproceduralCFG icfg, final PropagationManager propagationManager, final SootField containerField) {
		super(AtMostOnceProblem.makeDirectedCallGraph(icfg));
		final Set<SootField> allStaticFields = new HashSet<>();
		final Set<SootField> allInstanceFields = new HashSet<>();
		allInstanceFields.add(containerField);
		for(final SootClass cls : Scene.v().getClasses()) {
//			if(cls.isPhantom()) {
//				continue;
//			}
			for(final SootField f : cls.getFields()) {
				if(!f.isStatic()) {
					allStaticFields.add(f);
				} else {
					allInstanceFields.add(f);
				}
			}
		}
		staticUniverse = new CollectionFlowUniverse<>(allStaticFields);
		EMPTY_STATIC_SET = new ArrayPackedSet<>(staticUniverse);
		instanceUniverse = new CollectionFlowUniverse<>(allInstanceFields);
		EMPTY_INSTANCE_SET = new ArrayPackedSet<>(instanceUniverse);
		this.propagationManager = propagationManager;
		this.containerField = containerField;
		this.doAnalysis();
		
		this.findAccessMethods();
	}
	
	private void findAccessMethods() {
		final ReachableMethods rm = new ReachableMethods(Scene.v().getCallGraph(), Scene.v().getEntryPoints().iterator(), new Filter(new LegatoEdgePredicate()));
		rm.update();
		for(final QueueReader<MethodOrMethodContext> it = rm.listener(); it.hasNext(); ) {
			final SootMethod m = it.next().method();
			if(!m.hasActiveBody()) {
				continue;
			}
			if(m.isStatic()) {
				continue;
			}
			if(m.getParameterCount() != 0 && m.getParameterCount() != 1) {
				continue;
			}
			final SootField getField = getGetterField(m);
			final SootField setField = getSetterField(m);
			assert getField == null || setField == null : m;
			if(getField != null) {
				this.accessMethods.put(m, GETTER_TAG);
				this.accessedFields.put(m, getField);
			} else if(setField != null) {
				this.accessMethods.put(m, SETTER_TAG);
				this.accessedFields.put(m, setField);
			}
		}
	}
	
	private SootField getSetterField(final SootMethod m) {
		final PatchingChain<Unit> units = m.getActiveBody().getUnits();
		if(units.size() != 5) {
			return null;
		}
		SootField f = null;
		int step = 0;
		Local l = null;
		final Local thisLocal = m.getActiveBody().getThisLocal();
		if(m.getActiveBody().getParameterLocals().size() != 1) {
			return null;
		}
		for(final Unit u : units) {
			if(step == 0) {
				if(!(u instanceof NopStmt)) {
					return null;
				}
			} else if(step == 1) {
				if(!(u instanceof IdentityStmt)) {
					return null;
				}
				final IdentityStmt is = (IdentityStmt) u;
				if(!(is.getRightOp() instanceof ThisRef) || is.getLeftOp() != thisLocal) {
					return null;
				}
			} else if(step == 2) {
				if(!(u instanceof IdentityStmt)) {
					return null;
				}
				final IdentityStmt is = (IdentityStmt) u;
				if(!(is.getRightOp() instanceof ParameterRef) ||
						((ParameterRef)is.getRightOp()).getIndex() != 0 ||
						is.getLeftOp() != m.getActiveBody().getParameterLocal(0)) {
					return null;
				}
				l = (Local) is.getLeftOp();
			} else if(step == 3) {
				if(!(u instanceof AssignStmt)) {
					return null;
				}
				final AssignStmt s = (AssignStmt) u;
				if(!s.containsFieldRef()) {
					return null;
				}
				if(s.getLeftOp() instanceof InstanceFieldRef && 
						((InstanceFieldRef)s.getLeftOp()).getBase() == thisLocal &&
						s.getRightOp() == l) {
					f = s.getFieldRef().getField();
				} else {
					return null;
				}
			} else if(step == 4) {
				if(!(u instanceof ReturnVoidStmt)) {
					return null;
				}
				return f;
			}
			step++;
		}
		return null;
	}

	private SootField getGetterField(final SootMethod m) {
		final PatchingChain<Unit> units = m.getActiveBody().getUnits();
		if(units.size() != 4) {
			return null;
		}
		if(m.getParameterCount() != 0) {
			return null;
		}
		SootField f = null;
		int step = 0;
		Local l = null;
		final Local thisLocal = m.getActiveBody().getThisLocal();
		for(final Unit u : units) {
			if(step == 0) {
				if(!(u instanceof NopStmt)) {
					return null;
				}
			} else if(step == 1) {
				if(!(u instanceof IdentityStmt)) {
					return null;
				}
				final IdentityStmt is = (IdentityStmt) u;
				if(!(is.getRightOp() instanceof ThisRef) || is.getLeftOp() != thisLocal) {
					return null;
				}
			} else if(step == 2) {
				if(!(u instanceof AssignStmt)) {
					return null;
				}
				final AssignStmt s = (AssignStmt) u;
				if(!s.containsFieldRef()) {
					return null;
				}
				if(s.getLeftOp() instanceof Local && s.getRightOp() instanceof InstanceFieldRef && 
						((InstanceFieldRef)s.getRightOp()).getBase() == thisLocal) {
					l = (Local) s.getLeftOp();
					f = s.getFieldRef().getField();
				} else {
					return null;
				}
			} else if(step == 3) {
				if(!(u instanceof ReturnStmt)) {
					return null;
				}
				final ReturnStmt retStmt = (ReturnStmt) u;
				if(retStmt.getOp() != l) {
					return null;
				}
				return f;
			}
			step++;
		}
		return null;
	}

	@Override
	protected FlowPair<SootField, SootField> newInitialFlow() {
		return new FlowPair<SootField, SootField>(EMPTY_STATIC_SET.clone(), EMPTY_INSTANCE_SET.clone());
	}
	
	@Override
	protected void merge(final FlowPair<SootField, SootField> in1, final FlowPair<SootField, SootField> in2, final FlowPair<SootField, SootField> out) {
		in1.union(in2, out);
	}
	
	@Override
	protected void copy(final FlowPair<SootField, SootField> source, final FlowPair<SootField, SootField> dest) {
		source.copy(dest);
	}
	
	@Override
	protected void flowThrough(final FlowPair<SootField, SootField> in, final SootMethod d, final FlowPair<SootField, SootField> out) {
		in.copy(out);
		if(!d.hasActiveBody()) {
			return;
		}
		for(final Unit u : d.getActiveBody().getUnits()) {
			final Stmt s = (Stmt) u;
			if(s.containsFieldRef() && s.getFieldRef() instanceof StaticFieldRef) {
				if(s instanceof AssignStmt && ((AssignStmt) s).getLeftOp() == s.getFieldRef()) {
					assignMethods.put(s.getFieldRef().getField(), d);
				}
				out.getO1().add(s.getFieldRef().getField());
			} else if(s.containsFieldRef() && s.getFieldRef() instanceof InstanceFieldRef) {
				out.getO2().add(s.getFieldRef().getField());
			} else if(s.containsInvokeExpr()) {
				final InvokeExpr ie = s.getInvokeExpr();
				final SootMethod callee = ie.getMethod();
				PropagationSpec ps;
				if(propagationManager.isPropagationMethod(callee) && (ps = propagationManager.getPropagationSpec(s)) != null 
						&& ps.getPropagationTarget().isContainerAbstraction()) {
					out.getO2().add(containerField);
				}
			}
		}
	}
	
	public FlowSet<SootField> usedStaticFields(final SootMethod m) {
		return this.getFlowBefore(m).getO1();
	}
	
	public FlowSet<SootField> usedInstanceFields(final SootMethod m) {
		return this.getFlowBefore(m).getO2();
	}
	
	public boolean isGetterMethod(final SootMethod m) {
		return this.accessMethods.containsKey(m) && this.accessMethods.get(m) == GETTER_TAG;
	}

	public boolean isSetterMethod(final SootMethod m) {
		return this.accessMethods.containsKey(m) && this.accessMethods.get(m) == SETTER_TAG;
	}
	
	public SootField getRetrievedField(final SootMethod m) {
		return this.accessedFields.get(m);
	}
	
	public MultiMap<SootField, SootMethod> writerMethods() {
		return assignMethods;
	}

}
