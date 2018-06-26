package edu.washington.cse.instrumentation.analysis.solver;

import heros.DontSynchronize;
import heros.EdgeFunction;
import heros.EdgeFunctionCache;
import heros.EdgeFunctions;
import heros.FlowFunction;
import heros.FlowFunctionCache;
import heros.FlowFunctions;
import heros.IDETabulationProblem;
import heros.JoinLattice;
import heros.SynchronizedBy;
import heros.ZeroedFlowFunctions;
import heros.solver.CountingThreadPoolExecutor;
import heros.solver.IDESolver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.MethodContext;
import soot.MethodOrMethodContext;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class SimpleContextSensitiveAnalysis<D,V,I extends JimpleBasedInterproceduralCFG> {
	
	public static CacheBuilder<Object, Object> DEFAULT_CACHE_BUILDER = CacheBuilder.newBuilder()
			.concurrencyLevel(Runtime.getRuntime().availableProcessors()).initialCapacity(10000).softValues();

  protected static final Logger logger = LoggerFactory.getLogger(SimpleContextSensitiveAnalysis.class);

  //enable with -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
  public static final boolean DEBUG = logger.isDebugEnabled();

	protected CountingThreadPoolExecutor executor;
	
	@DontSynchronize("only used by single thread")
	protected int numThreads;
	
	@SynchronizedBy("thread safe data structure, only modified internally")
	protected final I icfg;
	
	protected final ConcurrentMap<MethodOrMethodContext,Table<Unit,D,V>> env = new ConcurrentHashMap<>();
	
//	//stores summaries that were queried before they were computed
//	//see CC 2010 paper by Naeem, Lhotak and Rodriguez
//	@SynchronizedBy("consistent lock on 'incoming'")
//	protected final Table<N,D,Table<N,D,EdgeFunction<V>>> endSummary = HashBasedTable.create();

	//edges going along calls
	//see CC 2010 paper by Naeem, Lhotak and Rodriguez
	@SynchronizedBy("consistent lock on field")
	protected final MultiMap<MethodOrMethodContext, MethodOrMethodContext> incoming = new HashMultiMap<>();

	//stores the return sites (inside callers) to which we have unbalanced returns
	//if followReturnPastSeeds is enabled
	@SynchronizedBy("use of ConcurrentHashMap")
	protected final Set<Unit> unbalancedRetSites;

	@DontSynchronize("stateless")
	protected final FlowFunctions<Unit, D, SootMethod> flowFunctions;

	@DontSynchronize("stateless")
	protected final EdgeFunctions<Unit,D,SootMethod,V> edgeFunctions;

	@DontSynchronize("only used by single thread")
	protected final Map<Unit,Set<D>> initialSeeds;

	@DontSynchronize("stateless")
	protected final JoinLattice<V> valueLattice;
	
	@DontSynchronize("stateless")
	protected final EdgeFunction<V> allTop;

	@DontSynchronize("benign races")
	public long flowFunctionApplicationCount;

	@DontSynchronize("benign races")
	public long flowFunctionConstructionCount;
	
	@DontSynchronize("benign races")
	public long propagationCount;
	
	@DontSynchronize("benign races")
	public long durationFlowFunctionConstruction;
	
	@DontSynchronize("benign races")
	public long durationFlowFunctionApplication;

	@DontSynchronize("stateless")
	protected final D zeroValue;
	
	@DontSynchronize("readOnly")
	protected final FlowFunctionCache<Unit,D,SootMethod> ffCache; 

	@DontSynchronize("readOnly")
	protected final EdgeFunctionCache<Unit,D,SootMethod,V> efCache;

	@DontSynchronize("readOnly")
	protected final boolean followReturnsPastSeeds;

	@DontSynchronize("readOnly")
	protected final boolean computeValues;

	/**
	 * Creates a solver for the given problem, which caches flow functions and edge functions.
	 * The solver must then be started by calling {@link #solve()}.
	 */
	public SimpleContextSensitiveAnalysis(final IDETabulationProblem<Unit,D,SootMethod,V,I> tabulationProblem) {
		this(tabulationProblem, DEFAULT_CACHE_BUILDER, DEFAULT_CACHE_BUILDER);
	}

	/**
	 * Creates a solver for the given problem, constructing caches with the given {@link CacheBuilder}. The solver must then be started by calling
	 * {@link #solve()}.
	 * @param flowFunctionCacheBuilder A valid {@link CacheBuilder} or <code>null</code> if no caching is to be used for flow functions.
	 * @param edgeFunctionCacheBuilder A valid {@link CacheBuilder} or <code>null</code> if no caching is to be used for edge functions.
	 */
	public SimpleContextSensitiveAnalysis(final IDETabulationProblem<Unit,D,SootMethod,V,I> tabulationProblem, 
			@SuppressWarnings("rawtypes") CacheBuilder flowFunctionCacheBuilder,
			@SuppressWarnings("rawtypes") CacheBuilder edgeFunctionCacheBuilder) {
		if(logger.isDebugEnabled()) {
			if(flowFunctionCacheBuilder != null)
				flowFunctionCacheBuilder = flowFunctionCacheBuilder.recordStats();
			if(edgeFunctionCacheBuilder != null)
				edgeFunctionCacheBuilder = edgeFunctionCacheBuilder.recordStats();
		}
		this.zeroValue = tabulationProblem.zeroValue();
		this.icfg = tabulationProblem.interproceduralCFG();		
		FlowFunctions<Unit, D, SootMethod> flowFunctions = tabulationProblem.autoAddZero() ?
				new ZeroedFlowFunctions<Unit,D,SootMethod>(tabulationProblem.flowFunctions(), tabulationProblem.zeroValue()) : tabulationProblem.flowFunctions(); 
		EdgeFunctions<Unit, D, SootMethod, V> edgeFunctions = tabulationProblem.edgeFunctions();
		if(flowFunctionCacheBuilder!=null) {
			ffCache = new FlowFunctionCache<Unit,D,SootMethod>(flowFunctions, flowFunctionCacheBuilder);
			flowFunctions = ffCache;
		} else {
			ffCache = null;
		}
		if(edgeFunctionCacheBuilder!=null) {
			efCache = new EdgeFunctionCache<Unit,D,SootMethod,V>(edgeFunctions, edgeFunctionCacheBuilder);
			edgeFunctions = efCache;
		} else {
			efCache = null;
		}
		this.flowFunctions = flowFunctions;
		this.edgeFunctions = edgeFunctions;
		this.initialSeeds = tabulationProblem.initialSeeds();
		this.unbalancedRetSites = Collections.newSetFromMap(new ConcurrentHashMap<Unit, Boolean>());
		this.valueLattice = tabulationProblem.joinLattice();
		this.allTop = tabulationProblem.allTopFunction();
		this.followReturnsPastSeeds = tabulationProblem.followReturnsPastSeeds();
		this.numThreads = Math.max(1,tabulationProblem.numThreads());
		this.computeValues = tabulationProblem.computeValues();
		this.executor = getExecutor();
	}

	/**
	 * Runs the solver on the configured problem. This can take some time.
	 */
	public void solve() {		
		submitInitialSeeds();
		awaitCompletionComputeValuesAndShutdown();
	}

	/**
	 * Schedules the processing of initial seeds, initiating the analysis.
	 * Clients should only call this methods if performing synchronization on
	 * their own. Normally, {@link #solve()} should be called instead.
	 */
	protected void submitInitialSeeds() {
		for(final Entry<Unit, Set<D>> seed: initialSeeds.entrySet()) {
			final Unit startPoint = seed.getKey();
			for(final D val: seed.getValue()) {
				propagate(icfg.getMethodOf(startPoint), startPoint, val, valueLattice.topElement(), null, false);
			}
		}
	}

	/**
	 * Awaits the completion of the exploded super graph. When complete, computes result values,
	 * shuts down the executor and returns.
	 */
	protected void awaitCompletionComputeValuesAndShutdown() {
		{
			final long before = System.currentTimeMillis();
			//run executor and await termination of tasks
			runExecutorAndAwaitCompletion();
			durationFlowFunctionConstruction = System.currentTimeMillis() - before;
		}
		if(logger.isDebugEnabled())
			printStats();
		
		//ask executor to shut down;
		//this will cause new submissions to the executor to be rejected,
		//but at this point all tasks should have completed anyway
		executor.shutdown();
		//similarly here: we await termination, but this should happen instantaneously,
		//as all tasks should have completed
		runExecutorAndAwaitCompletion();
	}

	/**
	 * Runs execution, re-throwing exceptions that might be thrown during its execution.
	 */
	private void runExecutorAndAwaitCompletion() {
		try {
			executor.awaitCompletion();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		final Throwable exception = executor.getException();
		if(exception!=null) {
			throw new RuntimeException("There were exceptions during IDE analysis. Exiting.",exception);
		}
	}

  /**
   * Dispatch the processing of a given edge. It may be executed in a different thread.
   * @param edge the edge to process
   */
  protected void scheduleEdgeProcessing(final ContextValueNode<MethodOrMethodContext, Unit, D> edge){
  	// If the executor has been killed, there is little point
  	// in submitting new tasks
  	if (executor.isTerminating())
  		return;
  	executor.execute(new PathEdgeProcessingTask(edge));
  	propagationCount++;
  }

	/**
	 * Lines 13-20 of the algorithm; processing a call site in the caller's context.
	 * 
	 * For each possible callee, registers incoming call edges.
	 * Also propagates call-to-return flows and summarized callee flows within the caller. 
	 * 
	 * @param edge an edge whose target node resembles a method call
	 */
	private void processCall(final ContextValueNode<MethodOrMethodContext, Unit,D> edge) {
		final MethodOrMethodContext callerContext = edge.context();
		final Unit n = edge.getTarget(); // a call node; line 14...

		final D d2 = edge.factAtTarget();
		final Collection<Unit> returnSiteNs = icfg.getReturnSitesOfCallAt(n);
		
		logger.trace("Processing call to {} with {} -> {} in {}", n, callerContext, d2, icfg.getMethodOf(n));
		
		final Table<Unit, D, V> methodEnv = env.get(callerContext);
		final V valueAtCall = methodEnv.contains(n, d2) ? methodEnv.get(n, d2) : valueLattice.topElement();
		
		//for each possible callee
		final Collection<SootMethod> callees = icfg.getCalleesOfCallAt(n);
		for(final SootMethod sCalledProcN: callees) { //still line 14
			final MethodOrMethodContext calleeContext = MethodContext.v(sCalledProcN, n);
			//compute the call-flow function
			final FlowFunction<D> function = flowFunctions.getCallFlowFunction(n, sCalledProcN);
			flowFunctionConstructionCount++;
			final Set<D> res = computeCallFlowFunction(function, null, d2);
			//for each callee's start point(s)
			//for each result node of the call-flow function
			for(final D d3: res) {
//					//create initial self-loop
				for(final Unit sP : icfg.getStartPointsOf(sCalledProcN)) {
					propagate(calleeContext, sP, d3, edgeFunctions.getCallEdgeFunction(n, d2, sCalledProcN, d3).computeTarget(valueAtCall), n, false); //line 15
				}

				synchronized (incoming) {
					incoming.put(calleeContext, callerContext);
				}
			}
			final Table<Unit, D, V> calledMethodEnv = env.get(calleeContext);
			for(final Unit eP : icfg.getEndPointsOf(sCalledProcN)) {
				Map<D, V> envAtReturn;
				synchronized(calledMethodEnv) {
					envAtReturn = calledMethodEnv.row(eP);
				}
				for(final Map.Entry<D, V> exitValue : envAtReturn.entrySet()) {
					for(final Unit retSite : returnSiteNs) {
						final FlowFunction<D> rf = flowFunctions.getReturnFlowFunction(n, sCalledProcN, eP, retSite);
						final D exitFact = exitValue.getKey();
						for(final D returnedFact : rf.computeTargets(exitFact)) {
							final EdgeFunction<V> retTransform = edgeFunctions.getReturnEdgeFunction(n, sCalledProcN, eP, exitFact, retSite, returnedFact);
							propagate(callerContext, retSite, returnedFact, retTransform.computeTarget(exitValue.getValue()), n, false);
						}
					}
				}
			}
		}
		//line 17-19 of Naeem/Lhotak/Rodriguez		
		//process intra-procedural flows along call-to-return flow functions
		for (final Unit returnSiteN : returnSiteNs) {
			final FlowFunction<D> callToReturnFlowFunction = flowFunctions.getCallToReturnFlowFunction(n, returnSiteN);
			flowFunctionConstructionCount++;
			final Set<D> returnFacts = callToReturnFlowFunction.computeTargets(d2);
			for(final D d3: returnFacts) {
				final EdgeFunction<V> edgeFnE = edgeFunctions.getCallToReturnEdgeFunction(n, d2, returnSiteN, d3);
				propagate(callerContext, returnSiteN, d3, edgeFnE.computeTarget(valueAtCall), n, false);
			}
		}
	}
	
	/**
	 * Computes the call flow function for the given call-site abstraction
	 * @param callFlowFunction The call flow function to compute
	 * @param d1 The abstraction at the current method's start node.
	 * @param d2 The abstraction at the call site
	 * @return The set of caller-side abstractions at the callee's start node
	 */
	protected Set<D> computeCallFlowFunction
			(final FlowFunction<D> callFlowFunction, final D d1, final D d2) {
		return callFlowFunction.computeTargets(d2);
	}

	/**
	 * Computes the call-to-return flow function for the given call-site
	 * abstraction
	 * @param callToReturnFlowFunction The call-to-return flow function to
	 * compute
	 * @param d1 The abstraction at the current method's start node.
	 * @param d2 The abstraction at the call site
	 * @return The set of caller-side abstractions at the return site
	 */
	protected Set<D> computeCallToReturnFlowFunction
			(final FlowFunction<D> callToReturnFlowFunction, final D d1, final D d2) {
		return callToReturnFlowFunction.computeTargets(d2);
	}
	
	/**
	 * Lines 21-32 of the algorithm.
	 * 
	 * Stores callee-side summaries.
	 * Also, at the side of the caller, propagates intra-procedural flows to return sites
	 * using those newly computed summaries.
	 * 
	 * @param edge an edge whose target node resembles a method exits
	 */
	protected void processExit(final ContextValueNode<MethodOrMethodContext, Unit,D> edge) {
		final Unit n = edge.getTarget(); // an exit node; line 21...
		final SootMethod methodThatNeedsSummary = icfg.getMethodOf(n);
		
		final MethodOrMethodContext d1 = edge.context();
		final D d2 = edge.factAtTarget();
		
		//for each of the method's start points, determine incoming calls
		final Set<MethodOrMethodContext> inc = new HashSet<MethodOrMethodContext>();
		synchronized (incoming) {
			inc.addAll(incoming.get(d1));
		}
		
		//for each incoming call edge already processed
		//(see processCall(..))
//		V returnValue = 
		final Table<Unit, D, V> methodEnv = env.get(d1);
		final V valueAtReturn;
		synchronized(methodEnv) {
			valueAtReturn = methodEnv.contains(n, d2) ? methodEnv.get(n, d2) : valueLattice.topElement();
		}
		for (final MethodOrMethodContext incomingContext: inc) {
			if(!(incomingContext instanceof MethodContext)) {
				continue;
			}
			final Unit c = (Unit) incomingContext.context();
			//for each return site
			for(final Unit retSiteC: icfg.getReturnSitesOfCallAt(c)) {
				//compute return-flow function
				final FlowFunction<D> retFunction = flowFunctions.getReturnFlowFunction(c, methodThatNeedsSummary,n,retSiteC);
				flowFunctionConstructionCount++;
				final Set<D> targets = computeReturnFlowFunction(retFunction, null, d2, c, Collections.<D>emptySet());
				for(final D d5: targets) {
					//compute composed function
					final EdgeFunction<V> f5 = edgeFunctions.getReturnEdgeFunction(c, icfg.getMethodOf(n), n, d2, retSiteC, d5);
					propagate(incomingContext, retSiteC, d5, f5.computeTarget(valueAtReturn), c, false);
				}
			}
		}
	}
	
	/**
	 * Computes the return flow function for the given set of caller-side
	 * abstractions.
	 * @param retFunction The return flow function to compute
	 * @param d1 The abstraction at the beginning of the callee
	 * @param d2 The abstraction at the exit node in the callee
	 * @param callSite The call site
	 * @param callerSideDs The abstractions at the call site
	 * @return The set of caller-side abstractions at the return site
	 */
	protected Set<D> computeReturnFlowFunction
			(final FlowFunction<D> retFunction, final D d1, final D d2, final Unit callSite, final Set<D> callerSideDs) {
		return retFunction.computeTargets(d2);
	}

	/**
	 * Lines 33-37 of the algorithm.
	 * Simply propagate normal, intra-procedural flows.
	 * @param edge
	 */
	private void processNormalFlow(final ContextValueNode<MethodOrMethodContext, Unit,D> edge) {
		final MethodOrMethodContext context = edge.context();
		final Unit n = edge.getTarget(); 
		final D d2 = edge.factAtTarget();

		final V valueAtPoint;
		final Table<Unit, D, V> methodEnv = env.get(context);
		synchronized(methodEnv) {
			valueAtPoint = methodEnv.contains(n, d2) ? methodEnv.get(n, d2) : valueLattice.topElement();
		}
		
		for (final Unit m : icfg.getSuccsOf(n)) {
			final FlowFunction<D> flowFunction = flowFunctions.getNormalFlowFunction(n,m);
			flowFunctionConstructionCount++;
			final Set<D> res = flowFunction.computeTargets(d2);
			for (final D d3 : res) {
				final V fprime = edgeFunctions.getNormalEdgeFunction(n, d2, m, d3).computeTarget(valueAtPoint);
				propagate(context, m, d3, fprime, null, false); 
			}
		}
	}
	
	/**
	 * Computes the normal flow function for the given set of start and end
	 * abstractions-
	 * @param flowFunction The normal flow function to compute
	 * @param d1 The abstraction at the method's start node
	 * @param d1 The abstraction at the current node
	 * @return The set of abstractions at the successor node
	 */
	protected Set<D> computeNormalFlowFunction
			(final FlowFunction<D> flowFunction, final D d1, final D d2) {
		return flowFunction.computeTargets(d2);
	}

	/**
	 * Propagates the flow further down the exploded super graph, merging any edge function that might
	 * already have been computed for targetVal at target. 
	 * @param context the source value of the propagated summary edge
	 * @param target the target statement
	 * @param targetVal the target value at the target statement
	 * @param v the new edge function computed from (s0,sourceVal) to (target,targetVal) 
	 * @param relatedCallSite for call and return flows the related call statement, <code>null</code> otherwise
	 *        (this value is not used within this implementation but may be useful for subclasses of {@link IDESolver}) 
	 * @param isUnbalancedReturn <code>true</code> if this edge is propagating an unbalanced return
	 *        (this value is not used within this implementation but may be useful for subclasses of {@link IDESolver}) 
	 */
	protected boolean propagate(final MethodOrMethodContext context, final Unit target, final D targetVal, final V v,
		/* deliberately exposed to clients */ final Unit relatedCallSite,
		/* deliberately exposed to clients */ final boolean isUnbalancedReturn) {
		final V jumpFnE;
		final V fPrime;
		final Table<Unit, D, V> methodEnv;
		if(!env.containsKey(context)) {
			methodEnv = env.putIfAbsent(context, HashBasedTable.<Unit, D, V>create());
		} else {
			methodEnv = env.get(context);
		}
		final boolean isNew;
		synchronized(methodEnv) {
			if(!methodEnv.contains(target, targetVal)) {
				jumpFnE = valueLattice.topElement();
			} else {
				jumpFnE = methodEnv.get(target, targetVal);
			}
			fPrime = valueLattice.join(jumpFnE, v);
			isNew = !fPrime.equals(v);
			if(isNew) {
				if(fPrime.equals(valueLattice.topElement())) {
					methodEnv.remove(target, targetVal);
				} else {
					methodEnv.put(target, targetVal, fPrime);
				}
			}
		}
		
		if(isNew) {
			final ContextValueNode<MethodOrMethodContext, Unit, D> edge = new ContextValueNode<MethodOrMethodContext, Unit,D>(context, target, targetVal);
			scheduleEdgeProcessing(edge);
		}
		return isNew;
	}
		
	/**
	 * Factory method for this solver's thread-pool executor.
	 */
	protected CountingThreadPoolExecutor getExecutor() {
		return new CountingThreadPoolExecutor(1, this.numThreads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	}
	
	/**
	 * Returns a String used to identify the output of this solver in debug mode.
	 * Subclasses can overwrite this string to distinguish the output from different solvers.
	 */
	protected String getDebugName() {
		return "";
	}

	public void printStats() {
		if(logger.isDebugEnabled()) {
			if(ffCache!=null)
				ffCache.printStats();
			if(efCache!=null)
				efCache.printStats();
		} else {
			logger.info("No statistics were collected, as DEBUG is disabled.");
		}
	}
	
	private class PathEdgeProcessingTask implements Runnable {
		private final ContextValueNode<MethodOrMethodContext, Unit, D> edge;

		public PathEdgeProcessingTask(final ContextValueNode<MethodOrMethodContext, Unit, D> edge) {
			this.edge = edge;
		}

		@Override
		public void run() {
			if(icfg.isCallStmt(edge.getTarget())) {
				processCall(edge);
			} else {
				//note that some statements, such as "throw" may be
				//both an exit statement and a "normal" statement
				if(icfg.isExitStmt(edge.getTarget())) {
					processExit(edge);
				}
				if(!icfg.getSuccsOf(edge.getTarget()).isEmpty()) {
					processNormalFlow(edge);
				}
			}
		}
	}
}
