package edu.washington.cse.instrumentation.analysis.resource;

import java.util.Collection;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;

public interface ResourceResolver {
	boolean isResourceAccess(InvokeExpr ie, Unit u);
	/*
	 * Return null means "*", aka don't know, maybe every resource 
	 */
	Set<String> getAccessedResources(InvokeExpr ie, Unit u);
	
	boolean isResourceMethod(SootMethod m);
	Collection<SootMethod> getResourceAccessMethods();
}
