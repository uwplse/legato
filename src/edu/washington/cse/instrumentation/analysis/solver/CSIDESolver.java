package edu.washington.cse.instrumentation.analysis.solver;

import heros.EdgeFunction;
import heros.FlowFunction;
import heros.IDETabulationProblem;
import heros.InterproceduralCFG;
import heros.solver.IDESolver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import soot.util.HashMultiMap;
import soot.util.MultiMap;

import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class CSIDESolver<N, D, M, V, C, I extends InterproceduralCFG<N, M>> extends IDESolver<N, D, M, V, I> {
	protected ContextResolution<C, N, M> contextResolver;
	private final ConcurrentHashMap<C, Table<N, D, V>> contextValues = new ConcurrentHashMap<>();
	private final MultiMap<M, C> methodContexts = new HashMultiMap<>();

	public CSIDESolver(final IDETabulationProblem<N, D, M, V, I> tabulationProblem, final ContextResolution<C, N, M> contextResolver) {
		super(tabulationProblem);
		this.contextResolver = contextResolver;
	}
	
	@Override
	public void solve() {
		submitInitialSeeds();
		runExecutorAndAwaitCompletion();
		
		computeContextSensitiveValues();
		
		executor.shutdown();
		//similarly here: we await termination, but this should happen instantaneously,
		//as all tasks should have completed
		runExecutorAndAwaitCompletion();
	}
	
	protected void resetValueState() { }

	protected ContextResolution<C, N, M> tryWithNewContextStrategy() {
		return null;
	}
	
	private void computeContextSensitiveValues() {
		if(executor.isShutdown()) {
			return;
		}
		runPropagateToStart();
		ContextResolution<C, N, M> m = null;
		while((m = this.tryWithNewContextStrategy()) != null) {
			this.contextResolver = m;
			
			this.contextValues.clear();
			this.methodContexts.clear();
			
			this.resetValueState();
			runPropagateToStart();
		}
		
		//Phase II(ii)
		//we create an array of all nodes and then dispatch fractions of this array to multiple threads
		final Set<N> allNonCallStartNodes = icfg.allNonCallStartNodes();
		@SuppressWarnings("unchecked")
		final
		N[] nonCallStartNodesArray = (N[]) new Object[allNonCallStartNodes.size()];
		int i=0;
		for (final N n : allNonCallStartNodes) {
			nonCallStartNodesArray[i] = n;
			i++;
		}
		//No need to keep track of the number of tasks scheduled here, since we call shutdown
		for(int t=0;t<numThreads; t++) {
			final ValueComputationTask task = new ValueComputationTask(nonCallStartNodesArray, t);
			scheduleValueComputationTask(task);
		}
		//await termination of tasks
		try {
			executor.awaitCompletion();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}

	}

	private void runPropagateToStart() {
		//Phase II(i)
    logger.debug("Computing the final values for the edge functions");
    //add caller seeds to initial seeds in an unbalanced problem
    final Map<N, Set<D>> allSeeds = new HashMap<N, Set<D>>(initialSeeds);
    for(final N unbalancedRetSite: unbalancedRetSites) {
    	Set<D> seeds = allSeeds.get(unbalancedRetSite);
    	if(seeds==null) {
    		seeds = new HashSet<D>();
    		allSeeds.put(unbalancedRetSite, seeds);
    	}
    	seeds.add(zeroValue);
    }
		//do processing
    final C initialContext = contextResolver.initialContext();
		for(final Entry<N, Set<D>> seed: allSeeds.entrySet()) {
			final N startPoint = seed.getKey();
			for(final D val: seed.getValue()) {
				registerContext(icfg.getMethodOf(startPoint), initialContext);
				contextValues.put(initialContext, HashBasedTable.<N, D, V>create());
				setVal(initialContext, startPoint, val, valueLattice.bottomElement());
				final ContextValueNode<C, N, D> superGraphNode = new ContextValueNode<>(initialContext, startPoint, val); 
				scheduleValueProcessing(new ValuePropagationTask(superGraphNode));
			}
		}
		logger.debug("Computed the final values of the edge functions");
		//await termination of tasks
		try {
			executor.awaitCompletion();
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected void setVal(final C context, final N unit, final D fact, final V val) {
		Table<N, D, V> m;
		if(!contextValues.containsKey(context)) {
			contextValues.putIfAbsent(context, HashBasedTable.<N, D, V>create());
			m = contextValues.get(context);
		} else {
			m = contextValues.get(context);
		}
		synchronized(m) {
			if(val == valueLattice.topElement()) {
				m.remove(unit, fact);
			} else {
				m.put(unit, fact, val);
			}
		}
	}
	
	protected V val(final C context, final N n, final D d) {
		if(!contextValues.containsKey(context)) {
			return valueLattice.topElement();
		}
		final Table<N, D, V> m = contextValues.get(context);
		V l;
		synchronized(m) {
			l = m.get(n, d);
		}
		if(l == null) { return valueLattice.topElement(); }
		else { return l; }
	}

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
	
	private class ValuePropagationTask implements Runnable {
		private final ContextValueNode<C, N, D> nAndD;

		public ValuePropagationTask(final ContextValueNode<C, N, D> superGraphNode) {
			this.nAndD = superGraphNode;
		}

		@Override
		public void run() {
			final N n = nAndD.getTarget();
			if(icfg.isStartPoint(n) ||
				initialSeeds.containsKey(n) ||			//our initial seeds are not necessarily method-start points but here they should be treated as such
				unbalancedRetSites.contains(n)) { 		//the same also for unbalanced return sites in an unbalanced problem
				propagateValueAtStart(nAndD, n);
			}
			if(icfg.isCallStmt(n)) {
				propagateValueAtCall(nAndD, n);
			}
		}
	}

	private void propagateValueAtStart(final ContextValueNode<C, N, D> nAndD, final N n) {
		final D d = nAndD.factAtTarget();		
		final M p = icfg.getMethodOf(n);
		final C context = nAndD.context();
		for(final N c: icfg.getCallsFromWithin(p)) {					
			Set<Entry<D, EdgeFunction<V>>> entries; 
			synchronized (jumpFn) {
				entries = jumpFn.forwardLookup(d,c).entrySet();
				for(final Map.Entry<D,EdgeFunction<V>> dPAndFP: entries) {
					final D dPrime = dPAndFP.getKey();
					final EdgeFunction<V> fPrime = dPAndFP.getValue();
					final N sP = n;

					propagateValue(context, c,dPrime,fPrime.computeTarget(val(context, sP,d)));
					flowFunctionApplicationCount++;
				}
			}
		}

	}

	private void propagateValueAtCall(final ContextValueNode<C, N, D> nAndD, final N n) {
		final D d = nAndD.factAtTarget();
		final C inputContext = nAndD.context();
		for(final M q: icfg.getCalleesOfCallAt(n)) {
			final C calleeContext = contextResolver.extendContext(inputContext, n, q);
			final FlowFunction<D> callFlowFunction = flowFunctions.getCallFlowFunction(n, q);
			flowFunctionConstructionCount++;
			for(final D dPrime: callFlowFunction.computeTargets(d)) {
				registerContext(q, calleeContext);
				final EdgeFunction<V> edgeFn = edgeFunctions.getCallEdgeFunction(n, d, q, dPrime);
				for(final N startPoint: icfg.getStartPointsOf(q)) {
					propagateValue(calleeContext, startPoint,dPrime, edgeFn.computeTarget(val(inputContext,n,d)));
					flowFunctionApplicationCount++;
				}
			}
		}
	}
	
	
	private void registerContext(final M q, final C calleeContext) {
		synchronized(methodContexts) {
			methodContexts.put(q, calleeContext);
		}
	}

	private void propagateValue(final C calleeContext, final N nHashN, final D nHashD,
			final V v) {
		final Table<N, D, V> val;
		if(!contextValues.containsKey(calleeContext)) {
			contextValues.putIfAbsent(calleeContext, HashBasedTable.<N, D, V>create());
			val = contextValues.get(calleeContext);
		} else {
			val = contextValues.get(calleeContext);
		}
		synchronized (val) {
			final V valNHash = val(calleeContext, nHashN, nHashD);
			final V vPrime = joinValueAt(calleeContext, nHashN, nHashD, valNHash,v);
			if(!vPrime.equals(valNHash)) {
				setVal(calleeContext, nHashN, nHashD, vPrime);
				scheduleValueProcessing(new ValuePropagationTask(new ContextValueNode<>(calleeContext, nHashN, nHashD)));
			}
		}
	}

	protected V joinValueAt(final C context, final N unit, final D fact, final V curr, final V newVal) {
		return valueLattice.join(curr, newVal);
	}
	
	@Override
	protected final V joinValueAt(final N unit, final D fact, final V curr, final V newVal) {
		throw new RuntimeException();
	}

	private class ValueComputationTask implements Runnable {
		private final N[] values;
		final int num;

		public ValueComputationTask(final N[] values, final int num) {
			this.values = values;
			this.num = num;
		}

		@Override
		public void run() {
			final int sectionSize = (int) Math.floor(values.length / numThreads) + numThreads;
			for(int i = sectionSize * num; i < Math.min(sectionSize * (num+1),values.length); i++) {
				final N n = values[i];
				final M containingMethod = icfg.getMethodOf(n);
				Set<C> contexts;
				synchronized(methodContexts) {
					contexts = methodContexts.get(containingMethod);
				}
				for(final N sP: icfg.getStartPointsOf(containingMethod)) {					
					Set<Cell<D, D, EdgeFunction<V>>> lookupByTarget;
					lookupByTarget = jumpFn.lookupByTarget(n);
					for(final Cell<D, D, EdgeFunction<V>> sourceValTargetValAndFunction : lookupByTarget) {
						final D dPrime = sourceValTargetValAndFunction.getRowKey();
						final D d = sourceValTargetValAndFunction.getColumnKey();
						final EdgeFunction<V> fPrime = sourceValTargetValAndFunction.getValue();
						for(final C cont : contexts) {
							final Table<N, D, V> val = contextValues.get(cont);
							synchronized (val) {
								final V joined = joinValueAt(cont, n, d, val(cont, n, d), fPrime.computeTarget(val(cont, sP, dPrime)));
								setVal(cont, n,d,joined);
							}
						}
						flowFunctionApplicationCount++;
					}
				}
			}
		}
	}
	
	@Override
	protected final V val(final N nHashN, final D nHashD) {
		throw new RuntimeException();
	}
	
	@Override
	public V resultAt(final N stmt, final D value) {
		V accum = valueLattice.topElement();
		final M m = icfg.getMethodOf(stmt);
		if(!methodContexts.containsKey(m)) {
			return accum;
		}
		for(final C context : methodContexts.get(m)) {
			if(!contextValues.containsKey(context)) {
				continue;
			}
			final Table<N, D, V> vals = contextValues.get(context);
			if(!vals.contains(stmt, value)) {
				continue;
			} else {
				accum = valueLattice.join(accum, vals.get(stmt, value));
			}
		}
		return accum;
	}
	
	@Override
	public Map<D, V> resultsAt(final N stmt) {
		final M m = icfg.getMethodOf(stmt);
		if(!methodContexts.containsKey(m)) {
			return new HashMap<>();
		}
		final Map<D, V> accum = new HashMap<>();
		for(final C context : methodContexts.get(m)) {
			if(!contextValues.containsKey(context)) {
				continue;
			}
			final Table<N, D, V> vals = contextValues.get(context);
			if(!vals.containsRow(stmt)) {
				continue;
			} else {
				final Map<D, V> row = Maps.filterKeys(vals.row(stmt), new Predicate<D>() {
					@Override
					public boolean apply(final D val) {
						return val!=zeroValue;
					}
				});
				for(final Map.Entry<D, V> valsAt : row.entrySet()) {
					if(!accum.containsKey(valsAt.getKey())) {
						accum.put(valsAt.getKey(), valsAt.getValue());
					} else {
						accum.put(valsAt.getKey(), valueLattice.join(accum.get(valsAt.getKey()), valsAt.getValue()));
					}
				}
			}
		}
		return accum;
	}
	
	public Set<C> getContextsOfMethod(final M m) {
		return this.methodContexts.get(m);
	}
	
	public V resultsAt(final C context, final N site, final D fact) {
		if(!contextValues.containsKey(context)) {
			return null;
		}
		return contextValues.get(context).get(site, fact);
	}
	
	@Override
	protected final void setVal(final N nHashN, final D nHashD, final V l) {
		throw new RuntimeException();
	}

	private void scheduleValueProcessing(final ValuePropagationTask vpt) {
		if(executor.isTerminating())
			return;
		executor.execute(vpt);
	}

	private void scheduleValueComputationTask(final ValueComputationTask task) {
		if(executor.isTerminating())
			return;
		executor.execute(task);
	}

}
