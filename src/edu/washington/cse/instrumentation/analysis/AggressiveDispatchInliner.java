package edu.washington.cse.instrumentation.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.IntType;
import soot.Local;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.internal.StmtBox;
import soot.jimple.toolkits.callgraph.reflection.AbstractReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;
import soot.util.NumberedString;
import edu.washington.cse.instrumentation.analysis.utils.JspUrlRouter;

public class AggressiveDispatchInliner extends AbstractReflectionHandler {
	private final JspUrlRouter router;
	private final NumberedString forwardSig;
	private final NumberedString includeSig;
	private final Map<Local, Set<String>> deferred;

	public AggressiveDispatchInliner(final JspUrlRouter router, final Map<Local, Set<String>> deferredResolution) {
		this.router = router;
		this.deferred = deferredResolution;
		forwardSig = Scene.v().getSubSigNumberer().findOrAdd("void forward(javax.servlet.ServletRequest,javax.servlet.ServletResponse)");
		includeSig = Scene.v().getSubSigNumberer().findOrAdd("void include(javax.servlet.ServletRequest,javax.servlet.ServletResponse)");
	}
	
	@Override
	public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
		final PatchingChain<Unit> units = m.getActiveBody().getUnits();
		boolean unrolled = false;
		for(final Iterator<Unit> uIt = units.snapshotIterator(); uIt.hasNext(); ) {
			final Unit u = uIt.next();
			final Stmt s = (Stmt) u;
			if(!s.containsInvokeExpr()) {
				continue;
			}
			final InvokeExpr ie = s.getInvokeExpr();
			if(!(ie instanceof InstanceInvokeExpr)) {
				continue;
			}
			final InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
			final SootMethodRef mRef = iie.getMethodRef();
			final NumberedString subSig = mRef.getSubSignature();
			if(subSig != forwardSig && subSig != includeSig) {
				continue;
			}
			final Local base = (Local) iie.getBase();
			if(!base.getType().toString().equals("javax.servlet.RequestDispatcher")) {
				continue;
			}
			// hold onto your butts
			final Collection<String> dispatchees = deferred.containsKey(base) ? deferred.get(base) : router.getDispatchClasses();
			doUnroll(units, m.getActiveBody(), u, dispatchees);
			unrolled = true;
		}
		if(unrolled && !AnalysisConfiguration.VERY_QUIET) {
			System.out.println("Unrolled indirection in: " + m);
		}
	}
	
	public static void doUnroll(final PatchingChain<Unit> units, final Body b, final Unit sourceCall, final Collection<String> dispatchees) {
		int it = 0;
		
		final Jimple jimple = Jimple.v();
		final LocalGenerator lg = new LocalGenerator(b);
		
		final SootMethodRef mRef;
		final Local base;
		final List<Value> args;
		{
			final Stmt s = (Stmt) sourceCall;
			final InvokeExpr ie = s.getInvokeExpr();
			final InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
			mRef = iie.getMethodRef();
			base = (Local) iie.getBase();
			args = iie.getArgs();
		}
		
		final Local dispatchLocal = lg.generateLocal(IntType.v());
		
		final Unit currUnit = jimple.newAssignStmt(dispatchLocal, 
				jimple.newStaticInvokeExpr(
						Scene.v().makeMethodRef(Scene.v().getSootClass("edu.washington.cse.servlet.Util"), "nondetInt", Collections.<Type>emptyList(), IntType.v(), true)));
		currUnit.addAllTagsOf(sourceCall);
		units.swapWith(sourceCall, currUnit);
		Unit itPoint = currUnit;
		final Unit endUnit = jimple.newNopStmt();
		endUnit.addAllTagsOf(sourceCall);
		UnitBox accum = null;
		for(final String dispClass : dispatchees) {
			final int currIt = it++;
			final UnitBox nextAccum = new StmtBox(null);
			final Unit checkUnit = jimple.newIfStmt(jimple.newNeExpr(dispatchLocal, IntConstant.v(currIt)), nextAccum);
			checkUnit.addAllTagsOf(sourceCall);
			if(accum != null) {
				accum.setUnit(checkUnit);
			}
			accum = nextAccum;
			units.insertAfter(checkUnit, itPoint);
			final Local l = lg.generateLocal(RefType.v(dispClass));
			final AssignStmt castUnit = jimple.newAssignStmt(l, jimple.newCastExpr(base, RefType.v(dispClass)));
			castUnit.addAllTagsOf(sourceCall);
			units.insertAfter(castUnit, checkUnit);
			final SootMethodRef nextRef = Scene.v().makeMethodRef(Scene.v().getSootClass(dispClass), mRef.name(), mRef.parameterTypes(), mRef.returnType(), mRef.isStatic());
			final Stmt callUnit = (Stmt) sourceCall.clone();
			callUnit.getInvokeExprBox().setValue(jimple.newVirtualInvokeExpr(l, nextRef, args));
			units.insertAfter(callUnit, castUnit);
			units.insertAfter(itPoint = jimple.newGotoStmt(endUnit), callUnit);
			itPoint.addAllTagsOf(sourceCall);
		}
		units.insertAfter(endUnit, itPoint);
		if(accum != null) {
			accum.setUnit(endUnit);
		}
	}
}
