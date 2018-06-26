package edu.washington.cse.instrumentation.analysis.solver;

public interface ContextResolution<T, N, M> {
	public T extendContext(T inputContext, N callSite, M destMethod);
	public T initialContext();
}
