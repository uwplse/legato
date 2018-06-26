package edu.washington.cse.instrumentation.analysis.solver;

import java.util.List;

import soot.Unit;
import boomerang.accessgraph.AccessGraph;

public class LegatoValueNode extends ContextValueNode<List<Unit>, Unit, AccessGraph> {
	public LegatoValueNode(final List<Unit> context, final Unit target, final AccessGraph targetVal) {
		super(context, target, targetVal);
	}
}
