package edu.washington.cse.instrumentation.analysis.propagation;

import java.util.Set;

import soot.Local;
import soot.SootField;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;


public class ReturnGraphResolver extends FieldListResolver {
	public ReturnGraphResolver(final SootField[] fields) {
		super(fields);
	}

	@Override
	public AccessGraph resolveGraph(final Stmt callSite) {
		if(!(callSite instanceof AssignStmt)) {
			return null;
		}
		final AssignStmt as = (AssignStmt) callSite;
		final Local lhs = (Local) as.getLeftOp();
		final WrappedSootField[] wsf = getWrappedSootFields(callSite, lhs);
		return new AccessGraph(lhs, lhs.getType(), wsf);
	}

	@Override
	public void postProcessArguments(final Set<Local> arguments, final Set<Local> subFieldLocal, final InvokeExpr ie) { }
}
