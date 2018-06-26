package edu.washington.instrumentation.analysis.test.iso;

import edu.washington.cse.instrumentation.analysis.dfa.TransitiveSymbol;
import edu.washington.cse.instrumentation.analysis.rectree.CallNode;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.TransitiveNode;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.procedure.TIntIntProcedure;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TObjectIntProcedure;
import gnu.trove.procedure.TObjectProcedure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

public class LabelIsomorphism {
	private static class NodeSignature {
		int label;
		TObjectIntMap<Key> reachSymbols;
		TIntObjectMap<TIntIntMap> reachCall;
		TIntObjectMap<TIntIntMap> reachTransition;
		int paramCount;
		
		private TIntList _reachCounts;
		private List<TIntList> _callCounts;
		private List<TIntList> _transitionCounts;
		
		public NodeSignature() {
			reachSymbols = new TObjectIntHashMap<>(11, 0.9f, -1);
			reachCall = new TIntObjectHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
			reachTransition = new TIntObjectHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
		}
		
		public static NodeSignature cn(final int callId, final int prime, final NodeSignature nodeSignature) {
			final NodeSignature toReturn = new NodeSignature();
			if(!toReturn.reachCall.containsKey(callId)) {
				toReturn.reachCall.put(callId, new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1));
			}
			toReturn.reachCall.get(callId).adjustOrPutValue(prime, 1, 1);
			if(nodeSignature != null) {
				toReturn.merge(nodeSignature);	
			}
			return toReturn;
		}

		public static NodeSignature p() {
			final NodeSignature toReturn = new NodeSignature();
			toReturn.paramCount = 1;
			return toReturn;
		}

		public static NodeSignature tr(final int callId, final int prime) {
			final NodeSignature toReturn = new NodeSignature();
			toReturn.reachSymbols.put(new Key(callId, prime), 1);
			toReturn.reachTransition.put(callId, new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1));
			toReturn.reachTransition.get(callId).adjustOrPutValue(prime, 1, 1);
			return toReturn;
		}

		public void merge(final NodeSignature sn) {
			this.paramCount += sn.paramCount;
			sn.reachSymbols.forEachEntry(new TObjectIntProcedure<Key>() {
				@Override
				public boolean execute(final Key k, final int count) {
					reachSymbols.adjustOrPutValue(k, count, count);
					return true;
				}
			});
			mergeCounts(reachCall, sn.reachCall);
			mergeCounts(reachTransition, sn.reachTransition);
		}
		
		private void mergeCounts(final TIntObjectMap<TIntIntMap> thisReach, final TIntObjectMap<TIntIntMap> otherReach) {
			otherReach.forEachEntry(new TIntObjectProcedure<TIntIntMap>() {
				@Override
				public boolean execute(final int transitionId, final TIntIntMap primeCount) {
					if(!(thisReach.containsKey(transitionId))) {
						thisReach.put(transitionId, new TIntIntHashMap(primeCount));
						return true;
					}
					primeCount.forEachEntry(new TIntIntProcedure() {
						@Override
						public boolean execute(final int primee, final int count) {
							thisReach.get(transitionId).adjustOrPutValue(primee, count, count);
							return true;
						}
					});
					return true;
				}
			});
		}
		
		public List<TIntList> callCounts() {
			if(_callCounts != null) {
				return _callCounts;
			}
			final ArrayList<TIntList> toReturn = computeCounts(reachCall);
			return _callCounts = toReturn;
		}
		
		public List<TIntList> transitionCounts() {
			if(_transitionCounts != null) {
				return _transitionCounts;
			}
			return _transitionCounts = computeCounts(reachTransition);
		}

		private ArrayList<TIntList> computeCounts(final TIntObjectMap<TIntIntMap> primingMap) {
			final ArrayList<TIntList> toReturn = new ArrayList<>(primingMap.size());
			primingMap.forEachValue(new TObjectProcedure<TIntIntMap>() {
				@Override
				public boolean execute(final TIntIntMap object) {
					int max = Integer.MIN_VALUE;
					{
						final TIntIterator it = object.keySet().iterator();
						while(it.hasNext()) {
							max = Math.max(it.next(), max);
						}
					}
					final TIntList l = new TIntArrayList(max + 1);
					for(int i = 0; i <= max; i++) {
						if(object.containsKey(i)) {
							l.add(object.get(i));
						} else {
							l.add(0);
						}
					}
					toReturn.add(l);
					return true;
				}
			});
			Collections.sort(toReturn, new Comparator<TIntList>() {
				@Override
				public int compare(final TIntList o1, final TIntList o2) {
					return TreeIsomorphism.lexiSort(o1, o2);
				}
			});
			return toReturn;
		}
		
		public TIntList symbolCounts() {
			if(_reachCounts != null) {
				return _reachCounts;
			}
			final TIntList l = new TIntArrayList(reachSymbols.valueCollection());
			l.sort();
			return _reachCounts = l;
		}
	}

	private final TreeIsomorphism ti;
	private IdentityHashMap<Node, NodeSignature> nodeSig1;
	private IdentityHashMap<Node, NodeSignature> nodeSig2;
	private static Comparator<NodeSignature> SIGCMP = new Comparator<NodeSignature>() {
		@Override
		public int compare(final NodeSignature o1, final NodeSignature o2) {
			if(o1.label != o2.label) {
				return o1.label - o2.label;
			}
			final int ret = TreeIsomorphism.lexiSort(o1.symbolCounts(), o2.symbolCounts());
			if(ret != 0) {
				return ret;
			}
			final int callCountRet = nestedLexicalSort(o1.callCounts(), o2.callCounts());
			if(callCountRet != 0) {
				return callCountRet;
			}
			final int transitionCountRet = nestedLexicalSort(o1.transitionCounts(), o2.transitionCounts());
			if(transitionCountRet != 0) {
				return transitionCountRet;
			}
			return o1.paramCount - o2.paramCount;
		}
	};
	
	public LabelIsomorphism(final TreeIsomorphism ti) {
		this.ti = ti;
		computeSig(ti.getLeftNode(), (nodeSig1 = new IdentityHashMap<Node,NodeSignature>()));
		computeSig(ti.getRightNode(), (nodeSig2 = new IdentityHashMap<Node,NodeSignature>()));
	}

	private static int nestedLexicalSort(final List<TIntList> o1, final List<TIntList> o2) {
		final int l1 = o1.size();
		final int l2 = o2.size();
		final int bound = Math.min(l1, l2);
		int i;
		for(i = 0; i < bound; i++) {
			final int cmp = TreeIsomorphism.lexiSort(o1.get(i), o2.get(i));
			if(cmp < 0) {
				return -1;
			} else if(cmp > 0) { 
				return 1;
			}
		}
		return l1 - l2;
	}

	private void computeSig(final Node n, final IdentityHashMap<Node, NodeSignature> sigMap) {
		NodeSignature sig = null;
		switch(n.getKind()) {
		case CONST:
			final CallNode cn = (CallNode) n;
			if(cn.next != null) {
				computeSig(cn.next, sigMap);
			}
			sig = NodeSignature.cn(cn.callId, cn.prime, cn.next != null ? sigMap.get(cn.next) : null);
			break;
		case PARAMETER:
			sig = NodeSignature.p();
			break;
		case CALLSITE:
			{
				final TransitiveNode tn = (TransitiveNode) n;
				final NodeSignature sig2 = NodeSignature.tr(tn.callId, tn.prime);
				sig = sig2;
				{
					final TIntIterator childIt = tn.getKeyIterator();
					while(childIt.hasNext()) {
						final int branchId = childIt.next();
						final Node v = tn.transitions[branchId];
						computeSig(v, sigMap);
						final NodeSignature sn = sigMap.get(v);
						sn.reachSymbols.adjustOrPutValue(new Key(new TransitiveSymbol(tn.callId, branchId, tn.prime)), 1, 1);
						sig2.merge(sn);
					}
				}
			}
			break;
		case IMMEDIATE_PRIME:
		case COMPRESSED_CALLSITE:
			throw new IllegalArgumentException();
		}
		sig.label = ti.getVertex(n).label;
		sigMap.put(n, sig);
	}
	
	private boolean checkLabelConsistency(final int h) {
		final List<NodeSignature> sig1 = new ArrayList<>();
		final List<NodeSignature> sig2 = new ArrayList<>();
		for(final Node n : ti.nodesAtLevel(h)) {
			if(nodeSig1.containsKey(n)) {
				sig1.add(nodeSig1.get(n));
			} else {
				assert nodeSig2.containsKey(n);
				sig2.add(nodeSig2.get(n));
			}
		}
		Collections.sort(sig1, SIGCMP);
		Collections.sort(sig2, SIGCMP);
		assert sig1.size() == sig2.size();
		for(int i = 0; i < sig1.size(); i++) {
			final NodeSignature s1 = sig1.get(i);
			final NodeSignature s2 = sig2.get(i);
			if(SIGCMP.compare(s1, s2) != 0) {
				return false;
			}
		}
		if(h == ti.getMaxHeight() - 1) {
			return true;
		} else {
			return checkLabelConsistency(h+1);
		}
	}

	public boolean isomormphicLabels() {
		return checkLabelConsistency(0);
	}
}
