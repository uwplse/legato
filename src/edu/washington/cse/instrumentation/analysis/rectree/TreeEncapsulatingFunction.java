package edu.washington.cse.instrumentation.analysis.rectree;

import heros.EdgeFunction;

public interface TreeEncapsulatingFunction extends EdgeFunction<RecTreeDomain> {
	public RecTreeDomain getTree();
}
