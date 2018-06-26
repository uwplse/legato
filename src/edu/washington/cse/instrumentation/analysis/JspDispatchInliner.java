package edu.washington.cse.instrumentation.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import soot.Body;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.toolkits.callgraph.reflection.AbstractReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;

public final class JspDispatchInliner extends AbstractReflectionHandler {
	private int forwardCounter = 0;

	@Override
	public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
		m.unfreeze();
		final Body body = m.getActiveBody();
		final PatchingChain<Unit> unitChain = body.getUnits();
		final Iterator<Unit> it = unitChain.snapshotIterator();
		final LocalGenerator lg = new LocalGenerator(body);
		final SootClass simpleContextClass = Scene.v().getSootClass("edu.washington.cse.servlet.jsp.SimplePageContext");
		final Jimple j = Jimple.v();
		while(it.hasNext()) {
			final Unit u = it.next();
			final Stmt s = (Stmt) u;
			if(!s.containsInvokeExpr()) {
				continue;
			}
			if(!(s.getInvokeExpr() instanceof VirtualInvokeExpr)) {
				continue;
			}
			final VirtualInvokeExpr iie = (VirtualInvokeExpr) s.getInvokeExpr();
			final String sig = iie.getMethodRef().getSignature();
			if(!sig.equals("<javax.servlet.jsp.PageContext: void include(java.lang.String)>") &&
				 !sig.equals("<javax.servlet.jsp.PageContext: void forward(java.lang.String)>") &&
				 !sig.equals("<javax.servlet.jsp.PageContext: void forward(java.lang.String,bool)>")) {
				continue;
			}
			final Value v = iie.getArg(0);
			if(!(v instanceof StringConstant)) {
				continue;
			}
			final String url = ((StringConstant) v).value;
			SootMethodRef generatedRef;
			{
				final SootMethod syntheticMethod = new SootMethod("legatoInline" + (forwardCounter++), Collections.<Type>emptyList(), VoidType.v(), Modifier.PUBLIC,
						Arrays.asList(Scene.v().getSootClass("javax.servlet.ServletException"), Scene.v().getSootClass("java.io.IOException")));
				final Body genB = j.newBody(syntheticMethod);
				syntheticMethod.setActiveBody(genB);
				final LocalGenerator g = new LocalGenerator(genB);
				
				final Local thisLocal = g.generateLocal(simpleContextClass.getType());
				final Local dispatchLocal = g.generateLocal(Scene.v().getRefType("javax.servlet.RequestDispatcher"));
				final Local reqLocal = g.generateLocal(Scene.v().getRefType("javax.servlet.http.HttpServletRequest"));
				final Local respLocal = g.generateLocal(Scene.v().getRefType("javax.servlet.http.HttpServletResponse"));
				
				final PatchingChain<Unit> genUnits = genB.getUnits();
				genUnits.add(j.newIdentityStmt(thisLocal, j.newThisRef(simpleContextClass.getType())));
				genUnits.add(j.newAssignStmt(reqLocal, j.newInstanceFieldRef(thisLocal, Scene.v().makeFieldRef(simpleContextClass, "request", reqLocal.getType(), false))));
				genUnits.add(j.newAssignStmt(respLocal, j.newInstanceFieldRef(thisLocal, Scene.v().makeFieldRef(simpleContextClass, "response", respLocal.getType(), false))));
				
				genUnits.add(j.newAssignStmt(dispatchLocal,
					j.newInterfaceInvokeExpr(reqLocal,
						Scene.v().makeMethodRef(Scene.v().getSootClass("javax.servlet.ServletRequest"), "getRequestDispatcher", Arrays.<Type>asList(RefType.v("java.lang.String")),
								dispatchLocal.getType(), false), StringConstant.v(url))));
				genUnits.add(j.newInvokeStmt(j.newInterfaceInvokeExpr(dispatchLocal,
						Scene.v().makeMethodRef(((RefType)dispatchLocal.getType()).getSootClass(), iie.getMethodRef().name(), 
							Arrays.<Type>asList(Scene.v().getRefType("javax.servlet.ServletRequest"), Scene.v().getRefType("javax.servlet.ServletResponse")), VoidType.v(), false),
							reqLocal, respLocal)));
				genUnits.add(j.newReturnVoidStmt());
				genB.validate();
				genUnits.addFirst(j.newNopStmt());
				simpleContextClass.addMethod(syntheticMethod);
				generatedRef = syntheticMethod.makeRef();
			}
			final Local downCastLocal = lg.generateLocal(simpleContextClass.getType());
			unitChain.insertAfter(j.newInvokeStmt(j.newVirtualInvokeExpr(downCastLocal, generatedRef)), u);
			unitChain.swapWith(u, j.newAssignStmt(downCastLocal, j.newCastExpr(iie.getBase(), simpleContextClass.getType())));
		}
	}
}