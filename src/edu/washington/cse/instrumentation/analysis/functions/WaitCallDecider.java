package edu.washington.cse.instrumentation.analysis.functions;

import soot.Scene;
import soot.SootMethod;
import soot.util.NumberedString;

public final class WaitCallDecider {
	private final NumberedString waitSig;
	private final NumberedString waitMs;
	private final NumberedString waitNano;

	public WaitCallDecider() {
		waitSig	= Scene.v().getSubSigNumberer().findOrAdd("void wait()");
		waitMs = Scene.v().getSubSigNumberer().findOrAdd("void wait(long)");
		waitNano = Scene.v().getSubSigNumberer().findOrAdd("void wait(long,int)");
	}
	
	public final boolean isWaitCall(final SootMethod m) {
		final NumberedString ns = m.getNumberedSubSignature();
		return ns == waitSig || ns == waitMs || ns == waitNano;
	}
}