package edu.washington.cse.instrumentation.analysis.solver;

import java.util.Map;
import java.util.Set;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.FlowFunctions;
import heros.IDETabulationProblem;
import heros.InterproceduralCFG;
import heros.JoinLattice;

public abstract class DefaultLegatoJimpleIDETabulationProblem<N, D, M, V, I extends InterproceduralCFG<N, M>>
	implements IDETabulationProblem<N, D, M, V, I> {
	
	private final I icfg;
	private final D zeroValue;
	private final EdgeFunction<V> allTop;
	private final JoinLattice<V> joinLattice;

	public DefaultLegatoJimpleIDETabulationProblem(final I icfg) {
		this.icfg = icfg;
		
		this.zeroValue = createZeroValue();
		this.allTop = createAllTopFunction();
		this.joinLattice = createJoinLattice();
	}
	
	protected abstract D createZeroValue();
	protected abstract EdgeFunction<V> createAllTopFunction();
	protected abstract JoinLattice<V> createJoinLattice();

	@Override
	abstract public FlowFunctions<N, D, M> flowFunctions();
	
	@Override
	abstract public EdgeFunctions<N, D, M, V> edgeFunctions();


	@Override
	public I interproceduralCFG() {
		return icfg;
	}

	@Override
	public abstract Map<N, Set<D>> initialSeeds();

	@Override
	public D zeroValue() {
		return zeroValue;
	}

	@Override
	public boolean followReturnsPastSeeds() {
		return false;
	}

	@Override
	public boolean autoAddZero() {
		return true;
	}

	@Override
	public int numThreads() {
		return 1;
	}

	@Override
	public boolean computeValues() {
		return true;
	}

	@Override
	public boolean recordEdges() {
		return false;
	}


	@Override
	public JoinLattice<V> joinLattice() {
		return joinLattice;
	}

	@Override
	public EdgeFunction<V> allTopFunction() {
		return allTop;
	}
}
