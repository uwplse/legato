package edu.washington.instrumentation.analysis.test.iso;

import edu.washington.cse.instrumentation.analysis.rectree.CallNode;
import edu.washington.cse.instrumentation.analysis.rectree.CompressedTransitiveNode;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.ParamNode;
import edu.washington.cse.instrumentation.analysis.rectree.PrimingNode;
import edu.washington.cse.instrumentation.analysis.rectree.TransitiveNode;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TreeIsomorphism {
	public static class Vertex {
		int label = 0;
		TIntList orderedChildren = new TIntArrayList(10, -1);
	}
	
	private final IdentityHashMap<Node, Vertex> vMap = new IdentityHashMap<>();
	private final Map<Node, Node> parentMap;
	private final Map<Node, Set<Node>> reachableSet;
	final Node n2Symm;
	final Node n1Symm;
	private final TIntObjectMap<List<Node>> levelMap;
	
	private final int h1, h2;
	
	public TreeIsomorphism(final Node n1, final Node n2) {
		parentMap = new IdentityHashMap<>();
		reachableSet = new IdentityHashMap<>();
		this.n1Symm = breakSharing(n1);
		this.n2Symm = breakSharing(n2);
		levelMap = new TIntObjectHashMap<>(11, 0.7f, -1);
		
		
		h1 = getTreeHeight(this.n1Symm, 0);
		h2 = getTreeHeight(this.n2Symm, 0);
		if(h1 != h2) {
			return;
		}
		computeLabels();
	}
	
	public boolean isomorphicStructure() {
		return h1 == h2 && getVertex(n1Symm).label == getVertex(n2Symm).label;
	}
	
	private void computeLabels() {
		for(int i = Math.max(h1, h2) - 2; i >= 0; i--) {
			{
				final int nextLevel = i + 1;
				final List<Node> levelNodes = levelMap.get(nextLevel);
				for(int j = 0; j < levelNodes.size(); j++) {
					final Node n = levelNodes.get(j);
					final Node parentNode = parentMap.get(n);
					assert parentNode != null;
					getVertex(parentNode).orderedChildren.add(getVertex(n).label);
				}
			}
			final List<Node> thisNodes = levelMap.get(i);
			final List<Vertex> verts = new ArrayList<>(thisNodes.size());
			for(final Node n : thisNodes) {
				final Vertex v = getVertex(n);
				v.orderedChildren.sort();
				verts.add(v);
			}
			Collections.sort(verts, new Comparator<Vertex>() {
				@Override
				public int compare(final Vertex v1, final Vertex v2) {
					final TIntList o1 = v1.orderedChildren;
					final TIntList o2 = v2.orderedChildren;
					return lexiSort(o1, o2);
				}
			});
			TIntList l = null;
			int labelCounter = 0;
			for(int j = 0; j < verts.size(); j++) {
				final Vertex v = verts.get(j);
				final TIntList orderedChildren = v.orderedChildren;
				if(l == null) {
					l = orderedChildren;
				} else if(!l.equals(orderedChildren)) {
					l = orderedChildren;
					labelCounter++;
				}
				v.label = labelCounter;
			}
		}
	}

	private int getTreeHeight(final Node n, final int depth) {
		int height = 0;
		switch(n.getKind()) {
		case CONST:
			final CallNode cn = (CallNode) n;
			if(cn.next != null) {
				height = 1 + getTreeHeight(cn.next, depth + 1);
			} else {
				height = 1;
			}
			break;
		case PARAMETER:
			height = 1;
			break;
		case CALLSITE:
		{
			final TransitiveNode bn = (TransitiveNode) n;
			int max = -1;
			{
				final TIntIterator it = bn.getKeyIterator();
				while(it.hasNext()) {
					final Node n_prime = bn.transitions[it.next()];
					final int subtreeHeight = getTreeHeight(n_prime, depth + 1);
					max = Math.max(max, subtreeHeight);
				}
			}
			height = max + 1;
			break;
		}
		case IMMEDIATE_PRIME:
			height = 1;
			break;
		case COMPRESSED_CALLSITE:
			throw new RuntimeException("Impossible");
		}
		getLevelList(depth).add(n);
		return height;
	}
	
	private List<Node> getLevelList(final int l) {
		if(levelMap.containsKey(l)) {
			return levelMap.get(l);
		} else {
			final List<Node> toReturn = new ArrayList<>();
			levelMap.put(l, toReturn);
			return toReturn;
		}
	}
	
	private Node breakSharing(final Node n) {
		switch(n.getKind()) {
		case CONST:
		{
			final CallNode cn = (CallNode) n;
			final CallNode toReturn;
			Node newNext = null;
			if(cn.next != null) {
				newNext = breakSharing(cn.next);
				toReturn = new CallNode(cn.callId, cn.prime, newNext, cn.nodeRole);
			} else {
				toReturn = new CallNode(cn.callId, cn.prime, null, cn.nodeRole);
			}
			if(newNext != null) {
				final Set<Node> rs = newNodeSet();
				parentMap.put(newNext, toReturn);
				rs.add(newNext);
				if(reachableSet.containsKey(newNext)) {
					rs.addAll(reachableSet.get(newNext));
				}
				reachableSet.put(toReturn, rs);
			}
			return toReturn;
		}
		case PARAMETER:
			return new ParamNode();
		case CALLSITE:
			final TransitiveNode bn = (TransitiveNode) n;
			final Node[] tr = bn.transitions.clone();
			final TransitiveNode toReturn = new TransitiveNode(bn.callId, bn.prime, tr, bn.size);
			final Set<Node> rs = newNodeSet();
			for(int i = 0; i < tr.length; i++) {
				if(tr[i] != null) { 
					final Node child = tr[i] = breakSharing(tr[i]);
					parentMap.put(child, toReturn);
					rs.add(child);
					if(reachableSet.containsKey(child)) {
						rs.addAll(reachableSet.get(child));
					}
				}
			}
			reachableSet.put(toReturn, rs);
			return toReturn;
		case IMMEDIATE_PRIME:
		{
			final PrimingNode pNode = (PrimingNode) n;
			return new PrimingNode(pNode.ps);
		}
		case COMPRESSED_CALLSITE:
		{
			final CompressedTransitiveNode ctn = (CompressedTransitiveNode) n;
			return breakSharing(ctn.dedup());
		}
		default:
			throw new RuntimeException();
		}
	}
	
	private static Set<Node> newNodeSet() {
		return Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
	}
	
	public Vertex getVertex(final Node n) {
		if(vMap.containsKey(n)) {
			return vMap.get(n);
		} else {
			final Vertex toReturn = new Vertex();
			vMap.put(n, toReturn);
			return toReturn;
		}
	}

	public Map<Node, Set<Node>> getReachableSet() {
		return Collections.unmodifiableMap(reachableSet);
	}

	public Node getLeftNode() {
		return n1Symm;
	}

	public Node getRightNode() {
		return n2Symm;
	}

	public Collection<Node> nodesAtLevel(final int h) {
		return getLevelList(h);
	}

	public static int lexiSort(final TIntList o1, final TIntList o2) {
		final int l1 = o1.size();
		final int l2 = o2.size();
		final int bound = Math.min(l1, l2);
		int i;
		for(i = 0; i < bound; i++) {
			final int cmp = o1.get(i) - o2.get(i);
			if(cmp < 0) {
				return -1;
			} else if(cmp > 0) { 
				return 1;
			}
		}
		return l1 - l2;
	}

	public int getMaxHeight() {
		return Math.max(h1, h2);
	}
}
