package edu.washington.cse.instrumentation.analysis.functions;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.util.NumberedString;
import boomerang.BoomerangContext;
import boomerang.mock.ReflectiveCallHandler;

public abstract class AbstractReflectionHandler implements ReflectiveCallHandler {
	protected final BoomerangContext context;
	protected final ReflectionDecider reflectionDecider;
	
	public AbstractReflectionHandler(final BoomerangContext context, final ReflectionDecider rd) {
		this.context = context;
		this.reflectionDecider = rd;
	}

	@Override
	public boolean handles(final Unit callSite, final SootMethod destMethod) {
		final NumberedString destinationSig = destMethod.getNumberedSubSignature();
		final NumberedString formalSubSig = ((Stmt)callSite).getInvokeExpr().getMethod().getNumberedSubSignature();
		final InvokeExpr ie = ((Stmt)callSite).getInvokeExpr();
		
		return reflectionDecider.isConstructorNewInstance(formalSubSig) || reflectionDecider.isExecutorExecute(formalSubSig, formalSubSig) ||
				reflectionDecider.isNewInstance(formalSubSig) || reflectionDecider.isMethodInvoke(formalSubSig) || reflectionDecider.isPrivilegedActionCall(ie, destinationSig);
	}
}
