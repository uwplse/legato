package edu.washington.cse.instrumentation.analysis.solver;

import heros.FlowFunctions;

public interface SolverAwareFlowFunctions<N, D, M, S> extends FlowFunctions<N, D, M> {
	public void setSolver(S solver);
}
