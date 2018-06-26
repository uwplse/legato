package edu.washington.instrumentation.analysis.test.iso;

import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;

public class Isomorphism {
	public static boolean isomorphic(final RecTreeDomain d1, final RecTreeDomain d2) {
		return computeTreeIsomorphism(d1.restRoot, d2.restRoot);
	}
	
	public static boolean isomorphic(final Node n1, final Node n2) {
		return computeTreeIsomorphism(n1, n2);
	}
	
	
	private static boolean computeTreeIsomorphism(final Node d1, final Node d2) {
		final TreeIsomorphism t = new TreeIsomorphism(d1, d2);
		if(!t.isomorphicStructure()) {
			return false;
		}
		final LabelIsomorphism l = new LabelIsomorphism(t);
		return l.isomormphicLabels();
	}
}
