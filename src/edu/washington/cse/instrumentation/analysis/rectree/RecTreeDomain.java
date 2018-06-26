package edu.washington.cse.instrumentation.analysis.rectree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Function;

import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TIntIntHashMap;

public class RecTreeDomain {
	private static final String TOP_SYMBOL = "\u22A4";
	private static final String BOTTOM_SYMBOL = "\u22A5";
	public final Node restRoot;
	public final Map<String, Node> pointwiseRoots;
	public static RecTreeDomain BOTTOM = new RecTreeDomain(null) {
		@Override
		public RecTreeDomain joinWith(final RecTreeDomain other) {
			return this;
		}
		
		@Override
		public boolean equal(final RecTreeDomain other) {
			return other == this;
		}
		
		@Override
		public boolean equals(final Object other) {
			return other == this;
		};

		@Override
		public RecTreeDomain prepend(final List<Symbol> s) {
			return this;
		}
		
		@Override
		public RecTreeDomain prepend(final Symbol s) {
			return this;
		};
		
		@Override
		public RecTreeDomain subst(final RecTreeDomain paramTree) {
			return this;
		};
		
		@Override
		public void toString(final StringBuilder sb) {
			sb.append(BOTTOM_SYMBOL);
		};
		
		@Override
		public String toString() {
			return BOTTOM_SYMBOL;
		}
		
		@Override
		public void walk(final TreeVisitor v) {}
		
		@Override
		public RecTreeDomain prime(final PrimeStorage ps) {
			return this;
		};
		
		@Override
		public void serialize(final Map<String, ? super Object> roots) {
			roots.put("*", BOTTOM_SYMBOL);
		}
		
		@Override
		public int getMaxPrimes() {
			return 0;
		};
	};
	
	public static RecTreeDomain TOP = new RecTreeDomain(null) {
		@Override
		public boolean equal(final RecTreeDomain other) {
			return this == other;
		};
		
		@Override
		public RecTreeDomain joinWith(final RecTreeDomain other) {
			return other;
		};
		
		@Override
		public boolean equals(final Object other) {
			return other == this;
		};
		
		@Override
		public RecTreeDomain prepend(final List<Symbol> s) {
			return this;
		}
		
		@Override
		public RecTreeDomain prepend(final Symbol s) {
			return this;
		};
		
		@Override
		public RecTreeDomain subst(final RecTreeDomain paramTree) {
			return this;
		};
		
		@Override
		public void toString(final StringBuilder sb) {
			sb.append(TOP_SYMBOL);
		};
		
		@Override
		public String toString() {
			return TOP_SYMBOL;
		}		
		
		@Override
		public void walk(final TreeVisitor v) {}
		
		@Override
		public RecTreeDomain prime(final PrimeStorage ps) {
			return this;
		}
		@Override
		public int getMaxPrimes() {
			return 0;
		}
	};
	
	public RecTreeDomain(final Node root) {
		this.restRoot = root;
		this.pointwiseRoots = null;
		assert checkInvariants();
	}
	
	private boolean checkInvariants() {
		return restRoot != null || pointwiseRoots != null || this.getClass() != RecTreeDomain.class;
	}

	public RecTreeDomain(final Map<String, Node> pointwiseRoots, final Node r) {
		this.restRoot = r;
		this.pointwiseRoots = pointwiseRoots;
		assert checkInvariants();
	}
	
	public RecTreeDomain(final Set<String> points, final Node p) {
		this.restRoot = null;
		this.pointwiseRoots = new HashMap<>();
		for(final String key : points) {
			pointwiseRoots.put(key, p);
		}
		assert checkInvariants();
	}
	

	private static RecTreeDomain joinWithRest(final RecTreeDomain pointwiseTree,
			final RecTreeDomain allPointsTree) {
		assert !pointwiseTree.isAllPoints() && allPointsTree.isAllPoints();
		final Map<String, Node> joined = new HashMap<>();
		for(final Map.Entry<String, Node> kv : pointwiseTree.pointwiseRoots.entrySet()) {
			final Node j = joinNodes(kv.getValue(), allPointsTree.restRoot);
			if(j == null) {
				return RecTreeDomain.BOTTOM;
			}
			joined.put(kv.getKey(), j);
		}
		Node rest;
		if(pointwiseTree.restRoot == null) {
			rest = allPointsTree.restRoot;
		} else {
			rest = joinNodes(allPointsTree.restRoot, pointwiseTree.restRoot);
		}
		if(rest == null) {
			return BOTTOM;
		}
		return new RecTreeDomain(joined, rest);
	}

	private static RecTreeDomain joinFull(final RecTreeDomain t1, final RecTreeDomain t2) {
		assert !t1.isAllPoints() && !t2.isAllPoints();
		final Map<String, Node> joined = new HashMap<>();
		final Set<String> t2Rest = new HashSet<>(t2.pointwiseRoots.keySet());
		for(final Map.Entry<String, Node> kv  : t1.pointwiseRoots.entrySet()) {
			Node j;
			if(t2.pointwiseRoots.containsKey(kv.getKey())) {
				j = joinNodes(t2.pointwiseRoots.get(kv.getKey()), kv.getValue());
				t2Rest.remove(kv.getKey());
			} else if(t2.restRoot != null) {
				j = joinNodes(kv.getValue(), t2.restRoot);
			} else {
				j = kv.getValue();
			}
			if(j == null) {
				return BOTTOM;
			}
			joined.put(kv.getKey(), j);
		}
		if(t1.restRoot == null) {
			for(final String key : t2Rest) {
				joined.put(key, t2.pointwiseRoots.get(key));
			}
		} else {
			for(final String key : t2Rest) {
				final Node j = joinNodes(t2.pointwiseRoots.get(key), t1.restRoot);
				if(j == null) {
					return BOTTOM;
				}
				joined.put(key, j);
			}
		}
		Node rest;
		if(t1.restRoot != null && t2.restRoot == null) {
			rest = t1.restRoot;
		} else if(t1.restRoot == null && t2.restRoot != null) {
			rest = t2.restRoot;
		} else if(t1.restRoot != null && t2.restRoot != null) {
			rest = joinNodes(t2.restRoot, t1.restRoot);
			if(rest == null) {
				return BOTTOM;
			}
		} else {
			rest = null;
		}
		return new RecTreeDomain(joined, rest);
	}

	private boolean isAllPoints() {
		return restRoot != null && pointwiseRoots == null;
	}
	
	private static final int MAX_DEPTH = 3; 
	
	private static boolean checkDepth(final Node n, final TIntIntHashMap depthMap) {
		switch(n.getKind()) {
		case CONST:
			final CallNode cn = (CallNode) n;
			if(cn.next != null && !checkDepth(cn.next, depthMap)) {
				return false;
			} else {
				return true;
			}
		case IMMEDIATE_PRIME:
		case PARAMETER:
			return true;
		case CALLSITE:
		{
			final TransitiveNode ts = (TransitiveNode) n;
			final int tSite = ts.callId;
			final int newVal = depthMap.adjustOrPutValue(tSite, 1, 1);
			if(newVal == MAX_DEPTH) {
				return false;
			}
			for(int i = 0; i < ts.transitions.length; i++) {
				if(ts.transitions[i] == null) { continue; }
				if(!checkDepth(ts.transitions[i], depthMap)) {
					return false;
				}
			}
			assert depthMap.adjustValue(tSite, -1);
			break;
		}
		case COMPRESSED_CALLSITE:
		{
			final CompressedTransitiveNode ctn = (CompressedTransitiveNode) n;
			final int tSite = ctn.callId;
			final int newVal = depthMap.adjustOrPutValue(tSite, 1, 1);
			if(newVal == MAX_DEPTH) {
				return false;
			}
			if(!checkDepth(ctn.next, depthMap)) {
				return false;
			}
			assert depthMap.adjustValue(tSite, -1);
			break;
		}
		}
		return true;
	}
	
	public static boolean checkDepth(final Node n) {
		return checkDepth(n, new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1));
	}
	
	private static Node joinNodes(final Node n1, final Node n2) {
		final Node root_prime = n1.joinWith(n2);
		if(root_prime == null) {
			return null;
		}
		final boolean withinBound = checkDepth(root_prime, new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1));
		if(!withinBound) {
			return null;
		}
		return root_prime;
	}
	
	public RecTreeDomain joinWith(final RecTreeDomain other) {
		if(other == BOTTOM) {
			return other;
		} else if(other == TOP) {
			return this;
		}
		if(this.isAllPoints() && other.isAllPoints()) {
			final Node newRoot = joinNodes(restRoot, other.restRoot);
			if(newRoot == null) {
				return BOTTOM;
			}
			return new RecTreeDomain(newRoot);
		} else if(this.isAllPoints() && !other.isAllPoints()) {
			return joinWithRest(other, this);
		} else if(!this.isAllPoints() && other.isAllPoints()) {
			return joinWithRest(this, other);
		} else {
			return joinFull(this, other);
		}
	}
	
	public RecTreeDomain prepend(final List<Symbol> s) {
		return new RecTreeDomain(RecTreeDomain.prepend(s, restRoot));
	}
	
	public void toString(final StringBuilder sb) {
		sb.append("[");
		boolean first = true;
		if(pointwiseRoots != null) {
			for(final Map.Entry<String, Node> kv : pointwiseRoots.entrySet()) {
				if(!first) {
					sb.append(",");
				} else {
					first = false;
				}
				sb.append(kv.getKey()).append("=");
				kv.getValue().toString(sb);
			}
		}
		if(restRoot != null) {
			if(!first) {
				sb.append(",");
			}
			sb.append("*=");
			restRoot.toString(sb);
		}
		sb.append("]");
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		toString(sb);
		return sb.toString();
	}
	
	public boolean equal(final RecTreeDomain other) {
		if(other == BOTTOM || other == TOP) {
			return false;
		}
		if(!Objects.equals(restRoot, other.restRoot)) {
			return false;
		}
		return Objects.equals(pointwiseRoots, other.pointwiseRoots);
	}
	
	@Override
	public boolean equals(final Object other) {
		if(other == null) {
			return false;
		}
		if(other.getClass() != RecTreeDomain.class) {
			return false;
		}
		return this.equal((RecTreeDomain) other);
	}
	
	@Override
	public int hashCode() {
		final Map<String, Object> o = new HashMap<>();
		serializeInternal(o);
		return o.hashCode();
	}
	
	private static Node prepend(final Symbol sym, final Node n) {
		final CallSymbol cs = (CallSymbol) sym;
		assert cs.getRole() == CallRole.SYNCHRONIZATION;
		return new CallNode(cs.getCallId(), cs.getPrime(), n, cs.getRole());
	}

	public static Node prepend(final List<Symbol> s, final Node node) {
		Node newNode = node;
		for(int i = s.size() - 1; i >= 0; i--) {
			final Symbol sym = s.get(i);
			newNode = prepend(sym, newNode);
		}
		return newNode;
	}

	public RecTreeDomain prepend(final Symbol s) {
		return new RecTreeDomain(RecTreeDomain.prepend(s, restRoot));
	}
	
	private static final RecTreeDomain _paramTree = new RecTreeDomain(new ParamNode());

	public static RecTreeDomain paramTree() {
		return _paramTree;
	}
	
	public void walk(final TreeVisitor v) {
		if(restRoot != null) {
			restRoot.walk(v);
		}
		if(pointwiseRoots != null) {
			for(final Node n : pointwiseRoots.values()) {
				n.walk(v);
			}
		}
	}
	
	private RecTreeDomain map(final Function<Node, Node> m) {
		if(this == BOTTOM || this == TOP) {
			return this;
		}
		final Map<String, Node> subMap;
		boolean changed = false;
		if(this.pointwiseRoots != null) {
			subMap = new HashMap<>();
			for(final Map.Entry<String, Node> kv : pointwiseRoots.entrySet()) {
				final Node n_prime = m.apply(kv.getValue());
				if(n_prime != kv.getValue()) {
					changed = true;
				}
				if(n_prime == null) {
					return BOTTOM;
				}
				subMap.put(kv.getKey(), n_prime);
			}
		} else {
			subMap = null;
		}
		Node restRoot = null;
		if(this.restRoot != null) {
			final Node r_prime = m.apply(this.restRoot);
			if(r_prime == null) {
				return BOTTOM;
			}
			if(r_prime != restRoot) {
				changed = true;
			}
			restRoot = r_prime;
		}
		if(!changed) {
			return this;
		}
		return new RecTreeDomain(subMap, restRoot);
	}

	public RecTreeDomain subst(final RecTreeDomain paramTree) {
		if(paramTree == BOTTOM || paramTree == TOP) {
			return paramTree;
		}
		assert this.restRoot != null && this.pointwiseRoots == null;
		assert this.restRoot.containsAbstraction() : restRoot;
		Map<String, Node> subMap;
		if(paramTree.pointwiseRoots != null) {
			subMap = new HashMap<>();
			for(final Map.Entry<String, Node> kv : paramTree.pointwiseRoots.entrySet()) {
				final Node s = restRoot.subst(kv.getValue());
				if(s == null) {
					return BOTTOM;
				}
				subMap.put(kv.getKey(), s);
			}
		} else {
			subMap = null;
		}
		Node subRestRoot;
		if(paramTree.restRoot != null) {
			subRestRoot = restRoot.subst(paramTree.restRoot);
			if(subRestRoot == null) {
				return BOTTOM;
			}
		} else {
			subRestRoot = null;
		}
		assert subRestRoot != null || subMap != null;
		return new RecTreeDomain(subMap, subRestRoot);
	}
	
	public RecTreeDomain prime(final PrimeStorage ps) {
		return map(new Function<Node, Node>() {
			@Override
			public Node apply(final Node n) {
				return n.prime(ps);
			}
		});
	}

	private static Node narrowNodeForSerialization(final Node n) {
		if(n instanceof BranchingNode) {
			return ((BranchingNode) n).tryNarrow();
		}
		return n;
	}
	
	public RecTreeDomain narrowForSerialize() {
		return map(new Function<Node, Node>() {
			@Override
			public Node apply(final Node n) {
				return narrowNodeForSerialization(n);
			}
		});
	}

	public void serialize(final Map<String, ? super Object> roots) {
		this.narrowForSerialize().serializeInternal(roots);
	}

	private void serializeInternal(final Map<String, ? super Object> roots) {
		final StringBuilder sb = new StringBuilder();
		if(pointwiseRoots != null) {
			for(final Map.Entry<String, Node> kv : pointwiseRoots.entrySet()) {
				sb.setLength(0);
				kv.getValue().toConcreteSyntax(sb);
				roots.put(kv.getKey(), sb.toString());
			}
		}
		if(restRoot != null) {
			sb.setLength(0);
			restRoot.toConcreteSyntax(sb);
			roots.put("*", sb.toString());
		}
	}

	public int getMaxPrimes() {
		final int[] max = new int[]{0};
		this.walk(new AbstractTreeVisitor(){
			@Override
			public void visitCallNode(final CallNode callNode) {
				max[0] = Math.max(max[0], callNode.prime);
			}
			
			@Override
			public void visitTransitionNode(final TransitiveNode transitiveNode) {
				max[0] = Math.max(max[0], transitiveNode.prime);
			}
		});
		return max[0];
	}
}
