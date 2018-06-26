package edu.washington.cse.instrumentation.analysis;

import heros.ItemPrinter;
import soot.BriefUnitPrinter;
import soot.SootMethod;
import soot.Unit;
import soot.UnitPrinter;
import boomerang.accessgraph.AccessGraph;

final class SootItemPrinter implements ItemPrinter<Unit, AccessGraph, SootMethod> {
	@Override
	public String printNode(final Unit node, final SootMethod parentMethod) {
		final UnitPrinter up = new BriefUnitPrinter(parentMethod.getActiveBody());
		up.noIndent();
		node.toString(up);
		return up.output().toString();
	}

	@Override
	public String printFact(final AccessGraph fact) {
		return fact.toString();
	}

	@Override
	public String printMethod(final SootMethod method) {
		return method.getSignature();
	}
}
