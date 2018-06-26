package edu.washington.cse.instrumentation.analysis.utils;

import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.Unit;
import soot.jimple.Stmt;

public class MethodResolver extends BodyTransformer {
	@Override
	protected void internalTransform(final Body b, final String phaseName, final Map<String, String> options) {
		for(final Unit u : b.getUnits()) {
			if(!(u instanceof Stmt)) {
				continue;
			}
			final Stmt s = (Stmt) u;
			if(s.containsInvokeExpr()) {
				s.getInvokeExpr().getMethodRef().resolve(); // force resolution
			}
		}
	}
}