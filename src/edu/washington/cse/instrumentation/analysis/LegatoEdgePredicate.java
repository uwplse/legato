package edu.washington.cse.instrumentation.analysis;

import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;

public class LegatoEdgePredicate implements EdgePredicate {
	
	private final ReflectionEdgePredicate refDelegate;
	private final SynthesizedEdgePredicate synthDelegate;

	public LegatoEdgePredicate() {
		this.synthDelegate = new SynthesizedEdgePredicate();
		this.refDelegate = new ReflectionEdgePredicate();
	}
	
	@Override
	public boolean want(final Edge e) {
		return this.refDelegate.want(e) && this.synthDelegate.want(e);
	};
}
