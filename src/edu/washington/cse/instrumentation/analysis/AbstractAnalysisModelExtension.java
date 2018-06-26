package edu.washington.cse.instrumentation.analysis;

import java.util.Collections;
import java.util.Set;

import soot.Local;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.jimple.toolkits.callgraph.reflection.PluggableReflectionHandler;
import boomerang.mock.MockedDataFlow;

public abstract class AbstractAnalysisModelExtension implements AnalysisModelExtension {

	@Override
	public Set<SootMethod> ignoredMethods() {
		return Collections.emptySet();
	}

	@Override
	public boolean isManagedLocal(final Local l) {
		return false;
	}

	@Override
	public boolean isManagedStatic(final SootField s) {
		return false;
	}
	
	@Override
	public boolean isManagedType(final Type t) {
		return false;
	}
	
	@Override
	public boolean supportsCodeRewrite() {
		return false;
	}
	
	@Override
	public PluggableReflectionHandler getReflectionHandler() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean supportsAliasMocking() {
		return false;
	}
	
	@Override
	public void setConfig(final AnalysisConfiguration config) { }
	
	@Override
	public void setupScene(final Scene v) { }

	@Override
	public MockedDataFlow getAliasForwardMock() {
		throw new UnsupportedOperationException();
	}

	@Override
	public MockedDataFlow getAliasBackwardMock() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void postProcessScene() { }

}
