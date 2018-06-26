package edu.washington.cse.instrumentation.analysis;

public interface AnalysisCompleteListener {
	void analysisCompleted(InconsistentReadSolver solver, AtMostOnceProblem problem);
}
