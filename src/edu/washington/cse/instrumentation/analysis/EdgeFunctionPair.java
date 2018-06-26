package edu.washington.cse.instrumentation.analysis;

import heros.EdgeFunction;
import soot.toolkits.scalar.Pair;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;

public class EdgeFunctionPair extends Pair<EdgeFunction<RecTreeDomain>, EdgeFunction<RecTreeDomain>> {
	public EdgeFunctionPair(EdgeFunction<RecTreeDomain> f1, EdgeFunction<RecTreeDomain> f2) {
		super(f1, f2);
	}
}
