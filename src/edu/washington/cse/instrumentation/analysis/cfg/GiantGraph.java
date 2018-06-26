package edu.washington.cse.instrumentation.analysis.cfg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import soot.Unit;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.Pair;

public class GiantGraph extends UnitGraph implements DirectedGraph<Unit> {
	private final class ExclusionIterator implements Iterator<Unit> {
		private final long[] packedExclude;
		int packedSubInd = 3;
		int packedInd = 0;
		boolean isOver;

		private ExclusionIterator(final long[] packedExclude) {
			this.packedExclude = packedExclude;
			isOver = packedInd == packedExclude.length || ((packedExclude[packedInd] >> (packedSubInd * 16)) & Short.MAX_VALUE) == 0L;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Unit next() {
			final long packed = packedExclude[packedInd];
			final int index = (int) ((packed >> (packedSubInd * 16)) & Short.MAX_VALUE);
			final Unit toReturn = units[index];
			packedSubInd--;
			if(packedSubInd == -1) {
				packedSubInd = 3;
				packedInd++;
			}
			isOver = packedInd == packedExclude.length || ((packedExclude[packedInd] >> (packedSubInd * 16)) & Short.MAX_VALUE) == 0L;
			return toReturn;
		}

		@Override
		public boolean hasNext() {
			return !isOver;
		}
	}

	private final Unit[] units;
	private final long[] packedSuccLists;
	private final long[] packedPrevList;
	private final int[] explicitPrevInd;
	private final int[] implicitExcludeInd;
	private final List<Unit>[] explicitPrev;
	private final long[][] implicitExclude;
	private final Unit[] heads, tails;
	private int[] explicitSuccInd;
	private List<Unit>[] explicitSucc; 

	@SuppressWarnings("unchecked")
	public GiantGraph(final DirectedGraph<Unit> in) {
		super(((UnitGraph)in).getBody());
		packedSuccLists = new long[in.size()];
		packedPrevList = new long[in.size()];
		final List<Pair<Integer, Unit>> implicitSuccs = new ArrayList<>();
		final List<Pair<Integer, Unit>> explicitPreds = new ArrayList<>();
		
		final List<Integer> explicitSuccs = new ArrayList<>();
		units = new Unit[in.size() + 1];
		for(final Unit u : in) {
			final int index = allocateOrFind(u);
			assert index != 0;
			final List<Unit> succs = in.getSuccsOf(u);
			if(succs.size() > 4) {
				explicitSuccs.add(index);
			} else {
				long succAccum = 0L;
				for(final Unit su : succs) {
					final int suInd = allocateOrFind(su);
					assert suInd < Short.MAX_VALUE;
					succAccum = (succAccum << 16) | suInd;
				}
				packedSuccLists[index - 1] = succAccum;
			}
			final List<Unit> preds = in.getPredsOf(u);
			if(preds.size() > 4 && preds.size() > (in.size() * 0.25)) {
				implicitSuccs.add(new Pair<>(index, u));
			} else if(preds.size() > 4) {
				explicitPreds.add(new Pair<>(index, u));
			} else {
				long predAccum = 0L;
				for(final Unit su : preds) {
					final int suInd = allocateOrFind(su);
					assert suInd < Short.MAX_VALUE;
					predAccum = (predAccum << 16) | suInd;
				}
				packedPrevList[index - 1] = predAccum;
			}
			units[index] = u;
		}
		
		{
			this.explicitSuccInd = new int[explicitSuccs.size()];
			this.explicitSucc = new List[explicitSuccs.size()];
			int it = 0;
			for(final int i : explicitSuccs) {
				explicitSuccInd[it++] = i;
			}
			Arrays.sort(explicitSuccInd);
			for(int i = 0; i < explicitSuccInd.length; i++) {
				this.explicitSucc[i] = new ArrayList<>(in.getSuccsOf(units[explicitSuccInd[i]]));
			}
		}
		
		{
			this.implicitExclude = new long[implicitSuccs.size()][];
			this.implicitExcludeInd = new int[implicitSuccs.size()];
			int it = 0;
			for(final Pair<Integer, Unit> p : implicitSuccs) {
				implicitExcludeInd[it++] = p.getO1();
			}
			Arrays.sort(implicitExcludeInd);
			for(int i = 0; i < implicitExcludeInd.length; i++) {
				final Unit bigUnit = units[implicitExcludeInd[i]];
				final List<Unit> preds = in.getPredsOf(bigUnit);
				final int numExcludedUnits = in.size() - preds.size();
				final int packSize = (int) Math.ceil(numExcludedUnits / 4.0);
				implicitExclude[i] = new long[packSize];
				final HashSet<Unit> inPrev = new HashSet<>(preds);
				long prevAccum = 0L;
				int packInd = 0;
				int numPacked = 0;
				for(int j = 1; j < units.length; j++) {
					final Unit u = units[j];
					if(inPrev.contains(u)) {
						continue;
					}
					prevAccum = (prevAccum << 16) | ((short)j);
					if((++numPacked) == 4) {
						implicitExclude[i][packInd++] = prevAccum;
						numPacked = 0;
						prevAccum = 0L;
					}
				}
				if(numPacked > 0) {
					while(numPacked < 4) {
						prevAccum = prevAccum << 16;
						numPacked++;
					}
					implicitExclude[i][packInd++] = prevAccum;
				}
			}
		}
		{
			this.explicitPrevInd = new int[explicitPreds.size()];
			this.explicitPrev = new List[explicitPreds.size()];
			
			int it = 0;
			for(final Pair<Integer, Unit> p : explicitPreds) {
				explicitPrevInd[it++] = p.getO1();
			}
			Arrays.sort(explicitPrevInd);
			for(int i = 0; i < explicitPrevInd.length; i++) {
				explicitPrev[i] = new ArrayList<>(in.getPredsOf(units[explicitPrevInd[i]]));
			}
		}
		this.heads = in.getHeads().toArray(new Unit[0]);
		this.tails = in.getTails().toArray(new Unit[0]);
	}

	@Override
	public List<Unit> getHeads() {
		return Arrays.asList(heads);
	}

	@Override
	public List<Unit> getTails() {
		return Arrays.asList(tails);
	}

	@Override
	public List<Unit> getPredsOf(final Unit s) {
		final int ind = find(s);
		final int impInd = Arrays.binarySearch(implicitExcludeInd, ind);
		final int expInd = Arrays.binarySearch(explicitPrevInd, ind);
		if(impInd < 0 && expInd < 0) {
			final long packed = packedPrevList[ind - 1];
			if(packed == 0L) {
				return Collections.emptyList();
			} else {
				final List<Unit> toReturn = new ArrayList<>();
				for(int i = 0; i < 4; i++) {
					final int packedInd = (int) ((packed >> (16 * i)) & Short.MAX_VALUE);
					if(packedInd != 0) {
						toReturn.add(units[packedInd]);
					}
				}
				return toReturn;
			}
		} else if(impInd >= 0) {
			final long[] packedExclude = implicitExclude[impInd];
			final Iterator<Unit> excludeUnits = new ExclusionIterator(packedExclude);
			assert excludeUnits.hasNext();
			Unit nextExclude = excludeUnits.next();
			final List<Unit> toReturn = new ArrayList<>();
			for(int i = 1; i < units.length; i++) {
				if(units[i] == nextExclude) {
					if(excludeUnits.hasNext()) {
						nextExclude = excludeUnits.next();
					} else {
						nextExclude = null;
					}
				} else {
					toReturn.add(units[i]);
				}
			}
			return toReturn;
		} else {
			return explicitPrev[expInd];
		}
	}

	@Override
	public List<Unit> getSuccsOf(final Unit s) {
		final int ind = find(s);
		final int explicitInd = Arrays.binarySearch(this.explicitSuccInd, ind);
		if(explicitInd >= 0) {
			return this.explicitSucc[explicitInd];
		}
		final long packed = packedSuccLists[ind - 1];
		if(packed == 0L) {
			return Collections.emptyList();
		} else {
			final List<Unit> toReturn = new ArrayList<>();
			for(int i = 0; i < 4; i++) {
				final int packedInd = (int) ((packed >> (16 * i)) & Short.MAX_VALUE);
				if(packedInd != 0) {
					toReturn.add(units[packedInd]);
				}
			}
			return toReturn;
		}
	}

	@Override
	public int size() {
		return units.length - 1;
	}
	
	private int allocateOrFind(final Unit u) {
		final int probe = (u.hashCode() % (units.length - 1)) + 1;
		int it = 0;
		while(it < units.length) {
			final int ind = ((probe + it) % (units.length - 1)) + 1;
			if(units[ind] == null) {
				units[ind] = u;
				return ind;
			} else if(units[ind] == u) {
				return ind;
			} else {
				it++;
			}
		}
		throw new RuntimeException("Out of space");
	}
	
	private int find(final Unit u) {
		final int len = units.length;
		final int probe = u.hashCode() % len;
		int it = 0;
		while(it < len) {
			final int ind = (probe + it) % len;
			if(units[ind] == u) {
				return ind;
			} else {
				it++;
			}
		}
		throw new RuntimeException("Not found");
	}

	@Override
	public Iterator<Unit> iterator() {
		return new Iterator<Unit>() {
			int ind = 1;
			
			@Override
			public boolean hasNext() {
				return ind < units.length; 
			}

			@Override
			public Unit next() {
				return units[ind++];
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
}
