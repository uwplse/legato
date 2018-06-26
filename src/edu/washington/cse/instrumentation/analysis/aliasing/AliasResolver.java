package edu.washington.cse.instrumentation.analysis.aliasing;

import heros.solver.Pair;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import soot.Local;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.solver.cfg.BackwardsInfoflowCFG;
import soot.jimple.infoflow.solver.cfg.InfoflowCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.exceptions.UnitThrowAnalysis;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.CollectionFlowUniverse;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import boomerang.AliasFinder;
import boomerang.BoomerangContext;
import boomerang.BoomerangOptions;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.cache.AliasResults;
import boomerang.cache.ResultCache;
import boomerang.context.IContextRequester;
import boomerang.context.NoContextRequester;
import boomerang.debug.NullDebugger;
import boomerang.ifdssolver.DefaultIFDSTabulationProblem.Direction;
import boomerang.ifdssolver.IPathEdge;
import boomerang.mock.MockedDataFlow;
import boomerang.preanalysis.WholeProgramFieldAnalysis;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AnalysisModelExtension;
import edu.washington.cse.instrumentation.analysis.functions.BoomerangBackwardReflectionHandler;
import edu.washington.cse.instrumentation.analysis.functions.BoomerangForwardReflectionHandler;
import edu.washington.cse.instrumentation.analysis.functions.ReflectionDecider;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager.PropagationTarget;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;

public class AliasResolver {

	private final InfoflowCFG baseCFG;
	private final BackwardsInfoflowCFG backwardsCFG;
	private final IContextRequester requester;
	public final SootField containerContentField = new SootField("<<contents>>", Scene.v().getRefType("java.lang.Object"));
	private final PropagationManager propagationManager;
	private final ReflectionDecider reflectionDecider;
	private PrintStream aliasLogStream;
	private final AnalysisModelExtension extension;
	private static final Stopwatch aliasTimer = Stopwatch.createUnstarted();
	public static final boolean CACHE_EDGES = Boolean.parseBoolean(System.getProperty("legato.alias-caching", "false"));
	public static final int RESET_CACHE_INTERVAL = Integer.parseInt(System.getProperty("legato.cache-flush-interval", "-1"));
	public static final String ALIAS_QUERY_LOG = System.getProperty("legato.alias-log");
	public static final boolean LOG_ALIASES = ALIAS_QUERY_LOG != null;
	
	public AliasResolver(final JimpleBasedInterproceduralCFG icfg, final PropagationManager pm, final Collection<SootMethod> ignored, final AnalysisModelExtension ext) {
		final WholeProgramFieldAnalysis wpfa = new WholeProgramFieldAnalysis(icfg, Scene.v().getEntryPoints());
		baseCFG = new InfoflowCFG(wpfa);
		backwardsCFG = new BackwardsInfoflowCFG(baseCFG, wpfa);
		requester = new NoContextRequester();
		this.propagationManager = pm;
		AliasFinder.IGNORED_METHODS.addAll(ignored);
		
		Scene.v().getSootClass("java.lang.Object").addField(containerContentField);
		
		if(LOG_ALIASES) {
			try {
				this.aliasLogStream = new PrintStream(new File(ALIAS_QUERY_LOG));
			} catch (final FileNotFoundException e) {
				this.aliasLogStream = null;
			}
		} else {
			this.aliasLogStream = null;
		}
		
		this.reflectionDecider = new ReflectionDecider();
		this.extension = ext;
	}
	
	public static long getTotalAliasTime() {
		return aliasTimer.elapsed(TimeUnit.MILLISECONDS);
	}

	private static final BoomerangOptions opts = new BoomerangOptions();
	
	static {
		opts.setQueryBudget(10000);
	}
	
	private static class MethodTracer extends NullDebugger {
		private final Set<SootMethod> visited = new HashSet<>();
		
		@Override
		public void addIncoming(final Direction dir, final SootMethod callee, final Pair<Unit, AccessGraph> pair, final IPathEdge<Unit, AccessGraph> pe) {
			visited.add(callee);
		}
	}
	
	public BoomerangContext getContext() {
		final BoomerangContext bc = new BoomerangContext(baseCFG, backwardsCFG, opts) {
			@Override
			public boolean isFieldAllowedForType(final Type t, final SootField f) {
				if(extension.isManagedType(t)) {
					return true;
				}
				if(f == containerContentField) {
					return propagationManager.isContainerType(t);
				}
				return super.isFieldAllowedForType(t, f);
			}
		};
		bc.backwardMockHandler = getBackwardMockHandler(bc);
		bc.forwardMockHandler = getForwardMockHandler(bc);
		bc.forwardReflectionHandler = new BoomerangForwardReflectionHandler(bc, reflectionDecider);
		bc.backwardReflectionHandler = new BoomerangBackwardReflectionHandler(bc, reflectionDecider);
		bc.debugger = new MethodTracer();
		return bc;
	}
	
	private MockedDataFlow getBackwardMockHandler(final BoomerangContext bc) {
		final MockedDataFlow fst = new BackwardContainerMockDataFlow(bc, propagationManager, containerContentField);
		if(!extension.supportsAliasMocking()) {
			return fst;
		}
		final MockedDataFlow snd = extension.getAliasBackwardMock();
		return aggregateMock(fst, snd);
	}

	public MockedDataFlow aggregateMock(final MockedDataFlow fst, final MockedDataFlow snd) {
		return new MockedDataFlow() {
			@Override
			public boolean handles(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params) {
				return fst.handles(callSite, invokeExpr, source, params) || snd.handles(callSite, invokeExpr, source, params);
			}
			
			@Override
			public boolean flowInto(final Unit callSite, final AccessGraph source, final InvokeExpr ie, final Value[] params) {
				return fst.flowInto(callSite, source, ie, params) || snd.flowInto(callSite, source, ie, params);
			}
			
			@Override
			public Set<AccessGraph> computeTargetsOverCall(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source,
					final Value[] params, final IPathEdge<Unit, AccessGraph> edge, final Unit succ) {
				if(fst.handles(callSite, invokeExpr, source, params)) {
					return fst.computeTargetsOverCall(callSite, invokeExpr, source, params, edge, succ);
				} else {
					return snd.computeTargetsOverCall(callSite, invokeExpr, source, params, edge, succ);
				}
			}
		};
	}
	
	private MockedDataFlow getForwardMockHandler(final BoomerangContext bc) {
		final MockedDataFlow fst = new ForwardContainerMockDataFlow(bc, propagationManager, containerContentField);
		if(!extension.supportsAliasMocking()) {
			return fst;
		}
		return aggregateMock(fst, extension.getAliasForwardMock());
		
	}
	
	private static class LeaksField extends RuntimeException {

		public LeaksField(final String msg) {
			super(msg);
		}
	}

	private BoomerangContext context = null;
	private final LoadingCache<SootField, Boolean> neverEscapes = CacheBuilder.newBuilder().build(new CacheLoader<SootField, Boolean>() {
		@Override
		public Boolean load(final SootField key) throws Exception {
			final SootClass cls = key.getDeclaringClass();
			if(cls.isPhantom()) {
				return false;
			}
			for(final SootMethod m : cls.getMethods()) {
				if(!m.isConcrete()) {
					return false;
				}
				if(!m.hasActiveBody()) {
					continue;
				}
				if(leaksField(m, key)) {
					return false;
				}
			}
			return true;
		}
		
		private boolean leaksField(final SootMethod m, final SootField key) {
			try {
				runLeakAnalysis(m, key);
				return false;
			} catch(final LeaksField f) {
				return true;
			}
		}
		
		private void runLeakAnalysis(final SootMethod m, final SootField key) {
			new ForwardFlowAnalysis<Unit, FlowSet<Local>>(new ExceptionalUnitGraph(m.getActiveBody(), UnitThrowAnalysis.v(), false)) {
				
				FlowUniverse<Local> universe = new CollectionFlowUniverse<>(m.getActiveBody().getLocals());
				FlowSet<Local> EMPTY = new ArrayPackedSet<>(universe);

				@Override
				protected void flowThrough(final FlowSet<Local> in, final Unit d, final FlowSet<Local> out) {
					in.copy(out);
					final Stmt s = (Stmt) d;
					if(s.containsInvokeExpr()) {
						final InvokeExpr ie = s.getInvokeExpr();
						final SootMethod m = ie.getMethod();
						boolean isSafeMethod = false;
						if(propagationManager.isPropagationMethod(m)) {
							final PropagationSpec spec = propagationManager.getPropagationSpec(d);
							isSafeMethod = spec.getPropagationTarget() == PropagationTarget.IDENTITY || spec.getPropagationTarget().isContainerAbstraction() ||
									(spec.getPropagationTarget() == PropagationTarget.RETURN && !Scene.v().getOrMakeFastHierarchy().canStoreType(key.getType(), m.getReturnType()));
						}
						if(!isSafeMethod) {
							for(final Value v : ie.getArgs()) {
								if(v instanceof Local && in.contains((Local) v)) {
									throw new LeaksField(d + " " + in);
								}
							}
							if(ie instanceof InstanceInvokeExpr) {
								final Local base = (Local) ((InstanceInvokeExpr) ie).getBase();
								if(in.contains(base)) {
									throw new LeaksField(d + " " + in);
								}
							}
						}
					}
					if(s instanceof AssignStmt) {
						final Value rhs = ((AssignStmt) s).getRightOp();
						final Value lhs = ((AssignStmt) s).getLeftOp();
						if(rhs instanceof Local && in.contains((Local) rhs)) {
							if(lhs instanceof Local) {
								out.add((Local) lhs);
							} else {
								throw new LeaksField(d + " " + in);
							}
						}
						if(rhs instanceof Constant) {
							if(lhs instanceof Local) {
								out.remove((Local) lhs);
							}
						} else if(rhs instanceof InstanceFieldRef && ((InstanceFieldRef) rhs).getField() == key) {
							assert lhs instanceof Local;
							out.add((Local) lhs);
						} else if(Scene.v().getOrMakeFastHierarchy().canStoreType(key.getType(), lhs.getType())) {
							if(lhs instanceof Local) {
								out.add((Local) lhs);
							}
						}
					}
					if(s instanceof ReturnStmt) {
						final Value op = ((ReturnStmt) s).getOp();
						if(op instanceof Local && in.contains((Local) op)) {
							throw new LeaksField(d + " " + in);
						}
					}
				}

				@Override
				protected FlowSet<Local> newInitialFlow() {
					return EMPTY.clone();
				}

				@Override
				protected void merge(final FlowSet<Local> in1, final FlowSet<Local> in2, final FlowSet<Local> out) {
					in1.union(in2, out);
				}

				@Override
				protected void copy(final FlowSet<Local> source, final FlowSet<Local> dest) {
					source.copy(dest);
				}
				
				{
					doAnalysis();
				}
			};
		}
		
	});
	
	public AliasResults doAliasSearch(final AccessGraph graph, final Unit stmt) {
		if(AnalysisConfiguration.CONSERVATIVE_HEAP) {
			return new AliasResults();
		}
		if(context == null) {
			context = getContext();
		}
		final AliasFinder af = new AliasFinder(context);
		AliasResults ar = new AliasResults();
		if(!graph.isStatic() && graph.getFieldCount() == 1 && graph.getFirstField().getField().isPrivate() && neverEscapes.getUnchecked(graph.getFirstField().getField())) {
			final AliasResults tempResults;
			aliasTimer.start();
			try {
				af.startQuery();
				tempResults = af.findAliasAtStmt(new AccessGraph(graph.getBase(), graph.getBaseType()), stmt, requester);
			} finally {
				aliasTimer.stop();
			}
			final Multimap<Pair<Unit, AccessGraph>, AccessGraph> prepended = HashMultimap.create();
			for(final Map.Entry<Pair<Unit, AccessGraph>, AccessGraph> entry : tempResults.entries()) {
				prepended.put(entry.getKey(), entry.getValue().appendFields(new WrappedSootField[]{graph.getFirstField()}));
			}
			ar = new AliasResults(prepended);
		} else {
			aliasTimer.start();
			try {
				af.startQuery();
				ar = af.findAliasAtStmt(graph, stmt, requester);
			} finally {
				aliasTimer.stop();
			}
		}
		if(!CACHE_EDGES) {
			final ResultCache cache = af.context.querycache;
			context = getContext();
			context.querycache = cache;
		} else if(CACHE_EDGES && (RESET_CACHE_INTERVAL != -1)) {
			checkAndReset();
		}
		if(LOG_ALIASES) {
			logResults(graph, stmt, ar);
		}
		return ar;
	}
	
	private void logResults(final AccessGraph graph, final Unit stmt, final AliasResults ar) {
		if(aliasLogStream == null) {
			return;
		}
		this.aliasLogStream.print(graph);
		this.aliasLogStream.print(" ");
		this.aliasLogStream.print(stmt);
		this.aliasLogStream.print(" ");
		this.aliasLogStream.println(this.canonAliasToString(ar));
	}

	private int numQueries = 0;
	
	private void checkAndReset() {
		++numQueries;
		if(numQueries % 75 == 0) {
			final ResultCache querycache = context.querycache;
			context = getContext();
			context.querycache = querycache;
		}
	}
	
	private String canonAliasToString(final AliasResults ar) {
		final ArrayList<Pair<Unit, AccessGraph>> keys = new ArrayList<>(ar.keySet());
		Collections.sort(keys, new Comparator<Pair<Unit, AccessGraph>>() {
			@Override
			public int compare(final Pair<Unit, AccessGraph> o1, final Pair<Unit, AccessGraph> o2) {
				return o1.toString().compareTo(o2.toString());
			}
		});
		final StringBuilder sb = new StringBuilder();
		sb.append("{");
		for(int i = 0; i < keys.size(); i++) {
			if(i != 0) {
				sb.append(",");
			}
			sb.append(keys.get(i).toString());
			sb.append("=[");
			final ArrayList<AccessGraph> aliases = new ArrayList<>(ar.get(keys.get(i)));
			Collections.sort(aliases, new Comparator<AccessGraph>() {
				@Override
				public int compare(final AccessGraph o1, final AccessGraph o2) {
					return o1.toString().compareTo(o2.toString());
				}
			});
			for(int j = 0; j < aliases.size(); j++) {
				if(j != 0) {
					sb.append(", ");
				}
				sb.append(aliases.get(j).toString());
			}
			sb.append("]");
		}
		sb.append("}");
		return sb.toString();
	}

	public BoomerangContext getOrMakeContext() {
		if(context != null) {
			return context;
		}
		return context = getContext();
	}
}
