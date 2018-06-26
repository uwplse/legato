package edu.washington.cse.instrumentation.analysis.propagation;

import java.util.Collections;
import java.util.Set;

import soot.Local;
import boomerang.accessgraph.AccessGraph;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager.PropagationTarget;

public class PropagationSpec {
	private final Set<Local> subFieldProps;
	private final Set<Local> bareLocalProps;
	private final PropagationTarget target;
	private final AccessGraph targetGraph;
	
	public PropagationSpec(final Set<Local> bareLocalProps, final Set<Local> subfieldProps, final PropagationTarget target, final AccessGraph targetGraph) {
		this.bareLocalProps = bareLocalProps;
		this.subFieldProps = subfieldProps;
		this.target = target;
		this.targetGraph = targetGraph;
	}

	public PropagationSpec(final Set<Local> bareLocalProps, final Set<Local> subfieldProps, final PropagationTarget target) {
		this(bareLocalProps, subfieldProps, target, null);
	}
	
	public PropagationSpec(final Set<Local> bareLocalProps, final PropagationTarget target) {
		this(bareLocalProps, Collections.<Local>emptySet(), target, null);
	}

	
	public AccessGraph getTargetGraph() {
		assert target == PropagationTarget.GRAPH;
		return targetGraph;
	}
	
	public Set<Local> getLocalPropagation() {
		return bareLocalProps;
	}
	
	public Set<Local> getSubFieldPropagation() {
		return subFieldProps;
	}
	
	public PropagationTarget getPropagationTarget() {
		return target;
	}

	@Override
	public String toString() {
		return "PropagationSpec [subFieldProps=" + subFieldProps
				+ ", bareLocalProps=" + bareLocalProps + ", target=" + target
				+ ", targetGraph=" + targetGraph + "]";
	}
}
