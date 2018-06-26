package edu.washington.cse.instrumentation.analysis.functions;

import heros.FlowFunction;
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.AnySubType;
import soot.ArrayType;
import soot.Body;
import soot.FastHierarchy;
import soot.Local;
import soot.Modifier;
import soot.PrimType;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.DefinitionStmt;
import soot.jimple.EqExpr;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InstanceOfExpr;
import soot.jimple.IntConstant;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.NeExpr;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.OnFlyCallGraphBuilder;
import soot.jimple.toolkits.callgraph.VirtualCalls;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.NumberedString;
import boomerang.AliasFinder;
import boomerang.BoomerangTimeoutException;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.FieldGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.cache.AliasResults;
import boomerang.forward.AbstractFlowFunctions;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.EverythingIsInconsistentException;
import edu.washington.cse.instrumentation.analysis.InconsistentReadSolver;
import edu.washington.cse.instrumentation.analysis.aliasing.AliasResolver;
import edu.washington.cse.instrumentation.analysis.functions.CallParamDeciderProvider.CallParameterDecider;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.DefinitionType;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.LHSType;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.RHSType;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.SyncPreAnalysis;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager.PropagationTarget;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;
import edu.washington.cse.instrumentation.analysis.solver.SolverAwareFlowFunctions;
import edu.washington.cse.instrumentation.analysis.utils.ImmutableTwoElementSet;

public class ForwardFlowFunctions extends AbstractPathFunctions implements SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> {
	private final SootField containerContentField;
	private final FastHierarchy fh;
	
	private final Set<Unit> fieldWrites = Collections.synchronizedSet(new HashSet<Unit>());
	private final Set<Unit> propagations = Collections.synchronizedSet(new HashSet<Unit>());;
	private final Set<SootField> propagationFields = Collections.synchronizedSet(new HashSet<SootField>());
	
	private final SootField allSubFields = new SootField("*", Scene.v().getRefType("java.lang.Object"));
	private final AliasResolver aliasResolver;
	private final FieldPreAnalysis fpa;
	private final Collection<SootMethod> ignoredMethods;
	private InconsistentReadSolver solver;

	public ForwardFlowFunctions(final AnalysisConfiguration conf,
			final AccessGraph zeroValue,
			final Table<Unit, AccessGraph, Set<AccessGraph>> synchReadLookup,
			final SyncPreAnalysis spa, final CallParamDeciderProvider cpdp,
			final FieldPreAnalysis fpa) {
		super(conf, zeroValue, spa, fpa, synchReadLookup, cpdp);
		
		this.aliasResolver = conf.aliasResolver;
		this.containerContentField = aliasResolver.containerContentField;
		this.fpa = fpa;
		
		this.fh = Scene.v().getOrMakeFastHierarchy();
		Scene.v().getSootClass("java.lang.Object").addField(allSubFields);
		
		this.ignoredMethods = conf.ignoredMethods;
	}
	
	@Override
	public void setSolver(final InconsistentReadSolver solver) {
		this.solver = solver;
	}
	
	@Override
	public FlowFunction<AccessGraph> getReturnFlowFunction(final Unit callSite,
			final SootMethod calleeMethod, final Unit exitStmt, final Unit returnSite) {
		if(calleeMethod.isStaticInitializer()) {
			final Stmt cs = (Stmt) callSite;
			if((cs.containsInvokeExpr() && cs.getInvokeExpr().getMethod().getName().equals("legato_dummy_clinit"))) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.isStatic()) {
							return Collections.singleton(source);
						} else if(source == zeroValue) {
							return Collections.singleton(source);
						} else {
							return Collections.emptySet();
						}
					}
				};
			} else {
				return KillAll.v();
			}
		}
		if(ignoredMethods.contains(calleeMethod)) {
			return KillAll.v();
		}
		if(resourceResolver.isResourceAccess(((Stmt)callSite).getInvokeExpr(), callSite)) {
			return KillAll.v();
		}
		final FlowFunction<AccessGraph> specialFlow = handleSpecialReturnFlow((Stmt)callSite, (Stmt) exitStmt, calleeMethod);
		if(specialFlow != null) {
			return specialFlow;
		}
		final Value rhs;
		final Local lhs;
		if(callSite instanceof DefinitionStmt && exitStmt instanceof ReturnStmt) {
			final DefinitionStmt ds = (DefinitionStmt)callSite;
			final ReturnStmt rs = (ReturnStmt)exitStmt;
			rhs = rs.getOp();
			lhs = (Local)ds.getLeftOp();
		} else {
			rhs = null;
			lhs = null;
		}
		final boolean isCatchBlock = returnSite instanceof IdentityStmt;
		final List<Local> locals = new ArrayList<>(calleeMethod.getActiveBody().getParameterLocals());
		final List<Value> args = new ArrayList<>(((Stmt)callSite).getInvokeExpr().getArgs());
		if(!calleeMethod.isStatic()) {
			locals.add(calleeMethod.getActiveBody().getThisLocal());
			final InstanceInvokeExpr iie = (InstanceInvokeExpr) ((Stmt)callSite).getInvokeExpr();
			args.add(iie.getBase());
		}
		final SootMethod callerMethod = icfg.getMethodOf(returnSite);
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source == zeroValue) {
					return Collections.singleton(source);
				}
				if(source.isStatic()) {
					return Collections.singleton(source);
				}
				final HashSet<AccessGraph> toRet = new HashSet<>();
				if(locals.contains(source.getBase()) && AtMostOnceProblem.propagateThroughCall(source)) {
					final int idx = locals.indexOf(source.getBase());
					if(lhs != args.get(idx) && args.get(idx) instanceof Local) {
						final Local argLocal = (Local) args.get(idx);
						if(hasCompatibleReturnType(argLocal, source.getBaseType(), callerMethod)) {
							final Type mappedType = selectReturnType(argLocal, source.getBaseType(), callerMethod);
							final AccessGraph translated = source.deriveWithNewLocal(argLocal, mappedType);
							toRet.add(translated);
						}
					}
				}
				if(rhs != null && source.baseMatches(rhs) && hasCompatibleReturnType(lhs, source.getBaseType(), callerMethod) && !isCatchBlock) {
					toRet.add(source.deriveWithNewLocal(lhs, source.getBaseType()));
				}
				return toRet;
			}
		};
	}
	
	@Override
	public FlowFunction<AccessGraph> getNormalFlowFunction(final Unit curr, final Unit succ) {
		if(curr instanceof IfStmt) {
			return interpretIf((IfStmt)curr, succ);
		}
		if(!(curr instanceof DefinitionStmt)) {
			return Identity.v();
		}
		if(curr instanceof IdentityStmt) {
			return Identity.v();
		}
		final DefinitionStmt ds = (DefinitionStmt)curr;
		final DefinitionType dt = ClassifyDefinition.classifyDef(ds);
		if(dt.lhsType == LHSType.FIELD_WRITE) {
			final SootField field = ds.getFieldRef().getField();
			final Local base = (Local) ((InstanceFieldRef)ds.getLeftOp()).getBase();
			if(dt.rhsType == RHSType.LOCAL) {
				final Local rhsLocal = (Local) ds.getRightOp();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						}
						final WrappedSootField wsf = new WrappedSootField(field, source.getBaseType(), curr);
						if(source.baseMatches(rhsLocal) &&
								AliasResults.canPrepend(source.deriveWithNewLocal(base, base.getType()), wsf)) {
							final AccessGraph derived = source.prependField(wsf).deriveWithNewLocal(base, base.getType());
							final Set<AccessGraph> toReturn = new HashSet<>();
							toReturn.add(derived);
							propagateWriteAliases(base, curr, derived.getFieldGraph(), toReturn, null, derived);
							
							if(!source.baseAndFirstFieldMatches(base, field)) {
								toReturn.add(source);
							}
							fieldWrites.add(curr);
							return toReturn;
						} else if(source.baseAndFirstFieldMatches(base, field)) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			} else if(dt.isKillRHS()) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						} else if(source.baseAndFirstFieldMatches(base, field)) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			}
			throw new RuntimeException("Impossible case: " + dt + " " + ds);
		} else if(dt.lhsType == LHSType.STATIC_WRITE) {
			final StaticFieldRef r = (StaticFieldRef) ds.getFieldRef();
			if(dt.isKillRHS()) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.firstFieldMatches(r.getField())) {
							assert source.isStatic();
							return Collections.emptySet();
						}
						return Collections.singleton(source);
					}
				};
			}
			assert dt.rhsType == RHSType.LOCAL : ds + " " + dt;
			final Local rhs = (Local) ds.getRightOp();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.baseMatches(rhs)) {
						return new ImmutableTwoElementSet<>(source.prependField(new WrappedSootField(r.getField(), source.getBaseType(), succ)).makeStatic(), source);
					} else if(source.firstFieldMatches(r.getField())) {
						return Collections.emptySet();
					} else {
						return Collections.singleton(source);
					}
				}
			};
		} else if(dt.lhsType == LHSType.LOCAL) {
			final Local l = (Local) ds.getLeftOp();
			switch(dt.rhsType) {
			case CAST:
			{
				final CastExpr ce = (CastExpr) ds.getRightOp();
				final Local castLocal = (Local) ce.getOp();
				final Type castType = ce.getCastType();
				final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						}
						if(source.baseMatches(l)) {
							return Collections.emptySet();
						}
						if(source.baseMatches(castLocal)) {
							if(castType instanceof RefLikeType && (fh.canStoreType(source.getBaseType(), castType) || fh.canStoreType(castType, source.getBaseType()))) {
								return new ImmutableTwoElementSet<AccessGraph>(source, source.deriveWithNewLocal(l, castType));
							} else if(source.getBaseType() instanceof PrimType) {
								return new ImmutableTwoElementSet<AccessGraph>(source, source.deriveWithNewLocal(l, castType)); 
							} else {
								return Collections.singleton(source);
							}
						} else {
							return Collections.singleton(source);
						}
					}
				};
			}
			case LOCAL:
			case EXPR:
				final List<Local> usedBoxes = new ArrayList<>();
				for(final ValueBox vb : ds.getUseBoxes()) {
					if(vb.getValue() instanceof Local) {
						usedBoxes.add((Local)vb.getValue());
					}
				}
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						} else if(usedBoxes.contains(source.getBase())) {
							return new ImmutableTwoElementSet<>(source, source.deriveWithNewLocal(l, source.getBaseType()));
						} else if(source.baseMatches(ds.getLeftOp())) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			
			case FIELD_READ:
				assert ds.getFieldRef() == ds.getRightOp();
				final InstanceFieldRef irf = (InstanceFieldRef) ds.getFieldRef();
				final Local base = (Local) irf.getBase();
				final SootField f = irf.getField();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						} else if(source.baseAndFirstFieldMatches(base, f)) {
							final Set<AccessGraph> toReturn = new HashSet<>();
							toReturn.add(source);
							for(final AccessGraph ag : source.popFirstField()) {
								toReturn.add(ag.deriveWithNewLocal(l, source.getFirstField().getType()));
							}
							if(!Modifier.isVolatile(f.getModifiers()) && isSyncedFieldAt(curr, f)) {
								for(final AccessGraph aliases: doAliasSearch(new AccessGraph(base, base.getType()), curr, source).mayAliasSet()) {
									toReturn.add(aliases.appendGraph(source.getFieldGraph()));
								}
								synchronized(synchReadLookup) {
									if(!synchReadLookup.contains(curr, source)) {
										synchReadLookup.put(curr, source, new HashSet<AccessGraph>());
									}
									synchReadLookup.get(curr, source).addAll(toReturn);
								}
							}
							return toReturn;
						} else if(source.baseAndFirstFieldMatches(base, allSubFields)) {
							final Set<AccessGraph> toReturn = new HashSet<>();
							toReturn.add(source);
							if(AtMostOnceProblem.mayAliasType(l.getType())) {
								toReturn.add(source.deriveWithNewLocal(l, l.getType()));
							} else {
								toReturn.add(new AccessGraph(l, l.getType()));
							}
							// TODO: do this properly
							if(!Modifier.isVolatile(f.getModifiers()) && isSyncedFieldAt(curr, f)) {
								throw new EverythingIsInconsistentException();
							}
							return toReturn;
						} else if(source.baseMatches(ds.getLeftOp())) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			case STATIC_READ:
				final StaticFieldRef r = (StaticFieldRef) ds.getFieldRef();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.baseMatches(l)) {
							return Collections.emptySet();
						} else if(source.isStatic() && source.firstFieldMatches(r.getField())) {
							final Set<AccessGraph> toReturn = new HashSet<>();
							toReturn.addAll(source.deriveWithNewLocal(l, source.getFirstField().getType()).popFirstField());
							toReturn.add(source);
							if(isSyncedFieldAt(curr, r.getField())) {
								synchronized(synchReadLookup) {
									if(!synchReadLookup.contains(curr, source)) {
										synchReadLookup.put(curr, source, new HashSet<AccessGraph>());
									}
									synchReadLookup.get(curr, source).addAll(toReturn);
								}
							}
							return toReturn;
						} else {
							return Collections.singleton(source);
						}
					}
				};
			case NEW:
			case CONST:
			case NULL:
			case REF_CONST:
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.baseMatches(l)) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			case ARRAY_READ:
				final Local arrayBase = (Local) ds.getArrayRef().getBase();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.baseAndFirstFieldMatches(arrayBase, AliasFinder.ARRAY_FIELD)) {
							final Set<AccessGraph> toReturn = new HashSet<>();
							for(final AccessGraph ag : source.popFirstField()) {
								toReturn.add(ag.deriveWithNewLocal(l, source.getFirstField().getType()));
							}
							if(!source.baseMatches(l)) {
								toReturn.add(source);
							}
							return toReturn;
						} else {
							return Collections.singleton(source);
						}
					}
				};
			}
			throw new RuntimeException("Unhandled RHS: " + dt.rhsType);
		} else if(dt.lhsType == LHSType.ARRAY_WRITE) {
			final Local arrayBase = (Local) ds.getArrayRef().getBase();
			// never strong updates for array writes
			if(dt.isKillRHS()) {
				return Identity.v();
			}
			final Local rhs = (Local) ds.getRightOp();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.baseMatches(rhs)) {
						final WrappedSootField[] wrappedF = new WrappedSootField[]{new WrappedSootField(AliasFinder.ARRAY_FIELD, source.getBaseType(), curr)};
						AccessGraph toAdd = new AccessGraph(arrayBase, arrayBase.getType(), wrappedF);
						if(source.getFieldCount() > 0) {
							toAdd = toAdd.appendGraph(source.getFieldGraph());
						}
						final AliasResults aliases = doAliasSearch(new AccessGraph(arrayBase, arrayBase.getType()), curr, toAdd);
						final HashSet<AccessGraph> toReturn = new HashSet<>();
						toReturn.add(source);
						toReturn.add(toAdd);
						for(final AccessGraph ag : aliases.mayAliasSet()) {
							if(!(ag.getRTType() instanceof ArrayType)) {
								continue;
							}
							AccessGraph app = ag.appendFields(wrappedF);
							if(source.getFieldCount() > 0) {
								app = app.appendGraph(source.getFieldGraph());
							}
							toReturn.add(app);
						}
						return toReturn;
					} else {
						return Collections.singleton(source);
					}
				}
			};
		} else {
			throw new RuntimeException("Unhandled lhs type: " + dt.lhsType);
		}
	}
	
	private FlowFunction<AccessGraph> identityCallReturnFlowFunction(final Value kill, final CallParameterDecider cpd) {
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(kill != null && source.baseMatches(kill)) {
					return Collections.emptySet();
				} else if(cpd.passesOverCall(source)) {
					return Collections.singleton(source);
				} else {
					return Collections.emptySet();
				}
			}
		};
	}

	@Override
	public FlowFunction<AccessGraph> getCallToReturnFlowFunction(final Unit callSite,
			final Unit returnSite) {
		final Stmt callStmt = (Stmt) callSite;
		final boolean isPropagation = propagationManager.isPropagationMethod(callStmt.getInvokeExpr().getMethod());
		final boolean isAccess = resourceResolver.isResourceAccess(callStmt.getInvokeExpr(), callStmt);
		
		final Value kill = callStmt instanceof DefinitionStmt ? ((DefinitionStmt)callStmt).getLeftOp() : null;
		final InvokeExpr ie = callStmt.getInvokeExpr();
		final CallParameterDecider cpd = paramDeciderCache.getUnchecked(callStmt);
		final FlowFunction<AccessGraph> returnMapper = identityCallReturnFlowFunction(kill, cpd);
		if(isPropagation) {
			final PropagationSpec propagationSpec = propagationManager.getPropagationSpec(callSite);
			final PropagationTarget propTarget = propagationSpec.getPropagationTarget();
			if(propagationSpec == null || 
					(propTarget == PropagationTarget.GRAPH && propagationSpec.getTargetGraph() == null) ||
					((propTarget == PropagationTarget.CONTAINER_GET || propTarget == PropagationTarget.CONTAINER_TRANSFER) && kill == null) ||
					propTarget == PropagationTarget.IDENTITY) {
				return returnMapper;
			}
			if(propTarget == PropagationTarget.DIE) {
				throw new EverythingIsInconsistentException();
			}
			if(propTarget.isContainerAbstraction()) {
				return containerAbstraction(callStmt, kill, ie, returnMapper, propagationSpec);
			}
			if(propTarget == PropagationTarget.HAVOC) {
				return havocPropagation(callStmt, propagationSpec, cpd, kill, ie);
			}
			// this still seems fishy
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if((source.getFieldCount() == 0 && propagationSpec.getLocalPropagation().contains(source.getBase())) ||
							(!source.isStatic() && propagationSpec.getSubFieldPropagation().contains(source.getBase()))) {
						final PropagationTarget propagationType = propTarget;
						if(propagationType == PropagationTarget.RECEIVER || (propagationType == PropagationTarget.FLUENT && kill == null)) {
							propagations.add(callStmt);
							return handleReceiverPropagation(returnSite, kill, ie, cpd, source);
						} else if(propagationType == PropagationTarget.RETURN) {
							if(kill == null) {
								return returnMapper.computeTargets(source);
							}
							propagations.add(callStmt);
							return new ImmutableTwoElementSet<AccessGraph>(source, new AccessGraph((Local) kill, kill.getType()));
						} else if(propagationType == PropagationTarget.FLUENT) {
							assert propagationType == PropagationTarget.FLUENT : propagationSpec;
							assert ie instanceof InstanceInvokeExpr;
							final Local base = (Local) ((InstanceInvokeExpr)ie).getBase();
							final Local lhsLocal = (Local)kill;
							propagations.add(callStmt);
							if(source.baseMatches(base)) {
								return new ImmutableTwoElementSet<AccessGraph>(source, new AccessGraph(lhsLocal, lhsLocal.getType()));
							} else {
								final Set<AccessGraph> toReturn = handleReceiverPropagation(returnSite, kill, ie, cpd, source);
								toReturn.add(new AccessGraph(lhsLocal, lhsLocal.getType()));
								return toReturn;
							}
						} else if(propagationType == PropagationTarget.GRAPH) {
							return new ImmutableTwoElementSet<AccessGraph>(source, propagationSpec.getTargetGraph());
						} else {
							throw new RuntimeException("failed to handle propagation type: " + propTarget);
						}
					} else {
						return returnMapper.computeTargets(source);
					}
				}
			};
		} else if(isAccess) {
			if(kill == null) {
				return returnMapper;
			}
			if(kill.getType() instanceof ArrayType) {
				final Type innerType = ((ArrayType) kill.getType()).getArrayElementType();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							final WrappedSootField arrayField = new WrappedSootField(AliasFinder.ARRAY_FIELD, innerType, callStmt);
							return new ImmutableTwoElementSet<AccessGraph>(source, new AccessGraph((Local) kill, kill.getType(), arrayField));
						} else {
							return returnMapper.computeTargets(source);
						}
					}
				};
			}
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return new ImmutableTwoElementSet<AccessGraph>(source, new AccessGraph((Local) kill, kill.getType()));
					} else {
						return returnMapper.computeTargets(source);
					}
				}
			};
		} else if(isPhantomMethodCall(callStmt, ie.getMethod())) {
			final Local base = (Local) (ie instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr)ie).getBase() : null);
			final WrappedSootField wrappedSubField = new WrappedSootField(allSubFields, AnySubType.v(Scene.v().getRefType("java.lang.Object")), callStmt);
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					if(!cpd.isArgument(source)) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(kill != null) {
						if(AtMostOnceProblem.mayAliasType(kill.getType())) {
							toReturn.add(new AccessGraph((Local)kill, kill.getType(),
									wrappedSubField));
						} else {
							toReturn.add(new AccessGraph((Local)kill, kill.getType()));
						}
					}
					if(base != null && !source.baseMatches(base)) {
						toReturn.add(new AccessGraph(base, base.getType(), wrappedSubField));
					}
					if(kill == null || !source.baseMatches(kill)) {
						toReturn.add(source);
					}
					return toReturn;
				}
			};
		} else {
			return returnMapper;
		}
	}

	private FlowFunction<AccessGraph> havocPropagation(final Stmt callStmt, final PropagationSpec propagationSpec,
			final CallParameterDecider cpd, final Value kill, final InvokeExpr ie) {
		final Set<Local> potentialOutputs = new HashSet<>();
		for(final Value v : ie.getArgs()) {
			if(!(v instanceof Local)) {
				continue;
			}
			if(!(AtMostOnceProblem.mayAliasType(v.getType()))) {
				continue;
			}
			potentialOutputs.add((Local) v);
		}
		if(ie instanceof InstanceInvokeExpr && AtMostOnceProblem.mayAliasType(((InstanceInvokeExpr)ie).getBase().getType())) {
			potentialOutputs.add((Local) ((InstanceInvokeExpr)ie).getBase());
		}
		if(kill != null) {
			potentialOutputs.add((Local) kill);
		}
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source.isStatic() || source == zeroValue) {
					return Collections.singleton(source);
				}
				final HashSet<AccessGraph> output = new HashSet<>();
				if(cpd.isArgument(source)) {
					for(final Local outValue : potentialOutputs) {
						final WrappedSootField wrappedAllSub = new WrappedSootField(allSubFields, allSubFields.getType(), callStmt);
						if(outValue != kill) {
							final Set<AccessGraph> as = doAliasSearch(new AccessGraph(outValue, outValue.getType()), callStmt, 
									new AccessGraph(outValue, outValue.getType(), wrappedAllSub)).mayAliasSet();
							output.addAll(AliasResults.appendField(as, wrappedAllSub, aliasResolver.getContext()));
						} else if(outValue instanceof PrimType) {
							output.add(new AccessGraph(outValue, outValue.getType()));
						} else {
							output.add(new AccessGraph(outValue, outValue.getType(), wrappedAllSub));
						}
					}
				}
				if(kill == null || !source.baseMatches(kill)) {
					output.add(source);
				}
				return output;
			}
		};
	}

	private void propagateWriteAliases(final Local base, final Unit source, final FieldGraph g, final Set<AccessGraph> out, final SootField firstField,
			final AccessGraph sourceGraph) {
		if(firstField == null) {
			final AliasResults aliases = doAliasSearch(new AccessGraph(base, base.getType()), source, sourceGraph);
			for(final AccessGraph ag: aliases.mayAliasSet()) {
				out.add(ag.appendGraph(g));
			}
		} else {
			final Type t = firstField.getDeclaringClass().getType();
			final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
			final AliasResults aliases = doAliasSearch(new AccessGraph(base, base.getType()), source, sourceGraph);
			for(final AccessGraph ag: aliases.mayAliasSet()) {
				final Type aType = ag.getRTType();
				if(fh.canStoreType(t, aType) || fh.canStoreType(aType, t)) {
					out.add(ag.appendGraph(g));
				}
			}
		}
	}

	private AliasResults doAliasSearch(final AccessGraph accessGraph, final Unit source, final AccessGraph forGraph) {
		try {
			return aliasResolver.doAliasSearch(accessGraph, source);
		} catch(final BoomerangTimeoutException e) {
			this.solver.reportHeapTimeout(source, forGraph);
			return new AliasResults();
		}
	}

	private FlowFunction<AccessGraph> containerAbstraction(final Stmt callStmt,
			final Value kill, final InvokeExpr ie, final FlowFunction<AccessGraph> returnMapper,
			final PropagationSpec propagationSpec) {
		final PropagationTarget propTarget = propagationSpec.getPropagationTarget();
		final SootMethod methodOfCall = icfg.getMethodOf(callStmt);
		if(propTarget == PropagationTarget.CONTAINER_TRANSFER) {
			assert kill != null;
			final Local lhs = (Local) kill;
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(propagationSpec.getLocalPropagation().contains(source.getBase()) && source.firstFieldMatches(containerContentField)) {
						if(lhs.getType() instanceof ArrayType) {
							final Set<AccessGraph> popped = source.popFirstField();
							for(final AccessGraph p : popped) {
								final WrappedSootField wsf = new WrappedSootField(AliasFinder.ARRAY_FIELD, source.getFirstField().getType(), callStmt);
								toReturn.add(p.prependField(wsf).deriveWithNewLocal(lhs, source.getFirstField().getType().getArrayType()));
							}
						} else {
							toReturn.add(source.deriveWithNewLocal(lhs, lhs.getType()));
						}
					}
					if(!source.baseMatches(lhs)) {
						toReturn.add(source);
					}
					return toReturn;
				}
			};
		} else if(propTarget == PropagationTarget.CONTAINER_MOVE) {
			final Set<Local> outVariables = new HashSet<>();
			for(final Value arg : ie.getArgs()) {
				if(arg instanceof Local && arg.getType() instanceof RefType) {
					outVariables.add((Local) arg);
				}
			}
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(propagationSpec.getLocalPropagation().contains(source.getBase()) && source.firstFieldMatches(containerContentField)) {
						for(final Local out : outVariables) {
							toReturn.add(source.deriveWithNewLocal(out, out.getType()));
						}
					}
					if(!source.baseMatches(kill)) {
						toReturn.add(source);
					}
					return toReturn;
				}
			};
		} else if(propTarget == PropagationTarget.CONTAINER_GET) {
			if(kill == null) {
				return returnMapper;
			}
			assert ie instanceof InstanceInvokeExpr;
			final Local base = (Local) ((InstanceInvokeExpr)ie).getBase();
			final Local lhs = (Local) kill;
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue || source.isStatic()) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<AccessGraph>();
					if(source.baseAndFirstFieldMatches(base, containerContentField) && hasCompatibleReturnType(lhs, source.getFirstField().getType(), methodOfCall)) {
						toReturn.addAll(source.deriveWithNewLocal(lhs, source.getFirstField().getType()).popFirstField());
					}
					if(!source.baseMatches(kill)) {
						toReturn.add(source);
					}
					return toReturn;
				}
			};
		} else if(propTarget == PropagationTarget.CONTAINER_PUT || 
				(propTarget == PropagationTarget.CONTAINER_REPLACE && kill == null)) {
			assert ie instanceof InstanceInvokeExpr;
			final Local base = (Local) ((InstanceInvokeExpr)ie).getBase();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(propagationSpec.getLocalPropagation().contains(source.getBase())) {
						final AccessGraph derived = 
								source.prependField(new WrappedSootField(containerContentField, source.getBaseType(), callStmt)).deriveWithNewLocal(base, base.getType());
						toReturn.add(derived);
						propagateWriteAliases(base, callStmt, derived.getFieldGraph(), toReturn, null, derived);
					}
					if(kill == null || !source.baseMatches(kill)) {
						toReturn.add(source);	
					}
					return toReturn;
				}
			};
		} else if(propTarget == PropagationTarget.CONTAINER_ADDALL) {
			final Local base = (Local) ((InstanceInvokeExpr)ie).getBase();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(propagationSpec.getLocalPropagation().contains(source.getBase()) && source.firstFieldMatches(containerContentField)) {
						final AccessGraph sourceGraph = source.deriveWithNewLocal(base, base.getType());
						toReturn.add(sourceGraph);
						propagateWriteAliases(base, callStmt, source.getFieldGraph(), toReturn, null, sourceGraph);
					}
					toReturn.add(source);
					return toReturn;
				}
			};
		} else if(propTarget == PropagationTarget.CONTAINER_REPLACE) {
			final Local base = (Local) ((InstanceInvokeExpr)ie).getBase();
			assert kill != null;
			final Local lhs = (Local) kill;
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					}
					final Set<AccessGraph> toReturn = new HashSet<>();
					if(source.baseAndFirstFieldMatches(base, containerContentField) && hasCompatibleReturnType(lhs, source.getFirstField().getType(), methodOfCall)) {
						toReturn.addAll(source.deriveWithNewLocal(lhs, source.getFirstField().getType()).popFirstField());
					}
					if(propagationSpec.getLocalPropagation().contains(source.getBase())) {
						final AccessGraph derived = source.prependField(new WrappedSootField(containerContentField, source.getBaseType(), callStmt))
								.deriveWithNewLocal(base, base.getType());
						toReturn.add(derived);
						propagateWriteAliases(base, callStmt, derived.getFieldGraph(), toReturn, null, derived);
					}
					if(!source.baseMatches(kill)) {
						toReturn.add(source);
					}
					return toReturn;
				}
			};
		} else {
			throw new IllegalArgumentException("Not a container abstraction: " + propTarget);
		}
	}

	@Override
	public FlowFunction<AccessGraph> getCallFlowFunction(final Unit callStmt,
			final SootMethod destinationMethod) {
		if(destinationMethod.isStaticInitializer()) {
			return KillAll.v();
		}
		if(resourceResolver.isResourceMethod(destinationMethod)) {
			return KillAll.v();
		}
		if(Scene.v().isExcluded(destinationMethod.getDeclaringClass())) {
			return KillAll.v();
		}
		if(ignoredMethods.contains(destinationMethod)) {
			return KillAll.v();
		}
		final Stmt s = (Stmt) callStmt;
		final FlowSet<SootField> accessedStatics = usedStaticFields(destinationMethod);
		final FlowFunction<AccessGraph> specialFlow = handleSpecialCallFlow(s, destinationMethod, accessedStatics);
		if(specialFlow != null) {
			return specialFlow;
		}
		final ArrayList<Local> paramLoc = new ArrayList<>(destinationMethod.getActiveBody().getParameterLocals());
		final ArrayList<Value> argValues = new ArrayList<>(s.getInvokeExpr().getArgs());
		final Value base;
		if(s.getInvokeExpr() instanceof InstanceInvokeExpr) {
			final InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
			paramLoc.add(destinationMethod.getActiveBody().getThisLocal());
			argValues.add(base = iie.getBase());
		} else {
			base = null;
		}
		final MultiMap<Local, Local> argMapping = new HashMultiMap<>();
		for(int i = 0; i < paramLoc.size(); i++) {
			final Value v = argValues.get(i);
			if(v instanceof Local) {
				argMapping.put((Local) v, paramLoc.get(i));
			}
		}
		final CallParameterDecider cpm = paramDeciderCache.getUnchecked(callStmt);
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source == zeroValue) {
					return Collections.singleton(source);
				}
				if(source.isStatic()) {
					if(cpm.passesThroughCall(source)) {
						return Collections.singleton(source);
					} else {
						return Collections.emptySet();
					}
				}
				if(!argMapping.containsKey(source.getBase())) {
					return Collections.emptySet();
				}
				if((cpm.isGetter() || cpm.isSetter()) && !cpm.passesThroughCall(source)) {
					return Collections.emptySet();
				}
				// || ((cpm.isGetter() || cpm.isSetter()) && !cpm.passesThroughCall(source))) {
				final Set<AccessGraph> toReturn = new HashSet<>();
				for(final Local derive : argMapping.get(source.getBase())) {
					if(derive == base) {
						if(AbstractFlowFunctions.hasCompatibleTypesForCall(source, destinationMethod.getDeclaringClass()) && realizeableFlow(source.getBaseType(), destinationMethod, s)) {
							toReturn.add(source.deriveWithNewLocal(derive, selectMostSpecificType(source.getBaseType(), destinationMethod.getDeclaringClass().getType())));
						}
            continue;
					}
					toReturn.add(source.deriveWithNewLocal(derive, source.getBaseType()));
				}
				return toReturn;
			}
		};
	}
	
	private boolean realizeableFlow(final Type baseType, final SootMethod destinationMethod, final Stmt s) {
		final InvokeExpr ie = s.getInvokeExpr();
		assert !(ie instanceof StaticInvokeExpr); 
		if(!(ie instanceof VirtualInvokeExpr) && !(ie instanceof InterfaceInvokeExpr)) {
			return true;
		}
		final SootMethod m = VirtualCalls.v().resolveNonSpecial((RefType) baseType, ie.getMethodRef().getSubSignature());
		return m == destinationMethod;
	}

	
	private FlowSet<SootField> usedStaticFields(final SootMethod destinationMethod) {
		return fpa.usedStaticFields(destinationMethod);
	}

	private FlowFunction<AccessGraph> interpretIf(final IfStmt curr, final Unit succ) {
		final Value cond = curr.getCondition();
		final boolean isNotEq = cond instanceof NeExpr;
		if(!isNotEq && !(cond instanceof EqExpr)) {
			return Identity.v();
		}
		final boolean isTarget = curr.getTarget() == succ;
		final ConditionExpr ce = (ConditionExpr) cond;
		if(ce.getOp1() instanceof Local && ce.getOp2() instanceof Local) {
			return Identity.v();
		}
		if(!(ce.getOp1() instanceof Local) && !(ce.getOp2() instanceof Local)) {
			return Identity.v();
		}
		assert (ce.getOp1() instanceof Local) != (ce.getOp2() instanceof Local) : curr + " " + cond;
		final Local cmp = (Local) (ce.getOp1() instanceof Local ? ce.getOp1() : ce.getOp2());
		final Value toComp = ce.getOp1() instanceof Local ? ce.getOp2() : ce.getOp1();
		// nullness check, we can interpret this
		if(toComp instanceof NullConstant) {
			final boolean opMustBeNull = (isNotEq && !isTarget) || (!isNotEq && isTarget);
			if(opMustBeNull) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.baseMatches(cmp) && source.getFieldCount() > 0) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						} 
					}
				};
			} else {
				return Identity.v();
			}
		// gross but sound heuristic
		} else if(toComp instanceof IntConstant && (((IntConstant)toComp).value == 0) && cmp.getName().startsWith("$")) {
			final SimpleLocalDefs sld = localDefCache.getUnchecked(icfg.getMethodOf(curr));
			final List<Unit> defs = sld.getDefsOfAt(cmp, curr);
			if(defs.size() != 1) {
				return Identity.v();
			}
			final Value rhs = ((DefinitionStmt)defs.get(0)).getRightOp();
			if(!(rhs instanceof InstanceOfExpr)) {
				return Identity.v();
			}
			final InstanceOfExpr ioe = (InstanceOfExpr) rhs;
			final Local typeChecked = (Local) ioe.getOp();
			final Type checked = ioe.getCheckType();
			final boolean mustBeType = (isTarget && isNotEq) || (!isTarget && !isNotEq);
			final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
			if(mustBeType) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(!source.baseMatches(typeChecked)) {
							return Collections.singleton(source);
						}
						final Type rtt = source.getBaseType();
						if(!fh.canStoreType(rtt, checked)) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			} else {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(!source.baseMatches(typeChecked)) {
							return Collections.singleton(source);
						}
						final Type rtt = source.getBaseType();
						if(fh.canStoreType(rtt, checked)) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			}
		} else {
			return Identity.v();
		}
	}
	
	/*
	 * Copy pasted from OnFlyCallGraphBuilder.java in Soot
	 */
	
	private FlowFunction<AccessGraph> getReflectionArgCallMapper(final Stmt callSite, final SootMethod dest, final int argIndex) {
		final Body b = dest.getActiveBody();
		final List<Value> v = callSite.getInvokeExpr().getArgs();
		final Value invokeArgArr = v.get(argIndex);
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source.baseAndFirstFieldMatches(invokeArgArr, AliasFinder.ARRAY_FIELD)) {
					final Type t = source.getFirstField().getType();
					final Set<Type> reachingType = Collections.singleton(t);
					final Set<AccessGraph> toReturn = new HashSet<>();
					for(int i = 0; i < dest.getParameterCount(); i++) {
						if(OnFlyCallGraphBuilder.isReflectionCompatible(fh, dest.getParameterType(i), reachingType)) {
							Type destType;
							if(dest.getParameterType(i) instanceof PrimType) {
								destType = dest.getParameterType(i);
							} else {
								destType = t;
							}
							toReturn.addAll(source.deriveWithNewLocal(b.getParameterLocal(i), destType).popFirstField());
						}
					}
					return toReturn;
				} else {
					return Collections.emptySet();
				}
			}
		};
	}
	
	private FlowFunction<AccessGraph> getInvokeReturnMapper(final Stmt callSite, final SootMethod dest, final Stmt exitStmt, final int arrayArgIndex) {
		final Body b = dest.getActiveBody();
		final List<Value> v = callSite.getInvokeExpr().getArgs();
		final Local invokeArgArr = (Local) (v.get(arrayArgIndex) instanceof Local ? v.get(arrayArgIndex) : null);
		final Local lhs = (Local) ((callSite instanceof AssignStmt) ? ((AssignStmt)callSite).getLeftOp() : null);
		final Value op = exitStmt instanceof ReturnStmt ? ((ReturnStmt)exitStmt).getOp() : null;
		
		final HashSet<Local> argLocals = new HashSet<>(b.getParameterLocals());
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				final Set<AccessGraph> toReturn = new HashSet<>();
				if(source.isStatic()) {
					toReturn.add(source);
				}
				if(op != null && lhs != null && source.baseMatches(op)) {
					if(source.getBaseType() instanceof PrimType) {
						assert source.getFieldCount() == 0;
						toReturn.add(source.deriveWithNewLocal(lhs, ((PrimType)source.getBaseType()).boxedType()));
					} else {
						toReturn.add(source.deriveWithNewLocal(lhs, source.getBaseType()));
					}
				}
				if(argLocals.contains(source.getBase()) && AtMostOnceProblem.propagateThroughCall(source) && invokeArgArr != null) {
					final WrappedSootField f = new WrappedSootField(AliasFinder.ARRAY_FIELD, source.getBaseType(), callSite);
					toReturn.add(source.deriveWithNewLocal(invokeArgArr, invokeArgArr.getType()).prependField(f));
				}
				return toReturn;
			}
		};
	}
	
	private final ReflectionDecider reflectionDecider = new ReflectionDecider();
	
	private FlowFunction<AccessGraph> handleSpecialCallFlow(final Stmt callSite, final SootMethod dest, final FlowSet<SootField> usedFields) {
		final NumberedString formalMethodSubSig = callSite.getInvokeExpr().getMethod().getNumberedSubSignature();
		final NumberedString calledMethodSubSig = dest.getNumberedSubSignature();
		if(
				// executor execute
			reflectionDecider.isExecutorExecute(formalMethodSubSig, calledMethodSubSig) ||
			// privilegd do
			reflectionDecider.isPrivilegedActionCall(callSite.getInvokeExpr(), calledMethodSubSig)) {
			final Value v = callSite.getInvokeExpr().getArg(0);
			assert !dest.isStatic();
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return Collections.singleton(source);
					} else if(source.baseMatches(v)) {
						return Collections.singleton(source.deriveWithNewLocal(thisLocal, thisLocal.getType()));
					} else if(source.isStatic() && usedFields.contains(source.getFirstField().getField())) {
						return Collections.singleton(source);
					} else {
						return Collections.emptySet();
					}
				}
			};
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			final CallParameterDecider cpm = paramDeciderCache.getUnchecked(callSite);
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						Collections.singleton(source);
					}
					if(!source.isStatic()) {
						return Collections.emptySet();
					}
					if(cpm.passesThroughCall(source)) {
						return Collections.singleton(source);
					} else {
						return Collections.emptySet();
					}
				}
			};
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			if(!dest.isConstructor()) {
				return KillAll.v();
			}
			final FlowFunction<AccessGraph> argArrayMapper = getReflectionArgCallMapper(callSite, dest, 0);
			final CallParameterDecider cpm = paramDeciderCache.getUnchecked(callSite);
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return Collections.singleton(source);
					}
					if(source.isStatic()) {
						if(cpm.passesThroughCall(source)) {
							return Collections.singleton(source);
						} else {
							return Collections.emptySet();
						}
					}
					return argArrayMapper.computeTargets(source);
				}
			};
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final List<Value> v = callSite.getInvokeExpr().getArgs();
			final Value baseArg = v.get(0);
			final FlowFunction<AccessGraph> argArrayMapper = getReflectionArgCallMapper(callSite, dest, 1);
			final CallParameterDecider cpm = paramDeciderCache.getUnchecked(callSite);
			if(!(v.get(0) instanceof Local)) {
				// static call
				if(!dest.isStatic()) {
					return KillAll.v();
				}
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						}
						if(source.isStatic()) {
							if(cpm.passesThroughCall(source)) {
								return Collections.singleton(source);
							} else {
								return Collections.emptySet();
							}
						}
						return argArrayMapper.computeTargets(source);
					}
				};
			} else {
				final Body b = dest.getActiveBody();
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source == zeroValue) {
							return Collections.singleton(source);
						}
						if(source.isStatic()) {
							if(cpm.passesThroughCall(source)) {
								return Collections.singleton(source);
							} else {
								return Collections.emptySet();
							}
						}
						if(source.baseMatches(baseArg)) {
							if(boomerang.forward.ForwardFlowFunctions.hasCompatibleTypesForCall(source, dest.getDeclaringClass())) {
								final Type newType = selectMostSpecificType(source.getBaseType(), dest.getDeclaringClass().getType());
								final AccessGraph newAbstraction = source.deriveWithNewLocal(b.getThisLocal(), newType);
								return Collections.singleton(newAbstraction);
							} else {
								return Collections.emptySet();
							}
						} else {
							return argArrayMapper.computeTargets(source);
						}
					}
				};
			}
		}
		return null;
	}
	
	private FlowFunction<AccessGraph> handleSpecialReturnFlow(final Stmt callSite, final Stmt exitStmt, final SootMethod dest) {
		final NumberedString formalMethodSubSig = callSite.getInvokeExpr().getMethod().getNumberedSubSignature();
		final NumberedString calledMethodSubSig = dest.getNumberedSubSignature();
		final boolean isPrivlegedDo = reflectionDecider.isPrivilegedActionCall(callSite.getInvokeExpr(), calledMethodSubSig);
		if(
				// executor execute
			reflectionDecider.isExecutorExecute(formalMethodSubSig, calledMethodSubSig) ||
			
			// privileged do with no return value
			(isPrivlegedDo && (!(callSite instanceof DefinitionStmt) || !(exitStmt instanceof ReturnStmt)))) {
			final Value v = callSite.getInvokeExpr().getArg(0);
			assert !dest.isStatic();
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return Collections.singleton(source);
					} else if(source.baseMatches(thisLocal) && v instanceof Local) {
						return Collections.singleton(source.deriveWithNewLocal((Local) v, v.getType()));
					} else if(source.isStatic()) {
						return Collections.singleton(source);
					} else {
						return Collections.emptySet();
					}
				}
			};
		} else if(isPrivlegedDo) {
			final Local lhs = (Local) ((DefinitionStmt)callSite).getLeftOp();
			final Value retValue = ((ReturnStmt)exitStmt).getOp();
			final Value v = callSite.getInvokeExpr().getArg(0);
			assert !dest.isStatic();
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return Collections.singleton(source);
					}
					if(source.isStatic()) {
						return Collections.singleton(source);
					}
					final HashSet<AccessGraph> toReturn = new HashSet<>();
					if(source.baseMatches(thisLocal) && v instanceof Local) {
						toReturn.add(source.deriveWithNewLocal((Local) v, v.getType()));
					}
					if(source.baseMatches(retValue)) {
						toReturn.add(source.deriveWithNewLocal(lhs, lhs.getType()));
					}
					return toReturn;
				}
			};
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			if(!(callSite instanceof AssignStmt)) {
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.isStatic() || source == zeroValue) {
							return Collections.singleton(source);
						} else {
							return Collections.emptySet();
						}
					}
				};
			}
			final Local lhs = (Local) ((AssignStmt)callSite).getLeftOp();
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source.isStatic() || source == zeroValue) {
						return Collections.singleton(source);
					} if(source.baseMatches(thisLocal)) {
						return Collections.singleton(source.deriveWithNewLocal(lhs, source.getBaseType()));
					} else {
						return Collections.emptySet();
					}
				}
			};
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			final FlowFunction<AccessGraph> returnMapper = getInvokeReturnMapper(callSite, dest, exitStmt, 0);
			if(!(callSite instanceof AssignStmt)) {
				return returnMapper;	
			}
			final Local lhs = (Local) ((AssignStmt)callSite).getLeftOp();
			assert dest.isConstructor();
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					if(source == zeroValue) {
						return Collections.singleton(source);
					}
					if(source.baseMatches(thisLocal)) {
						return Collections.singleton(source.deriveWithNewLocal(lhs, source.getBaseType()));
					} else {
						return returnMapper.computeTargets(source);
					}
				}
			};
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final List<Value> v = callSite.getInvokeExpr().getArgs();
			final FlowFunction<AccessGraph> returnMapper = getInvokeReturnMapper(callSite, dest, exitStmt, 1);
			final Local receiverLocal = (Local) (v.get(0) instanceof Local ? v.get(0) : null);
			if(dest.isStatic() || receiverLocal == null) {
				return returnMapper;
			}
			final Local thisLocal = dest.getActiveBody().getThisLocal();
			final SootMethod callerMethod = icfg.getMethodOf(callSite);
			return new FlowFunction<AccessGraph>() {
				@Override
				public Set<AccessGraph> computeTargets(final AccessGraph source) {
					final Set<AccessGraph> toReturn = returnMapper.computeTargets(source);
					if(source.baseMatches(thisLocal) &&
							!(callSite instanceof AssignStmt && ((AssignStmt)callSite).getLeftOp() == receiverLocal) &&
							AtMostOnceProblem.propagateThroughCall(source) && hasCompatibleReturnType(receiverLocal, source.getBaseType(), callerMethod)) {
						final Type mappedType = selectReturnType(receiverLocal, source.getBaseType(), callerMethod);
						toReturn.add(source.deriveWithNewLocal(receiverLocal, mappedType));
					}
					return toReturn; 
				}
			};
		}
		return null;
	}

	private final Type selectMostSpecificType(final Type t1, final Type t2) {
		if(!fh.canStoreType(t1, t2) && !fh.canStoreType(t2, t1)) {
			throw new RuntimeException("Broken invariant");
		}
		if(fh.canStoreType(t1, t2)) {
			return t1;
		} else {
			return t2;
		}
	}
	
	private boolean hasCompatibleReturnType(final Local argLocal, final Type returnedType, final SootMethod methodOf) {
		if(!methodOf.isStatic() && argLocal == methodOf.getActiveBody().getThisLocal()) {
			final Type t = methodOf.getDeclaringClass().getType();
			return fh.canStoreType(returnedType, t) || fh.canStoreType(t, returnedType);
		} else {
			return fh.canStoreType(returnedType, argLocal.getType()) || fh.canStoreType(argLocal.getType(), returnedType);
		}
	}
	
	private Type selectReturnType(final Local argLocal, final Type returnedType, final SootMethod methodOf) {
		if(!methodOf.isStatic() && argLocal == methodOf.getActiveBody().getThisLocal()) {
			return selectMostSpecificType(returnedType, methodOf.getDeclaringClass().getType());
		} else {
			return selectMostSpecificType(returnedType, argLocal.getType());
		}
	}
	
	private final LoadingCache<SootMethod, SimpleLocalDefs> localDefCache = CacheBuilder.newBuilder().build(new CacheLoader<SootMethod, SimpleLocalDefs>() {
		@Override
		public SimpleLocalDefs load(final SootMethod key) throws Exception {
			return new SimpleLocalDefs((UnitGraph) icfg.getOrCreateUnitGraph(key));
		} 
	});
	
	private Set<AccessGraph> handleReceiverPropagation(final Unit returnSite, 
			final Value kill, final InvokeExpr ie, final CallParameterDecider cpd,
			final AccessGraph source) {
		final HashSet<AccessGraph> toRet = new HashSet<>();
		assert ie instanceof InstanceInvokeExpr;
		final Local baseVar = (Local) ((InstanceInvokeExpr)ie).getBase();
		final AccessGraph tgtGraph = new AccessGraph(baseVar, baseVar.getType());
		if(AtMostOnceProblem.mayAliasType(baseVar.getType())) {
			Set<AccessGraph> aliases;
			try {
				aliases = aliasResolver.doAliasSearch(tgtGraph, returnSite).mayAliasSet();
			} catch (final BoomerangTimeoutException e) {
				aliases = Collections.emptySet();
			}
			for(final AccessGraph g : aliases) {
				if(g.getFieldCount() > 0) {
					propagationFields.add(g.getLastField().getField());
				}
			}
			toRet.addAll(aliases);
		}
		if(kill == null || !tgtGraph.baseMatches(kill)) {
			toRet.add(tgtGraph);
		}
		if((kill == null || !source.baseMatches(kill)) && !cpd.passesThroughCall(source)) {
			toRet.add(source);
		}
		return toRet;
	}
	public void dumpStats() {
		System.out.println(fieldWrites.size() + " field writes");
		System.out.println(fieldWrites);
		System.out.println(propagations.size() + " propagations");
		System.out.println(propagationFields.size() + " propagation fields");
		System.out.println(propagationFields);
	}
}
