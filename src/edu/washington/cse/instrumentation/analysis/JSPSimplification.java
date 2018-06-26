package edu.washington.cse.instrumentation.analysis;

import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.PatchingChain;
import soot.Unit;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class JSPSimplification extends BodyTransformer {
	@Override
	protected void internalTransform(final Body b, final String phaseName, final Map<String, String> options) {
		if(!b.getMethod().getSubSignature().equals("void _jspService(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)")) {
			return;
		}
		final PatchingChain<Unit> units = b.getUnits();
		Unit u = units.getFirst();
		while(u != null) {
			final Stmt s = (Stmt) u;
			u = units.getSuccOf(u);
			if(s.containsInvokeExpr()) {
				final InvokeExpr ie = s.getInvokeExpr();
				if(ie.getMethodRef().getSignature().startsWith("<javax.servlet.jsp.JspWriter: void write") &&
						ie.getMethodRef().parameterTypes().size() == 1 && ie.getArg(0) instanceof Constant) {
					units.remove(s);
				}
			}
		}
	}

}
