package edu.washington.cse.instrumentation.analysis;

import soot.SootMethod;
import soot.Unit;
import boomerang.accessgraph.AccessGraph;
import edu.washington.cse.instrumentation.analysis.solver.SolverAwareFlowFunctions;

public class NullAnalysisModelExtension extends AbstractAnalysisModelExtension {
	@Override
	public SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> extendFunctions(final SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> in) {
		return in;
	}
}
