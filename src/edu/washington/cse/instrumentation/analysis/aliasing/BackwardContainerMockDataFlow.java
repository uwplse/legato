package edu.washington.cse.instrumentation.analysis.aliasing;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import soot.ArrayType;
import soot.FastHierarchy;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import boomerang.AliasFinder;
import boomerang.BoomerangContext;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;
import boomerang.cache.AliasResults;
import boomerang.ifdssolver.IPathEdge;
import boomerang.mock.MockedDataFlow;
import boomerang.pointsofindirection.Read;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;
import edu.washington.cse.instrumentation.analysis.utils.ImmutableTwoElementSet;

public final class BackwardContainerMockDataFlow implements MockedDataFlow {
	private final BoomerangContext bc;
	private final PropagationManager propagationManager;
	private final SootField containerContentField;

	public BackwardContainerMockDataFlow(final BoomerangContext bc, final PropagationManager propagationManager, final SootField containerContentField) {
		this.bc = bc;
		this.propagationManager = propagationManager;
		this.containerContentField = containerContentField;
	}

	@Override
	public boolean handles(final Unit callSite, final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params) {
		return propagationManager.isPropagationMethod(invokeExpr.getMethod()) && propagationManager.getPropagationSpec(callSite).getPropagationTarget().isContainerAbstraction();
	}

	@Override
	public boolean flowInto(final Unit callSite, final AccessGraph source, final InvokeExpr ie, final Value[] params) {
		return true;
	}

	@Override
	public Set<AccessGraph> computeTargetsOverCall(final Unit callSite,
			final InvokeExpr invokeExpr, final AccessGraph source, final Value[] params,
			final IPathEdge<Unit, AccessGraph> edge, final Unit returnSite) {
		final PropagationSpec propagationSpec = propagationManager.getPropagationSpec(callSite);
		if(source.isStatic()) {
			return Collections.singleton(source);
		}
		Value lhs = null;
		if(callSite instanceof AssignStmt) {
			lhs = ((AssignStmt) callSite).getLeftOp();
		}
		assert invokeExpr instanceof InstanceInvokeExpr;
		final Local base = (Local) ((InstanceInvokeExpr)invokeExpr).getBase();
		final SootMethod m = invokeExpr.getMethod();
		assert propagationSpec.getPropagationTarget().isContainerAbstraction();
		switch(propagationSpec.getPropagationTarget()) {
		case CONTAINER_ADDALL:
		{
			if(!source.baseAndFirstFieldMatches(base, containerContentField)) {
				return Collections.singleton(source);
			}
			final Set<AccessGraph> out = new HashSet<>();
			out.add(source);
			for(final Value p : params) {
				if(!(p instanceof Local) || !(p.getType() instanceof RefType)) {
					continue;
				}
				final Local l = (Local) p;
				for(final AccessGraph popped : source.popFirstField()) {
					final Read handler = new Read(edge, l, containerContentField, returnSite, popped);
	        if (bc.getSubQuery() != null) {
	          bc.getSubQuery().add(handler);
	        }
				}
				out.add(source.deriveWithNewLocal(l, l.getType()));
			}
			return out;
		}
		case CONTAINER_GET:
		{
			if(lhs == null) {
				return Collections.singleton(source);
			} else if(source.baseMatches(lhs)) {
				final Set<AccessGraph> out = new HashSet<>();
	      handleGetFlow(callSite, source, edge, returnSite, base, out);
	      return out;
			} else {
				return Collections.singleton(source);
			}
		}
		case CONTAINER_MOVE:
		{
			for(final Value p : params) {
				if(source.baseAndFirstFieldMatches(p, containerContentField)) {
					for(final AccessGraph popped : source.popFirstField()) {
						final Read handler = new Read(edge, base, containerContentField, returnSite, popped);
	          if (bc.getSubQuery() != null) {
	            bc.getSubQuery().add(handler);
	          }
					}
					return new ImmutableTwoElementSet<AccessGraph>(source, source.deriveWithNewLocal(base, base.getType()));
				}
			}
			return Collections.singleton(source);
		}
		case CONTAINER_PUT:
		{
			if(source.baseAndFirstFieldMatches(base, containerContentField)) {
				final Set<AccessGraph> toReturn = new HashSet<>();
				handlePutFlow(invokeExpr, source, toReturn);
				toReturn.add(source);
				return toReturn;
			} else {
				return Collections.singleton(source);
			}
		}
		case CONTAINER_REPLACE:
		{
	    final Set<AccessGraph> out = new HashSet<>();
			if(lhs != null && source.baseMatches(lhs)) {
	      handleGetFlow(callSite, source, edge, returnSite, base, out);
			}
			if(source.baseAndFirstFieldMatches(base, containerContentField)) {
				handlePutFlow(invokeExpr, source, out);
			}
			if(lhs == null || !source.baseMatches(lhs)) {
				out.add(source);
			}
			return out;
		}
		case CONTAINER_TRANSFER:
		{
			if(lhs == null) {
				return Collections.singleton(source);
			} else if(source.baseAndFirstFieldMatches(lhs, containerContentField) || (source.baseMatches(lhs) && source.getFieldCount() == 0)) {
				return Collections.singleton(source.deriveWithNewLocal(base, base.getType()));
			} else if(source.baseAndFirstFieldMatches(lhs, AliasFinder.ARRAY_FIELD) && m.getReturnType() instanceof ArrayType) {
				final Set<AccessGraph> toReturn = new HashSet<>();
				assert source.getBaseType() instanceof ArrayType;
				final Type innerType = ((ArrayType)source.getBaseType()).baseType;
				for(final AccessGraph g : source.popFirstField()) {
					final AccessGraph pf = g.deriveWithNewLocal(base, base.getType()).prependField(new WrappedSootField(containerContentField, innerType, callSite));
					toReturn.add(pf);
				}
				return toReturn;
			} else {
				return Collections.singleton(source);
			}
		}
			//$CASES-OMITTED$
		default:
			throw new RuntimeException();
		}
	}

	private void handleGetFlow(final Unit callSite, final AccessGraph source,
			final IPathEdge<Unit, AccessGraph> edge, final Unit returnSite,
			final Local base, final Set<AccessGraph> out) {
		final Read handler = new Read(edge, base, containerContentField, returnSite, source);
		if (bc.getSubQuery() != null)
		  bc.getSubQuery().add(handler);

		final WrappedSootField newFirstField =
		    new WrappedSootField(containerContentField, source.getBaseType(), callSite);
		final AccessGraph ap = source.deriveWithNewLocal(base, base.getType());
		if (AliasResults.canPrepend(ap, newFirstField)) {
		  final AccessGraph prependField = ap.prependField(newFirstField);
		  out.add(prependField);
		}
	}

	private void handlePutFlow(final InvokeExpr invokeExpr,
			final AccessGraph source, final Set<AccessGraph> out) {
		if(!AtMostOnceProblem.mayAliasType(source.getFirstField().getType())) {
			out.add(source);
			return;
		}
		final Type fieldType = source.getFirstField().getType();
		final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		for(final Value v : invokeExpr.getArgs()) {
			if(!(v instanceof Local)) {
				continue;
			}
			final Local l = (Local) v;
			if(!fh.canStoreType(fieldType, v.getType())) {
				continue;
			}
			out.addAll(source.deriveWithNewLocal(l, fieldType).popFirstField());
		}
	}
}