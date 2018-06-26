package edu.washington.cse.instrumentation.analysis;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.Modifier;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class JasperTransformer extends BodyTransformer {
	private static final String GET_TAG_POOL_SIG = 
			"<org.apache.jasper.runtime.TagHandlerPool: org.apache.jasper.runtime.TagHandlerPool getTagHandlerPool(javax.servlet.ServletConfig)>";
	private static final String RELEASE_TAG_SIG = "<org.apache.jasper.runtime.TagHandlerPool: void release()>";
	private static final String REUSE_TAG_SIG = "<org.apache.jasper.runtime.TagHandlerPool: void reuse(javax.servlet.jsp.tagext.Tag)>";
	private static final String GET_TAG_SIG = "<org.apache.jasper.runtime.TagHandlerPool: javax.servlet.jsp.tagext.Tag get(java.lang.Class)>";

	@Override
	protected void internalTransform(final Body b, final String phaseName, final Map<String, String> options) {
		final PatchingChain<Unit> units = b.getUnits();
		final UnitGraph ug = new BriefUnitGraph(b);
		for(final Iterator<Unit> it = units.snapshotIterator(); it.hasNext(); ) {
			final Unit currUnit = it.next();
			final Stmt stmt = (Stmt) currUnit;
			if(!(currUnit instanceof AssignStmt)) {
				if(!stmt.containsInvokeExpr()) {
					continue;
				}
				final InvokeExpr ie = stmt.getInvokeExpr();
				final String calledSig = ie.getMethod().getSignature();
				if(calledSig.equals(GET_TAG_SIG)) {
					throw new RuntimeException("Could not adapt: " + b.getMethod());
				}
				if(calledSig.equals(REUSE_TAG_SIG) || 
						calledSig.equals(RELEASE_TAG_SIG)) {
					units.insertAfter(Jimple.v().newNopStmt(), currUnit);
//					b.validate();
				}
				continue;
			}
			final AssignStmt invokeStmt = (AssignStmt) currUnit;
			if(!invokeStmt.containsInvokeExpr()) {
				continue;
			}
			final Value lhs = invokeStmt.getLeftOp(); 
			final SootMethod m = invokeStmt.getInvokeExpr().getMethod();
			if(!m.getSignature().equals(GET_TAG_SIG) && !m.getSignature().equals(GET_TAG_POOL_SIG)) {
				continue;
			}
			if(m.getSignature().equals(GET_TAG_POOL_SIG)) {
				invokeStmt.setRightOp(NullConstant.v());
				continue;
			}
			final List<Unit> succs = ug.getSuccsOf(currUnit);
			if(succs.size() != 1) {
				throw new RuntimeException("Could not adapt: " + currUnit + " in " + b.getMethod());
			}
			final Unit nextUnit = succs.get(0);
			if(!(nextUnit instanceof AssignStmt) || !(((AssignStmt)nextUnit).getRightOp() instanceof CastExpr)) {
				throw new RuntimeException("Could not adapt: " + currUnit + " in " + b.getMethod());
			}
			final CastExpr castExpr = (CastExpr) ((AssignStmt)nextUnit).getRightOp();
			if(castExpr.getOp() != lhs) {
				throw new RuntimeException("Could not adapt: " + currUnit + " in " + b.getMethod());
			}
			final Type castedType = castExpr.getCastType();
			if(!(castedType instanceof RefType)) {
				throw new RuntimeException("Could not adapt: " + currUnit + " in " + b.getMethod());
			}
			invokeStmt.getRightOpBox().setValue(Jimple.v().newNewExpr((RefType)castedType));
			final SootClass created = ((RefType)castedType).getSootClass();
			final Unit invokeConstructor = Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr((Local)lhs, 
					Scene.v().makeMethodRef(created, "<init>", Collections.<Type>emptyList(), VoidType.v(), false)));
			units.insertAfter(invokeConstructor, currUnit);
//			b.validate();
		}
	}

	public static void synthesizeBodies(final SootClass declaringClass) {
		assert declaringClass.getSuperclass().getName().equals("org.apache.jasper.runtime.HttpJspBase") : declaringClass;
		// inline jsp implementations
		final RefType servletConfigType = RefType.v("javax.servlet.ServletConfig");
		final SootClass servletExceptionClass = Scene.v().getSootClass("javax.servlet.ServletException");
		
		final SootField configField = new SootField("_config", servletConfigType, Modifier.PRIVATE);
		declaringClass.addField(configField);
		final SootFieldRef configRef = configField.makeRef();
		
		final RefType thisType = RefType.v(declaringClass.getName());
		final Jimple jimple = Jimple.v();
		{
			final SootMethod initMethod = new SootMethod("init", Collections.<Type>singletonList(servletConfigType),
					VoidType.v(), Modifier.PUBLIC, Collections.<SootClass>singletonList(servletExceptionClass));
			final JimpleBody jb = jimple.newBody(initMethod);
			final PatchingChain<Unit> units = jb.getUnits();
			final Local thisLocal = jimple.newLocal("this", thisType);
			final Local configLocal = jimple.newLocal("configArg", servletConfigType);
			jb.getLocals().add(thisLocal);
			jb.getLocals().add(configLocal);
			
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			units.add(jimple.newIdentityStmt(configLocal, jimple.newParameterRef(servletConfigType, 0)));
			units.add(jimple.newAssignStmt(jimple.newInstanceFieldRef(thisLocal, configRef), configLocal));
			units.add(jimple.newReturnVoidStmt());
			
			declaringClass.addMethod(initMethod);
			initMethod.setActiveBody(jb);
		}
		
		{
			final SootMethod getConfigMethod = new SootMethod("getServletConfig", Collections.<Type>emptyList(),
					servletConfigType, Modifier.PUBLIC);
			final JimpleBody jb = jimple.newBody(getConfigMethod);
			final PatchingChain<Unit> units = jb.getUnits();
			final Local thisLocal = jimple.newLocal("this", thisType);
			final Local configLocal = jimple.newLocal("configArg", servletConfigType);
			jb.getLocals().add(thisLocal);
			jb.getLocals().add(configLocal);
			
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			units.add(jimple.newAssignStmt(configLocal, jimple.newInstanceFieldRef(thisLocal, configRef)));
			units.add(jimple.newReturnStmt(configLocal));
			declaringClass.addMethod(getConfigMethod);
			getConfigMethod.setActiveBody(jb);
		}
		
		{
			final SootMethod destroyMethod = new SootMethod("destroy", Collections.<Type>emptyList(), VoidType.v(), Modifier.PUBLIC);
			final JimpleBody jb = jimple.newBody(destroyMethod);
			final PatchingChain<Unit> units = jb.getUnits();
			final Local thisLocal = jimple.newLocal("this", thisType);
			jb.getLocals().add(thisLocal);
			
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			
			units.add(jimple.newInvokeStmt(
				jimple.newVirtualInvokeExpr(thisLocal, Scene.v().makeMethodRef(declaringClass, "jspDestroy", Collections.<Type>emptyList(), VoidType.v(), false))
			));
			units.add(jimple.newInvokeStmt(
				jimple.newVirtualInvokeExpr(thisLocal, Scene.v().makeMethodRef(declaringClass, "_jspDestroy", Collections.<Type>emptyList(), VoidType.v(), false))
			));
			units.add(jimple.newReturnVoidStmt());
			declaringClass.addMethod(destroyMethod);
			destroyMethod.setActiveBody(jb);
		}
		
		addServiceAdapter(declaringClass, "_jspService");
		
		{
			final RefType responseType = RefType.v("javax.servlet.http.HttpServletResponse");
			final RefType requestType = RefType.v("javax.servlet.http.HttpServletRequest");
			final List<Type> serviceParams = Arrays.<Type>asList(requestType,responseType);
			final SootMethod httpServiceMethod = new SootMethod("service",
				serviceParams, VoidType.v(),
				Modifier.PUBLIC,
				getServiceExceptions());
			final JimpleBody b = jimple.newBody(httpServiceMethod);
			final Local thisLocal = jimple.newLocal("r0", thisType);
			final Local reqLocal = jimple.newLocal("req", requestType);
			final Local respLocal = jimple.newLocal("resp", requestType);
			b.getLocals().addAll(Arrays.asList(thisLocal, reqLocal, respLocal));
			final PatchingChain<Unit> units = b.getUnits();
			units.add(jimple.newNopStmt());
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
			units.add(jimple.newIdentityStmt(reqLocal, jimple.newParameterRef(requestType, 0)));
			units.add(jimple.newIdentityStmt(respLocal, jimple.newParameterRef(responseType, 1)));
			
			units.add(
				jimple.newInvokeStmt(
					jimple.newVirtualInvokeExpr(thisLocal, Scene.v().makeMethodRef(declaringClass, "_jspService", serviceParams, VoidType.v(), false), reqLocal, respLocal)
				)
			);
			units.add(jimple.newReturnVoidStmt());
			
			httpServiceMethod.setActiveBody(b);
			declaringClass.addMethod(httpServiceMethod);
		}
	}

	private static List<SootClass> getServiceExceptions() {
		return Arrays.asList(Scene.v().getSootClass("javax.servlet.ServletException"), Scene.v().getSootClass("java.io.IOException"));
	}

	public static void addServiceAdapter(final SootClass declaringClass, final String callee) {
		final RefType thisType = declaringClass.getType();
		final Jimple jimple = Jimple.v();
		final RefType requestType = RefType.v("javax.servlet.ServletRequest");
		final RefType responseType = RefType.v("javax.servlet.ServletResponse");
		final SootMethod serviceMethod = new SootMethod("service", Arrays.<Type>asList(
				requestType,
				responseType
			), VoidType.v(), Modifier.PUBLIC,
			getServiceExceptions());
		final JimpleBody jb = jimple.newBody(serviceMethod);
		final PatchingChain<Unit> units = jb.getUnits();
		final Local thisLocal = jimple.newLocal("this", thisType);
		final Local reqLocal = jimple.newLocal("r1", requestType);
		final Local respLocal = jimple.newLocal("r2", responseType);			
		final RefType httpRequestType = RefType.v("javax.servlet.http.HttpServletRequest");
		final RefType httpResponseType = RefType.v("javax.servlet.http.HttpServletResponse");
		
		final Local t1 = jimple.newLocal("t1", httpRequestType);
		final Local t2 = jimple.newLocal("t2", httpResponseType);
		
		jb.getLocals().addAll(Arrays.asList(thisLocal, reqLocal, respLocal, t1, t2));
		
		units.add(jimple.newNopStmt());
		units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(thisType)));
		units.add(jimple.newIdentityStmt(reqLocal, jimple.newParameterRef(requestType, 0)));
		units.add(jimple.newIdentityStmt(respLocal, jimple.newParameterRef(responseType, 1)));
		
		units.add(jimple.newAssignStmt(t1, jimple.newCastExpr(reqLocal, httpRequestType)));
		units.add(jimple.newAssignStmt(t2, jimple.newCastExpr(respLocal, httpResponseType)));
		
		final SootMethodRef serviceMethodRef = Scene.v().makeMethodRef(declaringClass, callee, Arrays.<Type>asList(
			httpRequestType, httpResponseType
		), VoidType.v(), false);
		units.add(jimple.newInvokeStmt(
			jimple.newVirtualInvokeExpr(thisLocal, serviceMethodRef, t1, t2)
		));
		units.add(jimple.newReturnVoidStmt());
		
		serviceMethod.setActiveBody(jb);
		declaringClass.addMethod(serviceMethod);
	}
}
