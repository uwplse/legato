package edu.washington.cse.instrumentation.analysis.resource;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;

public class SimpleResourceResolver implements ResourceResolver {
	@Override
	public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
		return isResourceMethod(ie.getMethod());
	}
	
	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		return null;
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return m.getSubSignature().equals("int get()");
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		final Set<SootMethod> toReturn = new HashSet<>();
		for(final SootClass cls : Scene.v().getClasses()) {
			if(cls.isPhantom()) {
				continue;
			}
			if(cls.declaresMethod("int get()")) {
				toReturn.add(cls.getMethod("int get()"));
			}
		}
		return toReturn;
	}
}
