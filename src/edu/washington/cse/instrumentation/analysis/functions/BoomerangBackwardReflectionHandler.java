package edu.washington.cse.instrumentation.analysis.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import soot.ArrayType;
import soot.Local;
import soot.SootMethod;
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
import boomerang.forward.ForwardFlowFunctions;
import boomerang.ifdssolver.IPathEdge;

public class BoomerangBackwardReflectionHandler extends AbstractReflectionHandler {
	public BoomerangBackwardReflectionHandler(final BoomerangContext context, final ReflectionDecider rd) {
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
			reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig)) {
			assert params.length == 1 : Arrays.toString(params);
			if(source.baseMatches(callee.getActiveBody().getThisLocal()) && params[0] instanceof Local) {
				final Set<AccessGraph> out = new HashSet<>();
        handleCallThisParameter(source, (Local) params[0], out);
	      return out;
			}
			return Collections.emptySet();
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			return Collections.emptySet();
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			final Set<AccessGraph> out = new HashSet<>();
			handleInvokeArgReturn(out, params[0], callee, source);
      return out;
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final HashSet<AccessGraph> out = new HashSet<>();
			if(params[0] instanceof Local && !callee.isStatic() && 
					source.baseMatches(callee.getActiveBody().getThisLocal())) {
				handleCallThisParameter(source, (Local) params[0], out);
			}
			handleInvokeArgReturn(out, params[1], callee, source);
			return out;
		}
		throw new RuntimeException();
	}

	private void handleInvokeArgReturn(final Set<AccessGraph> out, final Value arrVal, final SootMethod callee, final AccessGraph source) {
		if(!(arrVal instanceof Local)) {
			return;
		}
		assert arrVal.getType() instanceof ArrayType;
		final Local[] paramLocals = callee.getActiveBody().getParameterLocals().toArray(new Local[0]);
		for(int i = 0; i < paramLocals.length; i++) {
			if(source.baseMatches(paramLocals[0])) {
				final AccessGraph outPath = source.deriveWithNewLocal((Local) arrVal,
						arrVal.getType()).prependField(new WrappedSootField(AliasFinder.ARRAY_FIELD, source.getBaseType(), null));
				out.add(outPath);
			}
		}
	}

	private void handleCallThisParameter(final AccessGraph source, final Local newBase, final Set<AccessGraph> out) {
		if (AbstractFlowFunctions.typeCompatible(newBase.getType(), source.getBaseType())) {
		  final AccessGraph possibleAccessPath =
		      source.deriveWithNewLocal(newBase, source.getBaseType());
		  out.add(possibleAccessPath);
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
			(reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig) &&
				!(callSite instanceof AssignStmt))) {
			
			assert params.length == 1 : Arrays.toString(params);
			if(params[0] instanceof Local) {
				final Set<AccessGraph> out = new HashSet<>();
        handleCallThisParameter(source, params, out, callee);
	      return out;
			}
			return Collections.emptySet();
		} else if(reflectionDecider.isPrivilegedActionCall(((Stmt)callSite).getInvokeExpr(), calledMethodSubSig)) {
			final Set<AccessGraph> out = new HashSet<>();
			if(params[0] instanceof Local) {
        handleCallThisParameter(source, params, out, callee);
			}
			handleCallReturnFlow(callSite, source, startPoint, out, callee);
			return out;
		} else if(reflectionDecider.isNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			if(!(callSite instanceof AssignStmt)) {
				return Collections.emptySet();
			}
			final HashSet<AccessGraph> out = new HashSet<>();
			handleReflectionConstructor(callSite, source, out, callee);
			return out;
		} else if(reflectionDecider.isConstructorNewInstance(formalMethodSubSig)) {
			if(!callee.isConstructor()) {
				return Collections.emptySet();
			}
			final Set<AccessGraph> out = new HashSet<>();
			handleInvokeArgCall(out, params[0], callee, source);
			handleReflectionConstructor(callSite, source, out, callee);
			return out;
		} else if(reflectionDecider.isMethodInvoke(formalMethodSubSig)) {
			final Set<AccessGraph> out = new HashSet<>();
			handleInvokeArgCall(out, params[1], callee, source);
			handleCallReturnFlow(callSite, source, startPoint, out, callee);
			if(params[0] instanceof Local && !callee.isStatic()) {
				handleCallThisParameter(source, params, out, callee);
			}
			return out;
		}
		throw new RuntimeException();
	}

	private void handleInvokeArgCall(final Set<AccessGraph> out, final Value arrVal, final SootMethod callee, final AccessGraph source) {
		if(!source.baseAndFirstFieldMatches(arrVal, AliasFinder.ARRAY_FIELD)) {
			return;
		}
		final Local[] paramLocals = callee.getActiveBody().getParameterLocals().toArray(new Local[0]);
		for(int i = 0; i < paramLocals.length; i++) {
			if (AbstractFlowFunctions.typeCompatible(paramLocals[i].getType(), source.getFirstField().getType())) {
		    final Set<AccessGraph> popped = source.deriveWithNewLocal(paramLocals[i], source.getFirstField().getType()).popFirstField();
		    for(final AccessGraph p : popped) {
		    	if(p.getFieldCount() > 0) {
		    		out.addAll(popped);
		    	}
		    }
		  }
		}
	}

	private void handleReflectionConstructor(final Unit callStmt, final AccessGraph source,
			final Set<AccessGraph> out, final SootMethod callee) {
    final AssignStmt as = (AssignStmt) callStmt;
    final Value leftOp = as.getLeftOp();
    if (leftOp instanceof Local && source.baseMatches(leftOp) && 
    		AbstractFlowFunctions.hasCompatibleTypesForCall(source, callee.getDeclaringClass()) && source.getFieldCount() > 0) {
      if (!context.isIgnoredMethod(callee)) {
        final AccessGraph replacedThisValue =
            source.deriveWithNewLocal(callee.getActiveBody().getThisLocal(), source.getBaseType());
        if (context.isValidAccessPath(replacedThisValue)) {
          out.add(replacedThisValue);
        }
      }
    }
	}

	private void handleCallReturnFlow(final Unit callStmt, final AccessGraph source, final Unit calleeSp,
			final Set<AccessGraph> out, final SootMethod callee) {
    final AssignStmt as = (AssignStmt) callStmt;
    final Value leftOp = as.getLeftOp();
    // mapping of return value
    if (leftOp instanceof Local && source.baseMatches(leftOp) && calleeSp instanceof ReturnStmt) {
      final ReturnStmt retSite = (ReturnStmt) calleeSp;
      final Value retOp = retSite.getOp();
      if (!context.isIgnoredMethod(callee)) {
        if (retOp instanceof Local) {
          if (AbstractFlowFunctions.typeCompatible(((Local) retOp).getType(), source.getBaseType())) {
            final AccessGraph possibleAccessPath =
                source.deriveWithNewLocal((Local) retOp, source.getBaseType());
            out.add(possibleAccessPath);
          }
        }
      }
    }
	}



	private void handleCallThisParameter(final AccessGraph source, final Value[] params, final Set<AccessGraph> out,
			final SootMethod callee) {
    if (source.baseMatches(params[0])) {
      if (callee != null
          && !ForwardFlowFunctions.hasCompatibleTypesForCall(source,
              callee.getDeclaringClass()))
        return;
      if (source.getFieldCount() == 0)
        return;
      if (!context.isIgnoredMethod(callee)) {
        final AccessGraph replacedThisValue =
            source.deriveWithNewLocal(callee.getActiveBody().getThisLocal(), source.getBaseType());
        if (context.isValidAccessPath(replacedThisValue)) {
          out.add(replacedThisValue);
        }
      }
    }
	}
}
