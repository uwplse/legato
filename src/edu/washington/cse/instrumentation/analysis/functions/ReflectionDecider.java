package edu.washington.cse.instrumentation.analysis.functions;

import soot.Scene;
import soot.jimple.InvokeExpr;
import soot.util.NumberedString;

public class ReflectionDecider {
	private final NumberedString sigExecutorExecute = Scene.v().getSubSigNumberer().
      findOrAdd( "void execute(java.lang.Runnable)" );
	private final NumberedString sigHandlerPost = Scene.v().getSubSigNumberer().
	      findOrAdd( "boolean post(java.lang.Runnable)" );
	private final NumberedString sigHandlerPostAtFrontOfQueue = Scene.v().getSubSigNumberer().
	      findOrAdd( "boolean postAtFrontOfQueue(java.lang.Runnable)" );
	private final NumberedString sigHandlerPostAtTime = Scene.v().getSubSigNumberer().
	      findOrAdd( "boolean postAtTime(java.lang.Runnable,long)" );
	private final NumberedString sigHandlerPostAtTimeWithToken = Scene.v().getSubSigNumberer().
	      findOrAdd( "boolean postAtTime(java.lang.Runnable,java.lang.Object,long)" );
	private final NumberedString sigHandlerPostDelayed = Scene.v().getSubSigNumberer().
	      findOrAdd( "boolean postDelayed(java.lang.Runnable,long)" );
  private final NumberedString sigObjRun = Scene.v().getSubSigNumberer().
      findOrAdd( "java.lang.Object run()" );
  private final NumberedString sigRun = Scene.v().getSubSigNumberer().
      findOrAdd( "void run()" );
  private final NumberedString sigInvoke = Scene.v().getSubSigNumberer().
  		findOrAdd("java.lang.Object invoke(java.lang.Object,java.lang.Object[])");
  private final NumberedString sigNewInstance = Scene.v().getSubSigNumberer().
  		findOrAdd("java.lang.Object newInstance()");
  private final NumberedString sigConstructorNewInstance = Scene.v().getSubSigNumberer().
  		findOrAdd("java.lang.Object newInstance(java.lang.Object[])");
  
	private boolean isPrivelegedActionSig(final String signature) {
		return signature.equals( "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction)>" )
		||  signature.equals( "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction)>" )
		||  signature.equals( "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedAction,java.security.AccessControlContext)>" )
		||  signature.equals( "<java.security.AccessController: java.lang.Object doPrivileged(java.security.PrivilegedExceptionAction,java.security.AccessControlContext)>" );
	}

	public boolean isConstructorNewInstance(final NumberedString formalMethodSubSig) {
		return formalMethodSubSig == sigConstructorNewInstance;
	}
	
	public boolean isPrivilegedActionCall(final InvokeExpr ie, final NumberedString calledMethodSubSig) {
		return isPrivelegedActionSig(ie.getMethod().getSignature()) && calledMethodSubSig == sigObjRun;
	}

	public boolean isMethodInvoke(final NumberedString formalMethodSubSig) {
		return formalMethodSubSig == sigInvoke;
	}
	
	public boolean isNewInstance(final NumberedString formalMethodSubSig) {
		return formalMethodSubSig == sigNewInstance;
	}

	public boolean isExecutorExecute(final NumberedString formalMethodSubSig,
			final NumberedString calledMethodSubSig) {
		return (formalMethodSubSig == sigExecutorExecute ||
		formalMethodSubSig == sigHandlerPost ||
		formalMethodSubSig == sigHandlerPostAtFrontOfQueue ||
		formalMethodSubSig == sigHandlerPostAtTimeWithToken ||
		formalMethodSubSig == sigHandlerPostAtTime ||
		formalMethodSubSig == sigHandlerPostDelayed) && calledMethodSubSig == sigRun;
	}
	
}
