package edu.washington.cse.instrumentation.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import soot.IntType;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.UnitBox;
import soot.VoidType;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.internal.StmtBox;

public class HttpServletTransformer {
	private static final String[] dispatchMethods = new String[]{
		"doPost",
		"doGet",
		"doHead",
		"doDelete",
		"doTrace",
		"doOptions",
		"doPut"
	};
	
	public static boolean dispatchesToDefault(final SootClass cls, final String subSig) {
		SootClass it = cls;
		while(!it.getName().equals("javax.servlet.http.HttpServlet")) {
			if(it.declaresMethod(subSig)) {
				return false;
			}
			if(it.getSuperclass() == null) {
				throw new NullPointerException("Invariant broken: " + it + " " + cls + " " + subSig);
			}
			it = it.getSuperclass();
		}
		return true;
	}
	
	public static void synthesizeBody(final SootClass cls) {
		if(cls.getName().equals("org.apache.jasper.runtime.HttpJspBase")) {
			return;
		}
		if(!cls.isConcrete()) {
			return;
		}
		if(dispatchesToDefault(cls, "void service(javax.servlet.ServletRequest,javax.servlet.ServletResponse)")) {
			JasperTransformer.addServiceAdapter(cls, "service");
		}
		final RefType thisType = cls.getType();
		final RefType requestType = Scene.v().getRefType("javax.servlet.http.HttpServletRequest");
		final RefType responseType = Scene.v().getRefType("javax.servlet.http.HttpServletResponse");
		
		final List<Type> requestTypes = Arrays.<Type>asList(requestType, responseType);
		final List<SootClass> exceptionList = Arrays.asList(Scene.v().getSootClass("javax.servlet.ServletException"), Scene.v().getSootClass("java.io.IOException"));
		if(dispatchesToDefault(cls, "void service(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)")) {
			final Jimple jimple = Jimple.v();
			final SootMethod serviceMethod = new SootMethod("service", requestTypes,
					VoidType.v(), Modifier.PUBLIC, 
					exceptionList);
			final JimpleBody jb = jimple.newBody(serviceMethod);
			final PatchingChain<Unit> units = jb.getUnits();
			final Local thisLocal = jimple.newLocal("this", thisType);
			final Local reqLocal = jimple.newLocal("req", requestType);
			final Local respLocal = jimple.newLocal("resp", requestType);
			jb.getLocals().addAll(Arrays.asList(thisLocal, reqLocal, respLocal));
			
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			units.add(jimple.newIdentityStmt(reqLocal, jimple.newParameterRef(requestType, 0)));
			units.add(jimple.newIdentityStmt(respLocal, jimple.newParameterRef(responseType, 1)));

			final Local dispatchLocal = jimple.newLocal("disp", IntType.v());
			jb.getLocals().add(dispatchLocal);
			
			units.add(jimple.newAssignStmt(dispatchLocal, jimple.newStaticInvokeExpr(
				Scene.v().makeMethodRef(Scene.v().getSootClass("edu.washington.cse.servlet.Util"), "nondetInt", Collections.<Type>emptyList(), IntType.v(), true)
			)));
			UnitBox accum = null;
			int it = 0;
			final Unit endUnit = jimple.newReturnVoidStmt();
			for(final String dispMethod : dispatchMethods) {
				final UnitBox nextAccum = new StmtBox(null);
				final Unit u = jimple.newIfStmt(jimple.newNeExpr(dispatchLocal, IntConstant.v(it++)), nextAccum);
				if(accum != null) {
					accum.setUnit(u);
				}
				accum = nextAccum;
				units.add(u);
				units.add(jimple.newInvokeStmt(jimple.newVirtualInvokeExpr(thisLocal, 
							Scene.v().makeMethodRef(cls, dispMethod, requestTypes, VoidType.v(), false), 
						reqLocal, respLocal)));
				units.add(jimple.newReturnVoidStmt());
			}
			if(accum != null) {
				accum.setUnit(endUnit);
			}
			units.add(endUnit);
			serviceMethod.setActiveBody(jb);
			cls.addMethod(serviceMethod);
		}
		for(final String method : dispatchMethods) {
			if(!dispatchesToDefault(cls, "void " + method + "(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)")) {
				continue;
			}
			final SootMethod m = new SootMethod(method, requestTypes, VoidType.v(), Modifier.PROTECTED, exceptionList);
			final Jimple jimple = Jimple.v();
			final JimpleBody b = jimple.newBody(m);
			
			final PatchingChain<Unit> units = b.getUnits();
			final Local thisLocal = jimple.newLocal("this", thisType);
			final Local reqLocal = jimple.newLocal("req", thisType);
			final Local respLocal = jimple.newLocal("resp", thisType);
			b.getLocals().addAll(Arrays.asList(thisLocal, reqLocal, respLocal));
			
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			units.add(jimple.newIdentityStmt(reqLocal, jimple.newParameterRef(requestType, 0)));
			units.add(jimple.newIdentityStmt(respLocal, jimple.newParameterRef(responseType, 1)));
			
			if(method.equals("doHead")) {
				jimple.newInvokeStmt(jimple.newVirtualInvokeExpr(thisLocal, Scene.v().makeMethodRef(cls, "doGet", requestTypes, VoidType.v(), false), reqLocal, respLocal));
			}
			units.add(jimple.newReturnVoidStmt());
			m.setActiveBody(b);
			cls.addMethod(m);
		}
	}
}
