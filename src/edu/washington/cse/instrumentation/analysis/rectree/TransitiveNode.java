package edu.washington.cse.instrumentation.analysis.rectree;

import java.util.Arrays;
import java.util.List;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.LabelContainer;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.dfa.TransitiveSymbol;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import gnu.trove.TIntCollection;
import gnu.trove.iterator.TIntIterator;

public class TransitiveNode extends BranchingNode {
	public final Node[] transitions;
	public final int callId;
	public final int prime;
	public final int size;
	
	public TransitiveNode(final int callId, final int prime, final Node[] transitions, final int size) {
		this.transitions = transitions;
		this.callId = callId;
		this.prime = prime;
		this.size = size;
	}
	
	public TransitiveNode(final int cardinality, final int callId, final int prime, final int branchId, final Node branch) {
		this.callId = callId;
		this.prime = prime;
		this.transitions = new Node[cardinality];
		this.size = 1;
		this.transitions[branchId] = branch;
	}

	@Override
	public NodeKind getKind() {
		return NodeKind.CALLSITE;
	}
	
	private volatile boolean annihilateCached = false;
	private volatile Node annihilateCache = null;
	
	@Override
	public boolean canAnnihilate() {
		return annihilate() != null;
	}
	
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
	
	public Node tryAnnihilate() {
		if(canAnnihilate()) {
			return annihilate();
		}
		return this;
	}
	
	@Override
	public Node joinWith(final Node n) {
		if(n.getKind() == NodeKind.COMPRESSED_CALLSITE) {
			return this.joinWith(((CompressedTransitiveNode)n).dedup());
		}
		if(n.getKind() != NodeKind.CALLSITE) {
			if(this.canAnnihilate() && AnalysisConfiguration.ENABLE_NARROW) {
				return this.annihilate().joinWith(n);
			}
			return null;
		}
		final TransitiveNode tn = (TransitiveNode) n;
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
		return this.joinBranches(tn);
	}

	private volatile String labelCache = null;
	private volatile boolean collapseCached = false;
	private volatile Node collapsedCache = null;
	
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
		if(!(root instanceof TransitiveNode)) {
			return false;
		}
		final TransitiveNode tn = (TransitiveNode) root;
		if(tn.prime != this.prime || tn.callId != this.callId) {
			return false;
		}
		return Arrays.equals(transitions, tn.transitions);
	}

	@Override
	public void walk(final TreeVisitor v) {
		v.visitTransitionNode(this);
		walkChildren(v);
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
			return new TransitiveNode(callId, prime + ps.m.get(callId), transitions, size);
		}
	}

	public Symbol transitionSymbol(final int branchId) {
		return new TransitiveSymbol(callId, branchId, prime);
	}
	
	@Override
	public Node tryAnnihilate(final TIntCollection trSites, final TIntCollection accessSites, final TIntCollection syncSites) {
		if(!trSites.contains(callId)) {
			return this;
		}
		Node accum = null;
		for(int i = 0; i < transitions.length; i++) {
			if(transitions[i] == null) { continue; }
			final Node tr = transitions[i].tryAnnihilate(trSites, accessSites, syncSites);
			if(tr == null) {
				return null;
			}
			if(accum == null) {
				accum = tr;
			} else {
				accum = accum.joinWith(tr);
				if(accum == null) {
					return null;
				}
			}
		}
		return accum;
	}

	@Override
	public boolean computeContainsAbstraction() {
		for(int i = 0; i < transitions.length; i++) {
			if(transitions[i] == null) { continue; }
			if(transitions[i].computeContainsAbstraction()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Node subst(final Node child) {
		Node[] out = null;
		for(int i = 0; i < transitions.length; i++) {
			if(transitions[i] == null) {
				continue;
			}
			final Node pr = transitions[i].subst(child);
			if(pr == null) {
				return null;
			} else if(pr == transitions[i]) {
				continue;
			}
			if(out == null) {
				out = Arrays.copyOf(transitions, transitions.length);
			}
			out[i] = pr;
		}
		if(out == null) {
			return this;
		} else {
			return new TransitiveNode(callId, prime, out, size);
		}
	}
	
	public TIntIterator getKeyIterator() {
		return new TIntIterator() {
			int i = 0;
			int next = 0;
			{
				advance();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
			
			private void advance() {
				while(i < transitions.length) {
					if(transitions[i] == null) {
						i++;
						continue;
					} else {
						next = i;
						i++;
						return;
					}
				}
				next = -1;
			}

			@Override
			public boolean hasNext() {
				return next != -1;
			}
			
			@Override
			public int next() {
				final int n = next;
				advance();
				return n;
			}
		};
	}

	private void abstractToString(final NodeSerializer ns, final StringBuilder sb) {
		if(size == 1) {
			final int bId = getKeyIterator().next();
			final Symbol s = transitionSymbol(bId);
			ns.handleSymbol(s, sb);
			ns.handleNode(transitions[bId], sb);
		} else {
			sb.append("(");
			final TIntIterator it = getKeyIterator();
			boolean first = true;
			while(it.hasNext()) {
				final int k = it.next();
				final Symbol s = transitionSymbol(k);
				if(first) {
					first = false;
				} else{
					sb.append("|");
				}
				ns.handleSymbol(s, sb);
				ns.handleNode(transitions[k], sb);
			}
			sb.append(")");
		}
	}

	@Override
	public void toString(final StringBuilder sb) {
		abstractToString(PP, sb);
	}

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		abstractToString(CONCRETE, sb);
	}

	protected boolean canCollapse() {
		return collapse() != null; 
	}

	protected Node collapse() {
		if(collapseCached) {
			return collapsedCache;
		}
		if(size == 1) {
			for(int i = 0; i < transitions.length; i++) {
				if(transitions[i] == null) {
					continue;
				}
				collapsedCache = transitions[i];
				collapseCached = true;
				return collapsedCache;
			}
		}
		synchronized(this) {
			if(collapseCached) {
				return collapsedCache;
			}
			Node accum = null;
			for(int i = 0; i < transitions.length; i++) {
				if(transitions[i] == null) {
					continue;
				}
				if(accum == null) {
					accum = transitions[i];
				} else {
					accum = transitions[i].joinWith(accum);
					if(accum == null) {
						break;
					}
				}
			}
			collapsedCache = accum;
			collapseCached = true;
			return collapsedCache;
		}
	}

	protected void walkChildren(final TreeVisitor v) {
		for(int i = 0; i < transitions.length; i++) {
			if(transitions[i] == null) { continue; }
			transitions[i].walk(v);
		}
	}

	protected Node joinBranches(final TransitiveNode bn) {
		assert bn.getClass() == this.getClass();
		assert bn.transitions.length == this.transitions.length;
		int sz = size;
		final Node[] joined = transitions.clone();
		for(int i = 0; i < transitions.length; i++) {
			if(bn.transitions[i] != null) {
				if(joined[i] == null) {
					sz++;
					joined[i] = bn.transitions[i];
				} else {
					joined[i] = joined[i].joinWith(bn.transitions[i]);
					if(joined[i] == null) {
						return null;
					}
				}
			}
		}
		return new TransitiveNode(callId, prime, joined, sz);
	}

	@Override
	public Node tryNarrow() {
		if(this.canAnnihilate()) {
			final Node toRet = this.annihilate();
			if(toRet.getKind() == NodeKind.CALLSITE) {
				return ((TransitiveNode)toRet).tryNarrow();
			}
			return toRet;
		} else {
			final Node[] children = transitions.clone();
			for(int i = 0; i < children.length; i++) {
				if(children[i] == null) {
					continue;
				} else if(children[i] instanceof TransitiveNode) {
					children[i] = ((TransitiveNode)children[i]).tryNarrow();
				}
			}
			return new TransitiveNode(callId, prime, children, size);
		}
	}

	@Override
	public void visit(final TreeVisitor v) {
		v.visitTransitionNode(this);
	}
}
