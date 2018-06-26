package edu.washington.cse.instrumentation.analysis.interpreter;

import java.util.List;
import java.util.Map;

public interface OperationProvider<T> {
	T join(T n1, T n2);
	boolean equal(T n1, T n2);
	boolean extCommand(String[] tokens, List<T> stack, Map<String, T> varMap);
}
