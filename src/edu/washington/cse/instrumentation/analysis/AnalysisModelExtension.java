package edu.washington.cse.instrumentation.analysis;

import java.util.Set;

import soot.Local;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.toolkits.callgraph.reflection.PluggableReflectionHandler;
import boomerang.accessgraph.AccessGraph;
import boomerang.mock.MockedDataFlow;
import edu.washington.cse.instrumentation.analysis.solver.SolverAwareFlowFunctions;

public interface AnalysisModelExtension {
	// do not analyze these methods at all
	public Set<SootMethod> ignoredMethods();
	
	// if true, this local is fake and managed by this extension
	public boolean isManagedLocal(Local l);
	
	// if true, this static field is fake and managed by this extension
	public boolean isManagedStatic(SootField s);
	
	public SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver>
		extendFunctions(SolverAwareFlowFunctions<Unit, AccessGraph, SootMethod, InconsistentReadSolver> in);
	
	// rewrite code
	public boolean supportsCodeRewrite();
	public PluggableReflectionHandler getReflectionHandler();
	
	// alias extensions
	public boolean supportsAliasMocking();
	public MockedDataFlow getAliasForwardMock();
	public MockedDataFlow getAliasBackwardMock();
	
	public void setupScene(final Scene v);
	public void setConfig(final AnalysisConfiguration config);
	public void postProcessScene();

	public boolean isManagedType(Type t);
}
