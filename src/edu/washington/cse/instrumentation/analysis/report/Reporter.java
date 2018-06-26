package edu.washington.cse.instrumentation.analysis.report;

import heros.EdgeFunction;

import java.util.List;

import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;

public interface Reporter {
	void handleInconsistentValue(List<Unit> context, Unit target, AccessGraph targetVal, Table<List<Unit>, AccessGraph, RecTreeDomain> inputValues,
			boolean allPredConsistent, List<List<Unit>> dupContexts);
	void handleInconsistentFlow(AccessGraph sourceVal, Unit target, AccessGraph targetVal, EdgeFunction<RecTreeDomain> firstTree,
			EdgeFunction<RecTreeDomain> secondTree, boolean allConsistent);
	void setIcfg(JimpleBasedInterproceduralCFG icfg);
	void setInitialContext(List<Unit> list);
	void finish();
	
	void handleHeapTimeout(List<Unit> context, Unit unit, AccessGraph fact, RecTreeDomain val);
	void handleLostStaticFlow(List<Unit> list, Unit unit, AccessGraph fact, RecTreeDomain val);
}
