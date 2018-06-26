package edu.washington.cse.instrumentation.analysis.propagation;

import java.util.Set;


import soot.Local;
import soot.SootField;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;


public class OutArgResolver extends FieldListResolver implements GraphResolver {
	private final int argPosition;
	
	public OutArgResolver(final int argPosition) {
		this(argPosition, null);
	}
	
	public OutArgResolver(final int argPosition, final SootField[] outFields) {
		super(outFields);
		this.argPosition = argPosition;
	}

	@Override
	public AccessGraph resolveGraph(final Stmt callSite) {
		final InvokeExpr ie = callSite.getInvokeExpr();
		final Local local = extractResultLocal(ie);
		if(local == null) {
			return null;
		}
		if(fields != null) {
			final WrappedSootField[] wsf = getWrappedSootFields(callSite, local);
			return new AccessGraph(local, local.getType(), wsf);
		} else {
			return new AccessGraph(local, local.getType());
		}
	}

	private Local extractResultLocal(final InvokeExpr ie) {
		final Value v = ie.getArgs().get(argPosition);
		if(!(v  instanceof Local)) {
			return null;
		}
		final Local local = (Local) v;
		return local;
	}
	
	@Override
	public void postProcessArguments(final Set<Local> arguments, final Set<Local> subFieldLocal, final InvokeExpr ie) {
		final Local resultLocal = extractResultLocal(ie);
		if(resultLocal == null) {
			return;
		}
		if(arguments.contains(resultLocal)) {
			arguments.remove(resultLocal);
		}
		if(subFieldLocal.contains(resultLocal)) {
			subFieldLocal.remove(resultLocal);
		}
	}
}
