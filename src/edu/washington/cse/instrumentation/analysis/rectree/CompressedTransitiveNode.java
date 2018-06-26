package edu.washington.cse.instrumentation.analysis.rectree;

import java.util.Arrays;
import java.util.List;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.LabelContainer;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import gnu.trove.TIntCollection;

public class CompressedTransitiveNode extends BranchingNode {
	public final int callId;
	public final int prime;
	private final int[] compressedBranches;
	public final Node next;
	private final int cardinality;

	public CompressedTransitiveNode(final int callId, final int prime, final int branchId, final Node n, final int card) {
		this.callId = callId;
		this.prime = prime;
		this.compressedBranches = new int[]{branchId};
		this.next = n;
		this.cardinality = card;
		assert n != null;
	}
	
	public CompressedTransitiveNode(final int callId, final int prime, final int[] branches, final Node n, final int card) {
		this.callId = callId;
		this.prime = prime;
		this.compressedBranches = branches;
		this.next = n;
		this.cardinality = card;
		assert n != null;
	}

	@Override
	public NodeKind getKind() {
		return NodeKind.COMPRESSED_CALLSITE;
	}
	
	@Override
	public boolean canAnnihilate() {
		return annihilate() != null;
	}
	
	private volatile Node annihilateCache = null;
	private volatile boolean annihilateCached = false;
	
	@Override
	public Node annihilate() {
		if(annihilateCached) {
			return annihilateCache;
		}
		synchronized(this) {
			if(annihilateCached) {
				return annihilateCache;
			}
			final LabelContainer sites = AtMostOnceProblem.instance.getSitesForLabel(callId);
			annihilateCache = this.tryAnnihilate(sites.transitiveTransitiveLabels, sites.transitiveAccessLabels, sites.transitiveSynchLabels);
			annihilateCached = true;
			return annihilateCache;
		}
	}
	
	@Override
	public Node tryAnnihilate(final TIntCollection trSites, final TIntCollection accessSites, final TIntCollection transitiveSynchLabels) {
		if(!trSites.contains(callId)) {
			return this;
		}
		return next.tryAnnihilate(trSites, accessSites, transitiveSynchLabels);
	}

	@Override
	public Node joinWith(final Node n) {
		if(n.getKind() == NodeKind.CALLSITE) {
			return n.joinWith(this.dedup());
		}
		if(n.getKind() != NodeKind.COMPRESSED_CALLSITE) {
			if(this.canAnnihilate() && AnalysisConfiguration.ENABLE_NARROW) {
				return this.annihilate().joinWith(n);
			}
			return null;
		}
		final CompressedTransitiveNode tn = (CompressedTransitiveNode) n;
		if(tn.prime != this.prime || this.callId != tn.callId) {
			if((!this.canAnnihilate() && !tn.canAnnihilate()) || !AnalysisConfiguration.ENABLE_NARROW) {
				return null;
			}
			final List<Node> chain1 = this.getSingleChain();
			final List<Node> chain2 = tn.getSingleChain();
			for(int i = 0; i < chain1.size(); i++) {
				for(int j = 0; j < chain2.size(); j++) {
					if(chain1.get(i).label().equals(chain2.get(j).label())) {
						return chain1.get(i).joinWith(chain2.get(j));
					}
				}
			}
			final Node last2 = chain2.get(chain2.size() - 1);
			final Node last1 = chain1.get(chain1.size() - 1);
			return last1.joinWith(last2);
		}
		if(this.next == tn.next) {
			if(Arrays.equals(compressedBranches, tn.compressedBranches)) {
				return this;
			} else {
				return new CompressedTransitiveNode(callId, prime, mergeBranches(compressedBranches, tn.compressedBranches), next, this.cardinality);
			}
		}
		final Node nextPrime = this.next.joinWith(tn.next);
		if(nextPrime != null) {
			return new CompressedTransitiveNode(callId, prime, mergeBranches(compressedBranches, tn.compressedBranches), nextPrime, cardinality);
		} else {
			return dedupAndJoin(tn);
		}
	}
	
	private Node dedupAndJoin(final CompressedTransitiveNode tn) {
		return this.dedup().joinWith(tn.dedup());
	}
	
	public Node dedup() {
		final Node[] children = new Node[cardinality];
		for(final int i : compressedBranches) {
			children[i] = next;
		}
		return new TransitiveNode(callId, prime, children, compressedBranches.length);
	}

	private static int[] mergeBranches(final int[] b1, final int[] b2) {
		final int[] merge = new int[b1.length + b2.length];
		int i = 0, j = 0, o = 0;
		while(i < b1.length && j < b2.length) { 
			final int e1 = b1[i];
			final int e2 = b2[j];
			if(e1 == e2) {
				merge[o++] = e1;
				i++; j++;
			} else if(e1 < e2) {
				merge[o++] = e1;
				i++;
			} else {
				merge[o++] = e2;
				j++;
			}
		}
		if(i < b1.length) {
			System.arraycopy(b1, i, merge, o, b1.length - i);
			o += b1.length - i;
		}
		if(j < b2.length) {
			System.arraycopy(b2, j, merge, o, b2.length - j);
			o += b2.length - j;
		}
		final int[] v = o < merge.length ? Arrays.copyOf(merge, o) : merge;
		if(Arrays.equals(b1, v)) {
			return b1;
		} else if(Arrays.equals(b2, v)) {
			return b2;
		} else {
			return v;
		}
	}

	private volatile String labelCache = null;
	
	@Override
	public String label() {
		if(labelCache == null) {
			final StringBuilder sb = new StringBuilder();
			sb.append("{t").append(callId).append('}');
			for(int i = 0; i < prime; i++) {
				sb.append('\'');
			}
			return labelCache = sb.toString();
		} else {
			return labelCache;
		}
	}

	@Override
	public boolean equal(final Node root) {
		if(root.getKind() != NodeKind.COMPRESSED_CALLSITE) {
			return false;
		}
		final CompressedTransitiveNode ctn = (CompressedTransitiveNode) root;
		return ctn.callId == this.callId && ctn.prime == this.prime && 
				this.next.equals(ctn.next) && Arrays.equals(this.compressedBranches, ctn.compressedBranches);
	}

	@Override
	public void walk(final TreeVisitor v) {
		v.visitCompressedNode(this);
		next.walk(v);
	}

	@Override
	public Node subst(final Node child) {
		if(!this.containsAbstraction()) {
			return this;
		}
		final Node subst = next.subst(child);
		if(subst == null) {
			return null;
		}
		return new CompressedTransitiveNode(callId, prime, compressedBranches, subst, cardinality);
	}

	@Override
	public Node prime(final PrimeStorage ps) {
		if(ps.m.contains(callId) && ps.m.get(callId) == -1) {
			if(this.canAnnihilate()) {
				return this.annihilate().prime(ps);
			}
			return null;
		}
		if(!ps.m.contains(callId)) {
			return this;
		} else {
			return new CompressedTransitiveNode(callId, prime + ps.m.get(callId), compressedBranches, next, cardinality);
		}
	}

	@Override
	public void toString(final StringBuilder sb) {
		sb.append("{c").append(callId).append("}");
		boolean first = true;
		for(final int i : compressedBranches) {
			if(!first) {
				sb.append("\u208C");
			} else {
				first = false;
			}
			Node.subscriptNumber(sb, i);
		}
		for(int i = 0; i < prime; i++) {
			sb.append("'");
		}
		next.toString(sb);
	}

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		sb.append("{c").append(callId).append(",");
		boolean first = true;
		for(final int i : compressedBranches) {
			if(!first) {
				sb.append(";");
			} else {
				first = false;
			}
			sb.append(i);
		}
		sb.append("}");
		for(int i = 0; i < prime; i++) {
			sb.append("'");
		}
		next.toConcreteSyntax(sb);
	}

	@Override
	protected boolean computeContainsAbstraction() {
		return this.next.containsAbstraction();
	}

	@Override
	public Node tryNarrow() {
		if(this.canAnnihilate()) {
			final Node n = this.annihilate();
			if(n instanceof BranchingNode) {
				return ((BranchingNode) n).tryNarrow();
			}
		}
		if(next instanceof BranchingNode) {
			return new CompressedTransitiveNode(callId, prime, compressedBranches, ((BranchingNode) next).tryNarrow(), cardinality);
		}
		return this;
	}

	@Override
	public void visit(final TreeVisitor v) {
		v.visitCompressedNode(this);
	}

}
