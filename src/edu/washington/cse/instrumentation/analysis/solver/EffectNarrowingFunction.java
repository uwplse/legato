package edu.washington.cse.instrumentation.analysis.solver;

import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction.EdgeFunctionEffect;

public interface EffectNarrowingFunction {
	public EdgeFunctionEffect narrowEffect(EdgeFunctionEffect e);
}
