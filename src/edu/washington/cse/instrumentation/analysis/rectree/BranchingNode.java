package edu.washington.cse.instrumentation.analysis.rectree;

import java.util.ArrayList;
import java.util.List;


public abstract class BranchingNode extends Node {
	protected static final boolean ENABLE_NARROW = Boolean.parseBoolean(System.getProperty("legato.no-narrow", "true"));
	
	public abstract boolean canAnnihilate();
	public abstract Node annihilate();
	
	public abstract Node tryNarrow();
	
	public List<Node> getSingleChain() {
		final ArrayList<Node> toReturn = new ArrayList<>();
		BranchingNode tn = this;
		while(true) {
			toReturn.add(tn);
			if(!tn.canAnnihilate()) {
				break;
			} else {
				final Node n = tn.annihilate();
				assert tn != n : tn;
				if(n instanceof BranchingNode) {
					tn = (BranchingNode) n;
				} else {
					toReturn.add(n);
					break;
				}
			}
		}
		return toReturn;
	}
}
