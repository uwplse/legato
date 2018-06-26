package edu.washington.cse.instrumentation.analysis.rectree;

import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import gnu.trove.TIntCollection;

public class CallNode extends ConcreteNode {
	public CallNode(final int callId, final CallRole role) {
		this(callId, 0, role);
	}
	
	public CallNode(final int callId, final int prime, final CallRole role) {
		this(callId, prime, null, role);
	}
	
	public CallNode(final int callId, final int prime, final Node n, final CallRole role) {
		this.callId = callId;
		this.prime = prime;
		this.nodeRole = role;
		this.next = n;
		assert nodeRole != CallRole.SYNCHRONIZATION || next != null : this;
	}

	public CallNode(final int callId, final Node branch, final CallRole callRole) {
		this(callId, 0, branch, callRole);
	}

	@Override
	public NodeKind getKind() {
		return NodeKind.CONST;
	}
	
	private volatile boolean annihilateCached = false;
	private volatile Node annihilateCache = null;
	
	private Node collapseSynchronizationChains() {
		final int targetId = this.callId, prime = this.prime;
		Node it = this;
		while(true) {
			if(it.getKind() == NodeKind.IMMEDIATE_PRIME || it.getKind() == NodeKind.PARAMETER) {
				return it;
			}
			if(it instanceof BranchingNode) {
				final BranchingNode bn = (BranchingNode) it;
				if(bn.canAnnihilate()) {
					it = bn.annihilate();
				} else {
					return bn;
				}
			} else if(it.getKind() == NodeKind.CONST) {
				final CallNode cn = (CallNode) it;
				if(cn.nodeRole != CallRole.SYNCHRONIZATION || cn.prime != prime || cn.callId != targetId) {
					return it;
				}
				it = cn.next;
			} else {
				throw new RuntimeException("Unhandled case!!! " + it + " " + it.getKind());
			}
		}
	}
	
	public Node annihilate() {
		if(annihilateCached) {
			return annihilateCache;
		}
		synchronized(this) {
			if(annihilateCached) {
				return annihilateCache;
			}
			assert nodeRole == CallRole.SYNCHRONIZATION;
			if(findMatch(next) == null) {
				annihilateCache = null;
			} else {
				CallNode it = findMatch(next);
				while(true) {
					final CallNode ret = findMatch(it.next);
					if(ret == null) {
						break;
					} else {
						it = ret;
					}
				}
				annihilateCache = it;
			}
			annihilateCached = true;
			return annihilateCache;
		}
	}
	
	private CallNode findMatch(final Node n) {
		Node it = n;
		while(!matchesSync(it)) {
			if(it instanceof BranchingNode && ((BranchingNode)it).canAnnihilate()) {
				it = ((BranchingNode)it).annihilate();
			} else {
				return null;
			}
		}
		assert matchesSync(it);
		return ((CallNode)it);
	}
	
	private boolean matchesSync(final Node n) {
		final boolean toRet = n.getKind() == NodeKind.CONST && ((CallNode)n).nodeRole == CallRole.SYNCHRONIZATION &&
				((CallNode)n).prime == this.prime && ((CallNode)n).callId == this.callId;
		return toRet;
	}
	
	@Override
	public Node joinWith(final Node n) {
		// XXX: another not so great hack
		if(n.getKind() == NodeKind.CALLSITE || n.getKind() == NodeKind.COMPRESSED_CALLSITE) {
			return n.joinWith(this);
		}
		if(n.getKind() != NodeKind.CONST) {
			return null;
		}
		final CallNode cn = (CallNode) n;
		if(cn.callId != callId || cn.prime != this.prime) {
			return null;
		}
		if((this.next == null) != (cn.next == null)) {
			return null;
		}
		if(this.next == null) {
			return this;
		}
		if(this.nodeRole != cn.nodeRole) {
			return null;
		}
		if(this.nodeRole == CallRole.SYNCHRONIZATION) {
			final Node joined = this.collapseSynchronizationChains().joinWith(cn.collapseSynchronizationChains());
			if(joined == null) {
				return null;
			}
			return new CallNode(callId, prime, joined, nodeRole);
		}
		final Node next_prime = next.joinWith(cn.next);
		if(next == next_prime) {
			return this;
		} else if(cn.next == next_prime) {
			return cn;
		} else if(next_prime == null) {
			return null;
		} else {
			return new CallNode(callId, prime, next_prime, nodeRole);
		}
	}
	
	public final Node next;
	public final CallRole nodeRole;
	public final int callId;
	public final int prime;
	
	@Override
	public String label() {
		return callLabel(callId);
	}

	@Override
	public boolean equal(final Node root) {
		if(root.getKind() != NodeKind.CONST) {
			return false;
		}
		final CallNode cn = (CallNode) root;
		if(cn.callId != this.callId) {
			return false;
		}
		if(this.nodeRole != cn.nodeRole) {
			return false;
		}
		if((this.next == null) != (cn.next == null)) {
			return false;
		}
		if(this.prime != cn.prime) {
			return false;
		}
		if(this.next != null) {
			return next.equals(cn.next);
		}
		return true;
	}

	static String callLabel(final int l) {
		return "{" + l + "}";
	}

	@Override
	public Node subst(final Node child) {
		final Node sub_child = next == null ? null : next.subst(child);
		if(sub_child == null) {
			return null;
		}
		if(sub_child == next) {
			return this;
		} else {
			return new CallNode(callId, prime, sub_child, nodeRole);
		}
	}

	@Override
	public void walk(final TreeVisitor v) {
		v.visitCallNode(this);
	}

	private void abstractToString(final NodeSerializer ns, final StringBuilder sb) {
		final CallSymbol s = new CallSymbol(callId, nodeRole);
		ns.handleSymbol(s, sb);
		if(prime != 0) {
			for(int i = 0; i < prime; i++) {
				sb.append("'");
			}
		}
		if(next != null) {
			ns.handleNode(next, sb);
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

	@Override
	protected boolean computeContainsAbstraction() {
		if(next == null) {
			return false;
		}
		return next.containsAbstraction();
	}

	@Override
	public Node prime(final PrimeStorage ps) {
		if(ps.m.contains(callId)) {
			if(ps.m.get(callId) == -1) {
				return null;
			} else {
				return new CallNode(callId, prime + ps.m.get(callId), next, nodeRole);
			}
		} else {
			return this;
		}
	}
	
	@Override
	public Node tryAnnihilate(final TIntCollection trSites, final TIntCollection accessSites, final TIntCollection transitiveSynchLabels) {
		if(nodeRole == CallRole.GET) {
			assert next == null;
			if(accessSites.contains(callId)) {
				return null;
			} else {
				return this;
			}
		} else if(nodeRole == CallRole.SYNCHRONIZATION) {
			if(transitiveSynchLabels.contains(callId)) {
				return null;
			} else {
				return this;
			}
		} else {
			return this;
		}
	}

	@Override
	public void visit(final TreeVisitor v) {
		v.visitCallNode(this);
	}
}
