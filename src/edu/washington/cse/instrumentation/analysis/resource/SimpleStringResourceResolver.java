package edu.washington.cse.instrumentation.analysis.resource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import soot.Scene;
import soot.SootMethod;

public class SimpleStringResourceResolver extends PointsToMethodResourceResolver {
	private final String accessMethod;
	private final int keyParam;

	public SimpleStringResourceResolver(final String methodSignature) {
		final String[] tokens = methodSignature.split(";", 2);
		assert tokens.length == 2 : Arrays.toString(tokens) + " " + methodSignature;
		this.keyParam = Integer.parseInt(tokens[0]);
		this.accessMethod = tokens[1];
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return m.getSignature().equals(accessMethod);
	}

	@Override
	protected int getArgumentPosition(final SootMethod m) {
		assert m.getSignature().equals(accessMethod);
		return keyParam;
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		return Collections.singleton(Scene.v().getMethod(accessMethod));
	}

}
