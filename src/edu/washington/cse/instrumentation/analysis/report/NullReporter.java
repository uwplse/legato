package edu.washington.cse.instrumentation.analysis.report;

import heros.EdgeFunction;

import java.util.List;

import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;

public class NullReporter implements Reporter {

	@Override
	public void handleInconsistentFlow(final AccessGraph sourceVal, final Unit target,
			final AccessGraph targetVal, final EdgeFunction<RecTreeDomain> firstTree,
			final EdgeFunction<RecTreeDomain> secondTree, final boolean isPathSensitivity) { }

	@Override
	public void setIcfg(final JimpleBasedInterproceduralCFG icfg) { }

	@Override
	public void finish() { }

	@Override
	public void handleInconsistentValue(final List<Unit> context, final Unit target, final AccessGraph targetVal,
			final Table<List<Unit>, AccessGraph, RecTreeDomain> inputValues, final boolean allPredConsistent, final List<List<Unit>> dup) { }

	@Override
	public void setInitialContext(final List<Unit> list) { }

	@Override
	public void handleHeapTimeout(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) { }

	@Override
	public void handleLostStaticFlow(final List<Unit> list, final Unit unit, final AccessGraph fact, final RecTreeDomain val) { }
}
