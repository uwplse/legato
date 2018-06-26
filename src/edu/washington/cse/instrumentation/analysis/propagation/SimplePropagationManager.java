package edu.washington.cse.instrumentation.analysis.propagation;

import soot.SootMethod;
import soot.Type;
import soot.Unit;

public class SimplePropagationManager implements PropagationManager {
	@Override
	public boolean isPropagationMethod(final SootMethod m) {
		return false;
	}
	
	@Override
	public PropagationSpec getPropagationSpec(final Unit callSite) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isIdentityMethod(final SootMethod sm) {
		return false;
	}
	
	@Override
	public void initialize() {}

	@Override
	public boolean isContainerType(final Type t) {
		return false;
	}
}
