package edu.washington.cse.instrumentation.analysis.resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import soot.Local;
import soot.PointsToAnalysis;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;

public abstract class PointsToMethodResourceResolver implements ResourceResolver {
	@Override
	public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
		if(!this.isResourceMethod(ie.getMethod())) {
			return false;
		}
		final Set<String> accessedResources = getAccessedResources(ie, u);
		if(accessedResources == null) {
			return true;
		}
		return accessedResources.size() > 0;
	}

	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		assert this.isResourceMethod(ie.getMethod());
		final int argumentPosition = this.getArgumentPosition(ie.getMethod());
		if(argumentPosition == -1) {
			return null;
		}
		final List<Value> args = ie.getArgs();
		final Value v = args.get(argumentPosition);
		if(v instanceof NullConstant) {
			return Collections.emptySet();
		} else if(v instanceof StringConstant) {
			return Collections.singleton(((StringConstant) v).value);
		} else {
			assert v instanceof Local;
			final PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
			final Set<String> consts = pta.reachingObjects((Local)v).possibleStringConstants();
			// why tho
			if(consts != null && consts.size() == 0) {
				return null;
			}
			return consts;
		}
	}
	
	protected abstract int getArgumentPosition(SootMethod m);
}
