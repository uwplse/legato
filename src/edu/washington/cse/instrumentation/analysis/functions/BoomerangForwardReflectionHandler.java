package edu.washington.cse.instrumentation.analysis.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import soot.ArrayType;
import soot.Local;
import soot.PrimType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.util.NumberedString;
import boomerang.AliasFinder;
import boomerang.BoomerangContext;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.forward.AbstractFlowFunctions;
import boomerang.ifdssolver.IPathEdge;
import boomerang.mock.ReflectiveCallHandler;

public class BoomerangForwardReflectionHandler extends AbstractReflectionHandler implements ReflectiveCallHandler {
	public BoomerangForwardReflectionHandler(final BoomerangContext context, final ReflectionDecider rd) {
		super(context, rd);
		
	}

	@Override
	public Set<AccessGraph> computeReturnFlow(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params,
			final IPathEdge<Unit, AccessGraph> edge, final Unit returnSite, final Unit exitStmt, final SootMethod callee) {
		
		if(source.isStatic()) {
			return Collections.singleton(source);
		}
		final NumberedString formalMethodSubSig = ((Stmt)callSite).getInvokeExpr().getMethod().getNumberedSubSignature();
		final NumberedString calledMethodSubSig = callee.getNumberedSubSignature();
		if(
				// executor execute
			reflectionDecider.isExecutorExecute(formalMethodSubSig, calledMethodSubSig) ||
			// privilegd do
			(reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig) &&
				!(callSite instanceof AssignStmt))) {
			
			assert params.length == 1 : Arrays.toString(params);
			if(source.baseMatches(callee.getActiveBody().getThisLocal()) && params[0] instanceof Local) {
				final Set<AccessGraph> out = new HashSet<>();
        handleReturnThisParameter(source, params, out);
	      return out;
			}
			return Collections.emptySet();
		} else if(reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig)) {
			final Set<AccessGraph> out = new HashSet<>();
			if(source.baseMatches(callee.getActiveBody().getThisLocal()) && params[0] instanceof Local) {
        handleReturnThisParameter(source, params, out);
			}
			handleReturnFlow(callSite, source, exitStmt, out);
			return out;
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			if(!(callSite instanceof AssignStmt)) {
				return Collections.emptySet();
			}
			if(!source.baseMatches(callee.getActiveBody().getThisLocal())) {
				return Collections.emptySet();
			}
			final HashSet<AccessGraph> out = new HashSet<>();
			handleReflectionConstructor(callSite, source, out);
			return out;
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			final Set<AccessGraph> out = new HashSet<>();
			handleInvokeArgReturn(out, params[0], callee, source);
			if(source.baseMatches(callee.getActiveBody().getThisLocal())) {
				handleReflectionConstructor(callSite, source, out);
			}
			return out;
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final Set<AccessGraph> out = new HashSet<>();
			handleInvokeArgReturn(out, params[1], callee, source);
			handleReturnFlow(callSite, source, exitStmt, out);
			if(params[0] instanceof Local && !callee.isStatic() && 
					source.baseMatches(callee.getActiveBody().getThisLocal())) {
				handleReturnThisParameter(source, params, out);
			}
			return out;
		}
		throw new RuntimeException();
	}

	private void handleReturnThisParameter(final AccessGraph source, final Value[] params, final Set<AccessGraph> out) {
		final Local newBase = (Local) params[0];
		if (AbstractFlowFunctions.typeCompatible(newBase.getType(), source.getBaseType())) {
		  final AccessGraph possibleAccessPath =
		      source.deriveWithNewLocal(newBase, source.getBaseType());
		  out.add(possibleAccessPath);
		}
	}

	private void handleReturnFlow(final Unit callSite, final AccessGraph source, final Unit exitStmt, final Set<AccessGraph> out) {
		if(exitStmt instanceof ReturnStmt) {
		  final AssignStmt as = (AssignStmt) callSite;
		  final Value leftOp = as.getLeftOp();

		  final ReturnStmt returnStmt = (ReturnStmt) exitStmt;
		  final Value returns = returnStmt.getOp();
		  if (leftOp instanceof Local) {
		    if (returns instanceof Local && source.getBase() == returns) {
		      out.add(source.deriveWithNewLocal((Local) leftOp, source.getBaseType()));
		    }
		  }
		}
	}

	private void handleReflectionConstructor(final Unit callSite, final AccessGraph source, final Set<AccessGraph> out) {
		final AssignStmt assignStmt = (AssignStmt) callSite;
		final Local newBase = (Local) assignStmt.getLeftOp();
		if (AbstractFlowFunctions.typeCompatible(newBase.getType(), source.getBaseType())) {
		  final AccessGraph possibleAccessPath =
		      source.deriveWithNewLocal(newBase, source.getBaseType());
		  out.add(possibleAccessPath);
		}
	}
	
	private void handleInvokeArgReturn(final Set<AccessGraph> out, final Value arrVal, final SootMethod callee, final AccessGraph source) {
		if(!(arrVal instanceof Local)) {
			return;
		}
		assert arrVal.getType() instanceof ArrayType;
		final Local[] paramLocals = callee.getActiveBody().getParameterLocals().toArray(new Local[0]);
		for(int i = 0; i < paramLocals.length; i++) {
			if(source.baseMatches(paramLocals[0])) {
				final AccessGraph outPath = source.deriveWithNewLocal((Local) arrVal, arrVal.getType()).prependField(new WrappedSootField(AliasFinder.ARRAY_FIELD, source.getBaseType(), null));
				out.add(outPath);
			}
		}
	}

	@Override
	public Set<AccessGraph> computeCallFlow(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params, final IPathEdge<Unit, AccessGraph> edge,
			final SootMethod callee, final Unit startPoint) {
		if(source.isStatic()) {
			if(context.icfg.isStaticFieldUsed(callee, source.getFirstField().getField())) {
				return Collections.singleton(source);
			} else {
				return Collections.emptySet();
			}
		}
		
		final NumberedString formalMethodSubSig = ((Stmt)callSite).getInvokeExpr().getMethod().getNumberedSubSignature();
		final NumberedString calledMethodSubSig = callee.getNumberedSubSignature();
		if(
				// executor execute
			reflectionDecider.isExecutorExecute(formalMethodSubSig, calledMethodSubSig) ||
			// privilegd do
			reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig)) {
			assert params.length == 1 : Arrays.toString(params);
			if(source.baseMatches(params[0])) {
				final Set<AccessGraph> out = new HashSet<>();
	      handleCallThisParameter(source, edge, callee, out);
	      return out;
			}
			return Collections.emptySet();
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			return Collections.emptySet();
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			assert params.length == 1;
			if(!source.baseAndFirstFieldMatches(params[0], AliasFinder.ARRAY_FIELD)) {
				return Collections.emptySet();
			}
			final Set<AccessGraph> out = new HashSet<>();
			handleArgArray(source, callee, out);
      return out;
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final HashSet<AccessGraph> out = new HashSet<>();
			if(source.baseMatches(params[0]) && !callee.isStatic()) {
				handleCallThisParameter(source, edge, callee, out);
			}
			if(source.baseAndFirstFieldMatches(params[1], AliasFinder.ARRAY_FIELD)) {
				handleArgArray(source, callee, out);
			}
			return out;
		}
		
		throw new RuntimeException();
	}

	private void handleArgArray(final AccessGraph source, final SootMethod callee, final Set<AccessGraph> out) {
		final Local[] paramLocals = callee.getActiveBody().getParameterLocals().toArray(new Local[0]);
		for(int i = 0; i < paramLocals.length; i++) {
			if (AbstractFlowFunctions.typeCompatible(paramLocals[i].getType(), source.getFirstField().getType())) {
		    out.addAll(source.deriveWithNewLocal(paramLocals[i], source.getFirstField().getType()).popFirstField());
		  }
		}
	}

	private void handleCallThisParameter(final AccessGraph source, final IPathEdge<Unit, AccessGraph> edge, final SootMethod callee, final Set<AccessGraph> out) {
		if (callee != null && !AbstractFlowFunctions.hasCompatibleTypesForCall(source, callee.getDeclaringClass()))
		  return;
		if (context.isIgnoredMethod(callee)) {
		  return;
		}
		final AccessGraph d1 = edge != null ? edge.factAtSource() : null;
		if (d1 != null && d1.hasAllocationSite() && source.getFieldCount() < 1) {
		  final Unit sourceStmt = d1.getSourceStmt();
		  if (sourceStmt instanceof AssignStmt) {
		    final AssignStmt as = (AssignStmt) sourceStmt;
		    final Value rightOp = as.getRightOp();
		    final Type type = rightOp.getType();
		    if (type instanceof RefType) {
		      final RefType refType = (RefType) type;
		      final SootClass typeClass = refType.getSootClass();
		      final SootClass methodClass = callee.getDeclaringClass();
		      if (typeClass != null && methodClass != null && typeClass != methodClass
		          && !typeClass.isInterface()) {
		        if (!Scene.v().getFastHierarchy().isSubclass(typeClass, methodClass)) {
		          return;
		        }
		      }
		    } else if (type instanceof PrimType) {
		      return;
		    }
		  }
		}
		final AccessGraph replacedThisValue =
		    source.deriveWithNewLocal(callee.getActiveBody().getThisLocal(), source.getBaseType());
		if (context.isValidAccessPath(replacedThisValue)) {
		  out.add(replacedThisValue);
		}
	}

}
