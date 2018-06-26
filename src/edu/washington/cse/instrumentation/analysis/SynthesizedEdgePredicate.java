package edu.washington.cse.instrumentation.analysis;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

public class SynthesizedEdgePredicate implements EdgePredicate {
	private final SootMethod dummyClinitMethod;
	public SynthesizedEdgePredicate() {
		final SootClass cls = Scene.v().getMainClass();
		SootMethod found = null;
		for(final SootMethod m : cls.getMethods()) {
			if(m.getSubSignature().equals("void legato_dummy_clinit()")) {
				found = m;
				break;
			}
		}
		this.dummyClinitMethod = found;
	}
	
	@Override
	public boolean want(final Edge e) {
		if(!e.getTgt().method().isStaticInitializer()) {
			return true;
		}
		if(e.srcStmt() == null || !e.srcStmt().containsInvokeExpr()) {
			return false;
		}
		return e.srcStmt().getInvokeExpr().getMethod() == dummyClinitMethod;
	}
}