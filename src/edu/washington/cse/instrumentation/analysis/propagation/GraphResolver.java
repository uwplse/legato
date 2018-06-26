package edu.washington.cse.instrumentation.analysis.propagation;

import java.util.Set;

import soot.Local;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import boomerang.accessgraph.AccessGraph;

public interface GraphResolver {
	public AccessGraph resolveGraph(Stmt callSite);
	public void postProcessArguments(Set<Local> arguments, Set<Local> subFieldLocal, InvokeExpr ie);
}
