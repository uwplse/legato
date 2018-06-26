package edu.washington.cse.instrumentation.analysis.aliasing;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import soot.ArrayType;
import soot.Local;
import soot.RefType;
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
import boomerang.pointsofindirection.Write;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;
import edu.washington.cse.instrumentation.analysis.utils.ImmutableTwoElementSet;

public final class ForwardContainerMockDataFlow implements MockedDataFlow {
	private final BoomerangContext bc;
	private final PropagationManager propagationManager;
	private final SootField containerContentField;

	public ForwardContainerMockDataFlow(final BoomerangContext bc, final PropagationManager propagationManager, final SootField containerContentField) {
		this.bc = bc;
		this.propagationManager = propagationManager;
		this.containerContentField = containerContentField;
	}

	@Override
	public boolean handles(final Unit callSite, final InvokeExpr invokeExpr,
			final AccessGraph source, final Value[] params) {
		return propagationManager.isPropagationMethod(invokeExpr.getMethod()) && propagationManager.getPropagationSpec(callSite).getPropagationTarget().isContainerAbstraction();
	}

	@Override
	public boolean flowInto(final Unit callSite, final AccessGraph source, final InvokeExpr ie,
			final Value[] params) {
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
		Local lhs = null;
		if(callSite instanceof AssignStmt) {
			lhs = (Local) ((AssignStmt) callSite).getLeftOp();
		}
		assert invokeExpr instanceof InstanceInvokeExpr;
		final Local base = (Local) ((InstanceInvokeExpr)invokeExpr).getBase();
		final SootMethod m = invokeExpr.getMethod();
		assert propagationSpec.getPropagationTarget().isContainerAbstraction();
		switch(propagationSpec.getPropagationTarget()) {
		case CONTAINER_ADDALL:
		{
			if(propagationSpec.getLocalPropagation().contains(source.getBase()) && source.firstFieldMatches(containerContentField)) {
				final HashSet<AccessGraph> out = new HashSet<>();
	      final AccessGraph withNewLocal = source.deriveWithNewLocal(base, base.getType());
	      out.add(withNewLocal);
	      // we have to pop the field because under the current boomerang implementation, write's
	      // assume we're prepending a field. We aren't, so fake it
	      for(final AccessGraph popped : source.popFirstField()) {
	      	final Write write = new Write(callSite, base, containerContentField, popped.getBase(), popped, edge);
			    if (bc.getSubQuery().addToDirectlyProcessed(write)) {
			      final Set<AccessGraph> aliases = write.process(bc);
			      out.addAll(aliases);
			    }            	
	      }
		    return out;
			} else if(lhs == null || !source.baseMatches(lhs)) {
				return Collections.singleton(source);
			} else {
				return Collections.emptySet();
			}
		}
		case CONTAINER_GET:
		{
			if(lhs == null) {
				return Collections.singleton(source);
			}
			if(source.baseAndFirstFieldMatches(base, containerContentField)) {
				final HashSet<AccessGraph> out = new HashSet<>();
				out.addAll(source.deriveWithNewLocal(lhs, source.getFirstField().getType()).popFirstField());
				if(!source.baseMatches(lhs)) {
					out.add(source);
				}
				return out;
			} else if(source.baseMatches(lhs)) {
				return Collections.emptySet();
			} else {
				return Collections.singleton(source);
			}
		}
		case CONTAINER_MOVE:
		{
			final Set<AccessGraph> out = new HashSet<>();
			if(source.baseAndFirstFieldMatches(base, containerContentField)) {
				for(final Value p : params) {
					if(!(p instanceof Local) || !(p.getType() instanceof RefType)) {
						continue;
					}
					final Local l = (Local) p;
					for(final AccessGraph popped : source.popFirstField()) {
						final Write write = new Write(callSite, l, containerContentField, base, popped, edge);
				    if (bc.getSubQuery().addToDirectlyProcessed(write)) {
				      final Set<AccessGraph> aliases = write.process(bc);
				      out.addAll(aliases);
				    }
					}
				}
			}
			if(lhs == null || !source.baseMatches(lhs)) {
				out.add(source);
			}
			return out;
		}
		case CONTAINER_PUT:
		{
			if(propagationSpec.getLocalPropagation().contains(source.getBase())) {
				final HashSet<AccessGraph> out = new HashSet<>();
				handlePutFlow(callSite, source, edge, base, out);
	      out.add(source);
	      return out;
			} else {
				return Collections.singleton(source);
			}
		}
		case CONTAINER_REPLACE:
		{
			final Set<AccessGraph> out = new HashSet<>();
			if(source.baseAndFirstFieldMatches(base, containerContentField) && lhs != null) {
				out.addAll(source.deriveWithNewLocal(lhs, source.getFirstField().getType()).popFirstField());
			}
			if(propagationSpec.getLocalPropagation().contains(source.getBase())) {
	      handlePutFlow(callSite, source, edge, base, out);
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
			} else if(source.baseAndFirstFieldMatches(base, containerContentField) && m.getReturnType() instanceof ArrayType) {
				final Set<AccessGraph> toReturn = new HashSet<>();
				final Type t = source.getFirstField().getType();
				for(final AccessGraph g : source.popFirstField()) {
					final AccessGraph pf = g.deriveWithNewLocal(lhs, lhs.getType()).prependField(new WrappedSootField(AliasFinder.ARRAY_FIELD, t, callSite));
					toReturn.add(pf);
				}
				return toReturn;
			} else if(source.baseAndFirstFieldMatches(base, containerContentField) || (source.baseMatches(base) && source.getFieldCount() == 0)) {
				if(lhs == base) {
					return Collections.singleton(source.deriveWithNewLocal(lhs, lhs.getType()));
				} else {
					return new ImmutableTwoElementSet<AccessGraph>(source, source.deriveWithNewLocal(lhs, lhs.getType()));
				}
			} else if(lhs != null && source.baseMatches(lhs)) {
				return Collections.emptySet();
			} else {
				return Collections.singleton(source);
			}
		}
			//$CASES-OMITTED$
		default:
			throw new RuntimeException();
		}
	}

	private void handlePutFlow(final Unit callSite, final AccessGraph source,
			final IPathEdge<Unit, AccessGraph> edge, final Local base,
			final Set<AccessGraph> out) {
		final AccessGraph withNewLocal = source.deriveWithNewLocal(base, base.getType());
		final WrappedSootField newFirstField = new WrappedSootField(containerContentField, source.getBaseType(), callSite);
		if (AliasResults.canPrepend(withNewLocal, newFirstField)) {
		  final AccessGraph newAp = withNewLocal.prependField(newFirstField);
		  out.add(newAp);
		  final Write write = new Write(callSite, base, containerContentField, source.getBase(), source, edge);
		  if (bc.getSubQuery().addToDirectlyProcessed(write)) {
		    final Set<AccessGraph> aliases = write.process(bc);
		    out.addAll(aliases);
		  }
		}
	}
}