package edu.washington.cse.instrumentation.analysis;

import heros.FlowFunction;
import heros.flowfunc.Identity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.FastHierarchy;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.NewExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.reflection.AbstractReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;
import soot.jimple.toolkits.callgraph.reflection.PluggableReflectionHandler;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.ifdssolver.IPathEdge;
import boomerang.mock.MockedDataFlow;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition;
import edu.washington.cse.instrumentation.analysis.functions.ClassifyDefinition.DefinitionType;
import edu.washington.cse.instrumentation.analysis.solver.SolverAwareFlowFunctions;
import edu.washington.cse.instrumentation.analysis.utils.ImmutableTwoElementSet;

public class AttributeModelExtension extends AbstractAnalysisModelExtension {
	private static final String SESSION_CLASS = "edu.washington.cse.servlet.SessionAttributes";
	private static final String REQUEST_CLASS = "edu.washington.cse.servlet.RequestAttributes";
	private static final String ATTRIBUTE_FIELD = "<edu.washington.cse.servlet.RequestAttributes: edu.washington.cse.servlet.RequestAttributes $INSTANCE>";
	private static final String SESSION_FIELD = "<edu.washington.cse.servlet.SessionAttributes: edu.washington.cse.servlet.SessionAttributes $INSTANCE>";

	private static final String[] attributeClasses = new String[]{
		REQUEST_CLASS,
		SESSION_CLASS
	};
	
	private static final String PUT_SUBSIG = "void setAttribute(java.lang.String,java.lang.Object)";
	private static final String GET_SUBSIG = "java.lang.Object getAttribute(java.lang.String)";
	
	private static final Map<String, String> INTF_TO_FIELD = new HashMap<>();
	private SootField attributePseudoField;
	private SootField sessionPseudoField;
	private AnalysisConfiguration config;
	static {
		INTF_TO_FIELD.put("javax.servlet.http.HttpSession", SESSION_FIELD);
		INTF_TO_FIELD.put("javax.servlet.http.HttpServletRequest", ATTRIBUTE_FIELD);
	}
	
	private final Map<String, SootField> SERVLET_TO_FIELD = new HashMap<>();
	private Set<SootField> servletFields;
	
	public AttributeModelExtension() { }
	
	@Override
	public void setupScene(final Scene v) {
		for(final String name : attributeClasses) {
			v.addBasicClass(name);
		}
	}
	
	@Override
	public void setConfig(final AnalysisConfiguration config) {
		this.config = config;
		
		attributePseudoField = Scene.v().getField(ATTRIBUTE_FIELD);
		sessionPseudoField = Scene.v().getField(SESSION_FIELD);
	}
	
	@Override
	public boolean isManagedStatic(final SootField s) {
		return s == attributePseudoField || isServletField(s);
	}
	
	@Override
	public boolean isManagedType(final Type t) {
		return t instanceof RefType && (
			((RefType)t).getClassName().equals(SESSION_CLASS) ||
			((RefType)t).getClassName().equals(REQUEST_CLASS)
		);
	}
	
	private boolean isMethodCall(final Unit u, final String sig) {
		final Stmt s = (Stmt) u;
		if(!s.containsInvokeExpr()) {
			return false;
		}
		final InvokeExpr ie = s.getInvokeExpr();
		return ie.getMethodRef().getSignature().equals(sig);
	}

	private boolean isSessionAttributeSet(final Unit u) {
		return isMethodCall(u, "<" + SESSION_CLASS + ": " + PUT_SUBSIG + ">");
	}
	
	private boolean isSessionAttributeRead(final Unit u) {
		return isMethodCallAssign(u, "<" + SESSION_CLASS + ": " + GET_SUBSIG + ">");
	}

	private boolean isRequestAttributeRead(final Unit u) {
		return isMethodCallAssign(u, "<" + REQUEST_CLASS + ": " + GET_SUBSIG + ">");
	}

	private boolean isMethodCallAssign(final Unit u, final String sig) {
		if(!isMethodCall(u, sig)) {
			return false;
		}
		return u instanceof AssignStmt;
	}

	private boolean isRequestAttributeSet(final Unit u) {
		return isMethodCall(u, "<" + REQUEST_CLASS + ": " + PUT_SUBSIG + ">");
	}
	
	private boolean isServletField(final SootField f) {
		return servletFields.contains(f);
	}
	
	final static class AccessFlags {
		public boolean accessesSession = false;
		public boolean accessesRequest = false;
		
		@Override
		public boolean equals(final Object o) {
			if(!(o instanceof AccessFlags)) {
				return false;
			}
			final AccessFlags other = (AccessFlags) o;
			return other.accessesSession == this.accessesSession &&
					other.accessesRequest == this.accessesRequest;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (accessesSession ? 1231 : 1237);
			result = prime * result + (accessesRequest ? 1231 : 1237);
			return result;
		}
	}
	

	@Override
	public SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> extendFunctions(
			final SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> delegate) {
		final BackwardFlowAnalysis<SootMethod, AccessFlags> transitiveAccessAnalysis = 
			new BackwardFlowAnalysis<SootMethod, AccessFlags>(AtMostOnceProblem.makeDirectedCallGraph(config.icfg)) {
			{
				this.doAnalysis();
			}

			@Override
			protected void flowThrough(final AccessFlags in, final SootMethod d, final AccessFlags out) {
				copy(in, out);
				if(out.accessesRequest && out.accessesSession) {
					return;
				}
				if(!d.hasActiveBody()) {
					return;
				}
				for(final Unit u : d.getActiveBody().getUnits()) {
					if(isRequestAttributeRead(u) || isRequestAttributeSet(u)) {
						out.accessesSession = true;
					}
					if(isSessionAttributeRead(u) || isSessionAttributeSet(u)) {
						out.accessesRequest = true;
					}
				}
			}

			@Override
			protected AccessFlags newInitialFlow() {
				return new AccessFlags();
			}

			@Override
			protected void merge(final AccessFlags in1, final AccessFlags in2, final AccessFlags out) {
				out.accessesRequest = in1.accessesRequest || in2.accessesRequest;
				out.accessesSession = in1.accessesSession || in2.accessesSession;
			}

			@Override
			protected void copy(final AccessFlags source, final AccessFlags dest) {
				dest.accessesSession = source.accessesSession;
				dest.accessesRequest = source.accessesRequest;
			}
		};
		return new SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver>() {
			
			@Override
			public FlowFunction<AccessGraph> getReturnFlowFunction(final Unit callSite, final SootMethod calleeMethod, final Unit exitStmt, final Unit returnSite) {
				final FlowFunction<AccessGraph> toReturn = delegate.getReturnFlowFunction(callSite, calleeMethod, exitStmt, returnSite);
				return preservationFunction(toReturn);
			}
			
			private boolean matchesTwoFields(final SootField target, final SootField pseudoField, final AccessGraph source) {
				return source.isStatic() && source.firstFieldMatches(pseudoField) && source.getFieldCount() > 1 && source.getRepresentative()[1].getField().equals(target);
			}
			
			private boolean isServletRead(final Unit curr) {
				if(!(curr instanceof AssignStmt)) {
					return false;
				}
				final AssignStmt as = (AssignStmt) curr;
				return as.getRightOp() instanceof InstanceFieldRef && isServletFieldRef(as.getRightOp());
			}

			private boolean isServletFieldRef(final Value value) {
				return SERVLET_TO_FIELD.containsKey(((InstanceFieldRef)value).getField().getDeclaringClass().getName());
			}

			private boolean isServletWrite(final Unit curr) {
				if(!(curr instanceof AssignStmt)) {
					return false;
				}
				final AssignStmt as = (AssignStmt) curr;
				return as.getLeftOp() instanceof InstanceFieldRef && isServletFieldRef(as.getLeftOp());
			}

			protected boolean isDummyRead(final Unit curr) {
				if(!(curr instanceof AssignStmt)) {
					return false;
				}
				final AssignStmt as = (AssignStmt) curr;
				return as.containsFieldRef() && isServletField(as.getFieldRef().getField());
			}
			
			@Override
			public FlowFunction<AccessGraph> getNormalFlowFunction(final Unit curr, final Unit succ) {
				if(isDummyRead(curr)) {
					return Identity.v();
				}
				final FlowFunction<AccessGraph> wrapped = preservationFunction(delegate.getNormalFlowFunction(curr, succ));
				if(isServletWrite(curr)) {
					final AssignStmt as = (AssignStmt) curr;
					final SootField target = as.getFieldRef().getField();
					final SootField pseudoField = SERVLET_TO_FIELD.get(target.getDeclaringClass().getName());
					final DefinitionType cl = ClassifyDefinition.classifyDef(as);
					if(cl.isKillRHS()) {
						return new FlowFunction<AccessGraph>() {
							@Override
							public Set<AccessGraph> computeTargets(final AccessGraph source) {
								if(matchesTwoFields(target, pseudoField, source)) {
									return Collections.emptySet();
								}
								return Collections.singleton(source);
							}

						};
					}
					final Local l = (Local) as.getRightOp();
					return new FlowFunction<AccessGraph>() {
						@Override
						public Set<AccessGraph> computeTargets(final AccessGraph source) {
							if(matchesTwoFields(target, pseudoField, source)) {
								return Collections.emptySet();
							} else if(source.baseMatches(l)) {
								final AccessGraph genFact = source
									.prependField(new WrappedSootField(target, source.getBaseType(), curr))
									.prependField(new WrappedSootField(pseudoField, pseudoField.getType(), curr)).makeStatic();
								return new ImmutableTwoElementSet<>(genFact, source);
							} else {
								return Collections.singleton(source);
							}
						}
					};
				} else if(isServletRead(curr)) {
					final AssignStmt as = (AssignStmt) curr;
					final SootField target = as.getFieldRef().getField();
					final SootField pseudoField = SERVLET_TO_FIELD.get(target.getDeclaringClass().getName());

					final Local l = (Local) ((AssignStmt)curr).getLeftOp();
					return new FlowFunction<AccessGraph>() {
						@Override
						public Set<AccessGraph> computeTargets(final AccessGraph source) {
							if(matchesTwoFields(target, pseudoField, source)) {
								final Set<AccessGraph> popped = source.popFirstField();
								final Set<AccessGraph> toReturn = new HashSet<>();
								toReturn.add(source);
								for(final AccessGraph p : popped) {
									toReturn.addAll(p.deriveWithNewLocal(l, p.getFirstField().getType()).popFirstField());
								}
								return toReturn;
							} else {
								return wrapped.computeTargets(source);
							}
						}
					};
				} else {
					return wrapped;
				}
			}

			@Override
			public FlowFunction<AccessGraph> getCallToReturnFlowFunction(final Unit callSite, final Unit returnSite) {
				final FlowFunction<AccessGraph> toReturn = preservationFunction(delegate.getCallToReturnFlowFunction(callSite, returnSite));
				final Stmt s = (Stmt) callSite;
				final InvokeExpr ie = s.getInvokeExpr();
				if(ie.getMethodRef().name().equals("LegatoKillRequest")) {
					return new FlowFunction<AccessGraph>() {
						@Override
						public Set<AccessGraph> computeTargets(final AccessGraph source) {
							if(source.isStatic() && source.firstFieldMatches(attributePseudoField)) {
								return Collections.emptySet();
							}
							return toReturn.computeTargets(source);
						}
					};
				} else if(ie.getMethodRef().name().equals("LegatoKillSession")) {
					return new FlowFunction<AccessGraph>() {
						@Override
						public Set<AccessGraph> computeTargets(final AccessGraph source) {
							if(source.isStatic() && source.firstFieldMatches(sessionPseudoField)) {
								return Collections.emptySet();
							}
							return toReturn.computeTargets(source);
						}
					};
				}
				if(isSessionAttributeSet(callSite)) {
					return pseudoLocalWrite(callSite, toReturn, sessionPseudoField);
				} else if(isRequestAttributeSet(callSite)) {
					return pseudoLocalWrite(callSite, toReturn, attributePseudoField);
				} else if(isSessionAttributeRead(callSite)) {
					return pseudoLocalRead(callSite, toReturn, sessionPseudoField);
				} else if(isRequestAttributeRead(callSite)) {
					return pseudoLocalRead(callSite, toReturn, attributePseudoField);
				} else {
					return toReturn;
				}
			}

			@Override
			public FlowFunction<AccessGraph> getCallFlowFunction(final Unit callStmt, final SootMethod destinationMethod) {
				final FlowFunction<AccessGraph> toReturn = delegate.getCallFlowFunction(callStmt, destinationMethod);
				final AccessFlags transitiveCall = transitiveAccessAnalysis.getFlowBefore(destinationMethod);
				if(!transitiveCall.accessesRequest && !transitiveCall.accessesSession) {
					return toReturn;
				}
				return new FlowFunction<AccessGraph>() {
					@Override
					public Set<AccessGraph> computeTargets(final AccessGraph source) {
						if(source.isStatic() && source.firstFieldMatches(sessionPseudoField)) {
							if(transitiveCall.accessesSession) {
								return Collections.singleton(source);
							} else {
								return Collections.emptySet();
							}
						} else if(!source.isStatic() && source.firstFieldMatches(attributePseudoField)) {
							if(transitiveCall.accessesRequest) {
								return Collections.singleton(source);
							} else {
								return Collections.emptySet();
							}
						} else {
							return toReturn.computeTargets(source);
						}
					}
				};
			}
			
			@Override
			public void setSolver(final InconsistentReadSolver solver) {
				delegate.setSolver(solver);
			}
		};
	}

	
	@Override
	public Set<SootMethod> ignoredMethods() {
		final HashSet<SootMethod> toReturn = new HashSet<>();
		for(final String cls : attributeClasses) {
			toReturn.add(Scene.v().getMethod("<" + cls + ": " + PUT_SUBSIG + ">"));
			toReturn.add(Scene.v().getMethod("<" + cls + ": " + GET_SUBSIG + ">"));
		}
		for(final String cls : new String[]{
			"edu.washington.cse.servlet.SimpleHttpRequest",
			"edu.washington.cse.servlet.SimpleSession"
		}) {
			toReturn.add(Scene.v().getMethod("<" + cls + ": " + PUT_SUBSIG + ">"));
			toReturn.add(Scene.v().getMethod("<" + cls + ": " + GET_SUBSIG + ">"));
		}
		return toReturn;
	}
	
	@Override
	public PluggableReflectionHandler getReflectionHandler() {
		return new AbstractReflectionHandler() {
			@Override
			public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
				if(m.equals(Scene.v().getMainMethod())) {
					processServletTypes(m);
				}
				final PatchingChain<Unit> units = m.getActiveBody().getUnits();
				final LocalGenerator localGen = new LocalGenerator(m.getActiveBody());
				final Iterator<Unit> it = units.snapshotIterator();
				while(it.hasNext()) {
					final Unit u = it.next();
					final Stmt stmt = (Stmt) u;
					final Jimple jj = Jimple.v();
					for(final Map.Entry<String, String> intfKV : INTF_TO_FIELD.entrySet()) {
						for(final String s : new String[]{PUT_SUBSIG, GET_SUBSIG}) {
							final String sig = "<" + intfKV.getKey() + ": " + s + ">";
							if(isMethodCall(u, sig)) {
								final InvokeExpr oldCall = stmt.getInvokeExpr();
								final String fieldSignature = intfKV.getValue();
								final String hostingClass = Scene.v().signatureToClass(fieldSignature);
								final Local temp = localGen.generateLocal(Scene.v().getRefType(hostingClass));
								final SootField pseudoField = Scene.v().getField(fieldSignature);
								
								final Unit tempRead = jj.newAssignStmt(temp, jj.newStaticFieldRef(pseudoField.makeRef()));
								units.insertBefore(tempRead, u);
								final SootMethod forwarded = Scene.v().getMethod("<" + hostingClass + ": " + s+ ">");
								final InvokeExpr newIE = jj.newVirtualInvokeExpr(temp, forwarded.makeRef(), oldCall.getArgs());
								stmt.getInvokeExprBox().setValue(newIE);
							}
						}
					}
					if(!stmt.containsFieldRef()) {
						continue;
					}
					final SootField sf = stmt.getFieldRef().getField();
					if(sf.isStatic()) {
						continue;
					}
					final InstanceFieldRef ifr = (InstanceFieldRef) stmt.getFieldRef();
					final String className = sf.getDeclaringClass().getName();
					if(!SERVLET_TO_FIELD.containsKey(className)) {
						continue;
					}
					final SootField instanceField = SERVLET_TO_FIELD.get(className);
					final Local tmp = localGen.generateLocal(sf.getDeclaringClass().getType());
					final AssignStmt as = jj.newAssignStmt(tmp, jj.newStaticFieldRef(instanceField.makeRef()));
					units.insertBefore(as, stmt);
					ifr.setBase(tmp);
				}
			}
		};
	}
	
	private void processServletTypes(final SootMethod m) {
		final FastHierarchy fh = Scene.v().getFastHierarchy();
		final RefType servletType = Scene.v().getSootClass("javax.servlet.GenericServlet").getType();
		final HashSet<Type> servletTypes = new HashSet<>();
		for(final Local l : m.getActiveBody().getLocals()) {
			if(fh.canStoreType(l.getType(), servletType)) {
				servletTypes.add(l.getType());
			}
		}
		final HashSet<RefType> instTracker = new HashSet<>();
		final PatchingChain<Unit> uChain = m.getActiveBody().getUnits();
		for(final Unit u : uChain) {
			for(final ValueBox vb : u.getUseBoxes()) {
				if(vb.getValue() instanceof NewExpr) {
					final NewExpr ne = (NewExpr) vb.getValue();
					final RefType bt = ne.getBaseType();
					if(!servletTypes.contains(bt)) {
						continue;
					}
					if(!instTracker.add(bt)) {
						servletFields = Collections.emptySet();
						return;
					}
				}
			}
		}
		for(final RefType rt : instTracker) {
			final SootClass sc = rt.getSootClass();
			final SootField sf = new SootField("$INSTANCE", rt, Modifier.STATIC | Modifier.PUBLIC);
			sc.addField(sf);
			SERVLET_TO_FIELD.put(sc.getName(), sf);
		}
		final Iterator<Unit> uIt = uChain.snapshotIterator();
		final Jimple j = Jimple.v();
		while(uIt.hasNext()) {
			final Stmt s = (Stmt) uIt.next();
			if(!s.containsInvokeExpr()) {
				continue;
			}
			final InvokeExpr ie = s.getInvokeExpr();
			if(!(ie instanceof SpecialInvokeExpr)) {
				continue;
			}
			final SpecialInvokeExpr sie = (SpecialInvokeExpr) ie;
			final String baseType = sie.getMethodRef().declaringClass().getName();
			if(sie.getMethodRef().name().equals("<init>") && SERVLET_TO_FIELD.containsKey(baseType)) {
				final Local base = (Local) sie.getBase();
				final StaticFieldRef sRef = j.newStaticFieldRef(SERVLET_TO_FIELD.get(baseType).makeRef());
				final AssignStmt as = j.newAssignStmt(sRef, base);
				uChain.insertAfter(as, s);
			}
		}
		servletFields = new HashSet<>(SERVLET_TO_FIELD.values());
	}

	public FlowFunction<AccessGraph> preservationFunction(final FlowFunction<AccessGraph> toReturn) {
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source.isStatic() && (source.firstFieldMatches(attributePseudoField) || source.firstFieldMatches(sessionPseudoField))) {
					return Collections.singleton(source);
				}
				return toReturn.computeTargets(source);
			}
		};
	}
	
	private FlowFunction<AccessGraph> pseudoLocalWrite(final Unit callSite, final FlowFunction<AccessGraph> delegate, final SootField pseudoField) {
		final Value l = ((Stmt)callSite).getInvokeExpr().getArgs().get(1);
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(!source.isStatic() && source.baseMatches(l)) {
					final AccessGraph attributeValue = new AccessGraph(null, null, new WrappedSootField[]{
						new WrappedSootField(pseudoField, pseudoField.getType(), null),
						new WrappedSootField(config.aliasResolver.containerContentField, source.getBaseType(), callSite)
					});
					if(source.getFieldCount() > 0) {
						attributeValue.appendGraph(source.getFieldGraph());
					}
					return new ImmutableTwoElementSet<>(attributeValue, source);
				} else {
					return delegate.computeTargets(source);
				}
			}
		};
	}
	
	private FlowFunction<AccessGraph> pseudoLocalRead(final Unit callSite, final FlowFunction<AccessGraph> preservation, final SootField attributePseudoField) {
		assert callSite instanceof AssignStmt;
		assert ((AssignStmt)callSite).getLeftOp() instanceof Local;
		final Local lhs = (Local) ((AssignStmt)callSite).getLeftOp();
		final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		return new FlowFunction<AccessGraph>() {
			@Override
			public Set<AccessGraph> computeTargets(final AccessGraph source) {
				if(source.baseMatches(lhs)) {
					return Collections.emptySet();
				} else if(source.isStatic() && source.firstFieldMatches(attributePseudoField)) {
					final AccessGraph contents;
					{
						final Set<AccessGraph> popped = source.popFirstField();
						assert popped.size() == 1;
						final AccessGraph first = popped.iterator().next();
						assert first.getFirstField().getField() == config.aliasResolver.containerContentField;
						contents = first;
					}
					final Type attributeType = contents.getFirstField().getType();
					final Type lType = lhs.getType();
					if(!fh.canStoreType(attributeType, lType) && !fh.canStoreType(lType, attributeType)) {
						return Collections.singleton(source);
					}
					final HashSet<AccessGraph> toReturn = new HashSet<>();
					toReturn.add(source);
					toReturn.addAll(contents.deriveWithNewLocal(lhs, attributeType).popFirstField());
					return toReturn;
				} else {
					return preservation.computeTargets(source);
				}
			}
		};
	}
	
	@Override
	public boolean supportsCodeRewrite() {
		return true;
	}
	
	@Override
	public boolean supportsAliasMocking() {
		return true;
	}
	
	@Override
	public MockedDataFlow getAliasForwardMock() {
		return new MockedDataFlow() {
			@Override
			public boolean handles(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params) {
				return isRequestAttributeRead(callSite) || isRequestAttributeSet(callSite) || isSessionAttributeRead(callSite) || isSessionAttributeSet(callSite);
			}
			
			@Override
			public boolean flowInto(final Unit callSite, final AccessGraph source, final InvokeExpr ie, final Value[] params) {
				return true;
			}
			
			@Override
			public Set<AccessGraph> computeTargetsOverCall(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source,
				final Value[] params, final IPathEdge<Unit, AccessGraph> edge, final Unit succ) {
				FlowFunction<AccessGraph> toApply = null;
				
				if(isRequestAttributeRead(callSite)) {
					toApply = pseudoLocalRead(callSite, Identity.<AccessGraph>v(), attributePseudoField);
				} else if(isRequestAttributeSet(callSite)) {
					toApply = pseudoLocalWrite(callSite, Identity.<AccessGraph>v(), attributePseudoField);
				} else if(isSessionAttributeRead(callSite)) {
					toApply = pseudoLocalRead(callSite, Identity.<AccessGraph>v(), sessionPseudoField);
				} else {
					assert isSessionAttributeSet(callSite);
					toApply = pseudoLocalWrite(callSite, Identity.<AccessGraph>v(), sessionPseudoField);
				}
				return toApply.computeTargets(source);
			}
		};
	}
	
	@Override
	public MockedDataFlow getAliasBackwardMock() {
		return new MockedDataFlow() {
			
			@Override
			public boolean handles(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params) {
				return isRequestAttributeRead(callSite) || isRequestAttributeSet(callSite) || isSessionAttributeRead(callSite) || isSessionAttributeSet(callSite);
			}
			
			@Override
			public boolean flowInto(final Unit callSite, final AccessGraph source, final InvokeExpr ie, final Value[] params) {
				return true;
			}
			
			@Override
			public Set<AccessGraph> computeTargetsOverCall(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source,
				final Value[] params, final IPathEdge<Unit, AccessGraph> edge, final Unit succ) {
				if(isRequestAttributeRead(callSite)) {
					return backwardReadFlow(callSite, source, attributePseudoField);
				} else if(isRequestAttributeSet(callSite)) {
					return backwardPutFlow(callSite, source, attributePseudoField);
				} else if(isSessionAttributeRead(callSite)) {
					return backwardReadFlow(callSite, source, sessionPseudoField);
				} else {
					assert isSessionAttributeSet(callSite);
					return backwardPutFlow(callSite, source, sessionPseudoField);
				}
			}

			public Set<AccessGraph> backwardPutFlow(final Unit callSite, final AccessGraph source, final SootField pseudoField) {
				if(!source.isStatic() || !source.firstFieldMatches(pseudoField)) {
					return Collections.singleton(source);
				}
				assert source.getFieldCount() > 1;
				final Set<AccessGraph> poppedSet = source.popFirstField();
				assert poppedSet.size() == 1;
				
				final AccessGraph popped = poppedSet.iterator().next();
				assert popped.isStatic() && popped.firstFieldMatches(config.aliasResolver.containerContentField);
				
				final Value putVal = ((Stmt)callSite).getInvokeExpr().getArg(2);
				if(!(putVal instanceof Local)) {
					return Collections.singleton(source);
				}
				final Local putLocal = (Local) putVal;
				final Type localType = putLocal.getType();
				
				final Type contentType = popped.getFirstField().getType();
				final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
				if(!fh.canStoreType(localType, contentType) && !fh.canStoreType(contentType, localType)) {
					return Collections.singleton(source);
				}
				final Set<AccessGraph> out = new HashSet<>();
				out.add(source);
				out.addAll(popped.deriveWithNewLocal(putLocal, contentType).popFirstField());
				return out;
			}

			public Set<AccessGraph> backwardReadFlow(final Unit callSite, final AccessGraph source, final SootField pseudoField) {
				final AssignStmt as = (AssignStmt) callSite;
				assert as.getLeftOp() instanceof Local;
				final Local lhs = (Local) as.getLeftOp();
				if(!source.isStatic() && source.baseMatches(lhs)) {
					final AccessGraph out = 
						source.prependField(new WrappedSootField(config.aliasResolver.containerContentField, source.getBaseType(), callSite))
							.prependField(new WrappedSootField(pseudoField, pseudoField.getType(), null)).makeStatic();
					return Collections.singleton(out);
				} else {
					return Collections.singleton(source);
				}
			}
		};
	}
}
