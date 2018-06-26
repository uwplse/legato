package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;

import soot.Body;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.VoidType;
import soot.jimple.NullConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JReturnStmt;

public class GenerateControllerActions {
	private static final String ON_SUBMIT_SIMPLE = "org.springframework.web.servlet.ModelAndView onSubmit(java.lang.Object)";
	private static final String REDIRECT_VIEW_CLASS = "org.springframework.web.servlet.view.RedirectView";
	private static final String MODEL_VIEW_CLASS = "org.springframework.web.servlet.ModelAndView";
	private static final String REDIRECT_SENTINEL = "$$LEGATO$$REDIRECT";
	private static final String NO_VIEW = "$$LEGATO$$NOVIEW";
	private static final String ON_SUBMIT = "org.springframework.web.servlet.ModelAndView " +
			"onSubmit(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse,java.lang.Object,org.springframework.validation.BindException)";
	private static final String HANDLE_REQUEST_INTERNAL = 
			"org.springframework.web.servlet.ModelAndView handleRequestInternal(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)";
	private static final String HANDLE_REQUEST = 
			"org.springframework.web.servlet.ModelAndView handleRequest(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)";

	public static void main(final String[] args) throws IOException {
		final XML xml = new XMLDocument(new File(args[0])).registerNs("b", "http://www.springframework.org/schema/beans");
		final XML urlMapping = xml.nodes("/b:beans/b:bean[@id='urlMapping']").get(0);
		final HashSet<String> ids = new HashSet<>(urlMapping.xpath("./b:property[@name='mappings']/b:props/b:prop/text()"));
		final Set<String> classes = new HashSet<>();
		for(final String id : ids) {
			classes.addAll(xml.xpath("/b:beans/b:bean[@id='" + id + "']/@class"));
		}
		final String[] sootArgs = new String[]{
			"-soot-class-path",
			args[1] + "/target/subsonic/WEB-INF/classes",
			"-allow-phantom-refs",
			"-f", "none"
		};
		final String[] allArgs = new String[sootArgs.length + classes.size()];
		System.arraycopy(sootArgs, 0, allArgs, 0, sootArgs.length);
		System.arraycopy(classes.toArray(new String[0]), 0, allArgs, sootArgs.length, classes.size());
		// gross hack, but I don't feel like jumping through hoops setting up soot args
		soot.Main.main(allArgs);
		boolean first = true;
		final List<Type> handlerList = Arrays.<Type>asList(RefType.v("javax.servlet.http.HttpServletRequest"), RefType.v("javax.servlet.http.HttpServletResponse"));
		final RefType modelAndView = RefType.v("org.springframework.web.servlet.ModelAndView");
		
		for(final String controllerId : ids) {
			final SootClass cls = Scene.v().getSootClass(xml.xpath("/b:beans/b:bean[@id='" + controllerId + "']/@class").get(0));
			final String superClass = cls.getSuperclass().toString();
			if(first) {
				System.out.println("if(nondetBool()) {");
				first = false;
			} else {
				System.out.println("} else if(nondetBool()) {");
			}
			if(superClass.equals("java.lang.Object") && cls.getInterfaces().contains(Scene.v().getSootClass("org.springframework.web.servlet.mvc.Controller"))) {
				startTry(); {
					System.out.println("this." + controllerId + ".handleRequest(arg0, arg1);");
				} handleError();
			} else if(superClass.equals("org.springframework.web.servlet.mvc.ParameterizableViewController")) {
				startTry(); {
					System.out.printf("ModelAndView mv = this.%s.handleRequestInternal(arg0, arg1);\n", controllerId);
					moveModelToRequest();
					final String viewName = getControllerProperty(xml, controllerId, "viewName");
					forwardToUrl(viewName);
				} handleError();
			} else if(superClass.equals("org.springframework.web.servlet.mvc.SimpleFormController")) {
				final String successViewName = getControllerProperty(xml, controllerId, "formView");
				final String failureViewName = getControllerProperty(xml,controllerId , "successView");
				if(cls.declaresMethod(ON_SUBMIT)) {
					forwardToUrl(failureViewName);
					System.out.println("} else if(nondetBool()) {");
					startTry(); {
						System.out.printf("Object tmp = this.%s.formBackingObject(arg0);\n", controllerId);
						System.out.printf("ModelAndView mv = this.%s.onSubmit(arg0, arg1, tmp, new org.springframework.validation.BindException(tmp, \"\"+System.currentTimeMillis()));\n", controllerId, controllerId);
						moveModelToRequest();
						handleMVReturn(cls.getMethod(ON_SUBMIT));
					} handleError();
				} else if(cls.declaresMethod("void doSubmitAction(java.lang.Object)")) {
					forwardToUrl(failureViewName);
					System.out.println("} else if(nondetBool()) {");
					startTry(); {
						System.out.printf("this.%s.doSubmitAction(this.%s.formBackingObject(arg0));\n", controllerId, controllerId);
						forwardToUrl(successViewName);
					} handleError();
				} else if(cls.declaresMethod(ON_SUBMIT_SIMPLE)) {
					forwardToUrl(failureViewName);
					System.out.println("} else if(nondetBool()) {");
					startTry(); {
						System.out.printf("Object tmp = this.%s.formBackingObject(arg0);\n", controllerId);
						System.out.printf("ModelAndView mv = this.%s.onSubmit(tmp);\n", controllerId, controllerId);
						moveModelToRequest();
						handleMVReturn(cls.getMethod(ON_SUBMIT_SIMPLE));
					} handleError();
				} else {
					System.out.println("// TODO: handle " + cls);
				}
			} else if(superClass.equals("org.springframework.web.servlet.mvc.multiaction.MultiActionController")) {
				boolean firstMulti = true;
				for(final SootMethod m : cls.getMethods()) {
					final boolean hasMatchingType = m.getReturnType().equals(modelAndView) || m.getReturnType() == VoidType.v();
					if(!hasMatchingType || !m.getParameterTypes().equals(handlerList)) {
						continue;
					}
					if(!m.isPublic()) {
						continue;
					}
					if(!firstMulti) {
						System.out.println("} else if(nondetBool()) {");
					} else {
						firstMulti = false;
					}
					if(m.getReturnType() == VoidType.v()) {
						startTry(); {
							System.out.printf("this.%s.%s(arg0, arg1);\n", controllerId, m.getName());
							System.out.println("return;");
						} handleError();
					} else {
						startTry(); {
							System.out.printf("ModelAndView mv = this.%s.%s(arg0, arg1);\n", controllerId, m.getName());
							moveModelToRequest();
							handleMVReturn(m);
						} handleError();
					}
				}
			} else if(superClass.equals("org.springframework.web.servlet.mvc.AbstractController")) {
				startTry(); {
					System.out.printf("ModelAndView mv = this.%s.handleRequestInternal(arg0, arg1);\n", controllerId);
					moveModelToRequest();
					handleMVReturn(cls.getMethod(HANDLE_REQUEST_INTERNAL));
				} handleError();
			} else {
				startTry(); {
					assert cls.declaresMethod(HANDLE_REQUEST);
					System.out.printf("ModelAndView mv = this.%s.handleRequest(arg0, arg1);\n", controllerId);
					handleMVReturn(cls.getMethod(HANDLE_REQUEST));
				} handleError();
			}
		}
		System.out.println("}");
	}

	private static void startTry() {
		System.out.println("try {");
	}

	private static void handleError() {
		System.out.println("} catch(Exception e) { arg0.getRequestDispatcher(\"/error.jsp\").forward(arg0, arg1); return; }");
	}

	private static void handleMVReturn(final SootMethod m) {
		final String staticView = tryResolveStaticView(m);
		if(staticView == null) {
			nondetermisticView();
		// HAAACK
		} else if(staticView.equals(REDIRECT_SENTINEL)) {
			System.out.println("return;");
		} else if(staticView.equals(NO_VIEW)) {
			System.out.println("return;");
		} else {
			forwardToUrl(staticView);
		}
	}

	private static String getControllerProperty(final XML xml, final String controllerId, final String property) {
		return xml.xpath("/b:beans/b:bean[@id='" + controllerId + "']/b:property[@name='" + property +"']/@value").get(0);
	}

	private static void nondetermisticView() {
		System.out.printf("arg0.getRequestDispatcher(\"\"+System.currentTimeMillis()).forward(arg0, arg1);");
		System.out.println("return;");
	}

	private static void forwardToUrl(final String successViewName) {
		System.out.printf("arg0.getRequestDispatcher(\"/WEB-INF/jsp/%s.jsp\").forward(arg0, arg1);\n", successViewName);
		System.out.println("return;");
	}

	private static String tryResolveStaticView(final SootMethod m) {
		final Body b = m.retrieveActiveBody();
		String toRet = null;
		for(final Unit u : b.getUnits()) {
			if(u instanceof JReturnStmt) {
				final JReturnStmt ret = (JReturnStmt) u;
				if(ret.getOp() instanceof NullConstant) {
					if(toRet != null && !toRet.equals(NO_VIEW)) {
						return null;
					}
					toRet = NO_VIEW;
				}
				continue;
			}
			final Stmt s = (Stmt) u;
			if(!s.containsInvokeExpr()) {
				continue;
			}
			final SootMethodRef mref = s.getInvokeExpr().getMethodRef();
			final boolean resolveCall = isResolveCall(mref);
			if(resolveCall) {
				if(mref.parameterType(0) == RefType.v("java.lang.String") && s.getInvokeExpr().getArg(0) instanceof StringConstant) {
					final StringConstant sc = (StringConstant) s.getInvokeExpr().getArg(0);
					if(toRet != null && !toRet.equals(sc.value)) {
						return null;
					}
					toRet = sc.value;
				} else if(resolveCall && s.getInvokeExpr().getArg(0) instanceof RefType && s.getInvokeExpr().getArg(0).getType() == RefType.v(REDIRECT_VIEW_CLASS)) {
					if(toRet != null && !toRet.equals(REDIRECT_SENTINEL)) {
						return null;
					}
					toRet = REDIRECT_SENTINEL;
				} else {
					return null;
				}
			}
		}
		return toRet;
	}

	private static boolean isResolveCall(final SootMethodRef mref) {
		return mref.declaringClass().getName().equals(MODEL_VIEW_CLASS) && mref.name().equals("<init>") 
				&& mref.parameterTypes().size() > 0;
	}

	private static void moveModelToRequest() {
		System.out.printf("arg0.setAttribute(System.currentTimeMillis()+\"\", mv.getModel().get(System.currentTimeMillis()+\"\"));\n");
	}
}
