package edu.washington.cse.instrumentation.analysis;

import java.util.HashSet;
import java.util.Set;

import soot.SootMethod;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

public class ReflectionEdgePredicate implements EdgePredicate {
	public static final String[] REFLECTION_SIGS = new String[]{
		"<java.lang.Class: java.lang.Object newInstance()>",
		"<java.lang.Class: java.lang.Class forName(java.lang.String)>",
		"<java.lang.reflect.Method: java.lang.Object invoke(java.lang.Object,java.lang.Object[])>",
		"<java.lang.reflect.Constructor: java.lang.Object invoke(java.lang.Object[])>",
	};
	
	private final Set<String> toIgnore = new HashSet<>();
	public ReflectionEdgePredicate() {
		for(final String sig : REFLECTION_SIGS) {
			toIgnore.add(sig);
		}
	}
	
	@Override
	public boolean want(final Edge e) {
		final SootMethod m = e.getTgt().method();
		return !toIgnore.contains(m.getSignature());
	}
}
