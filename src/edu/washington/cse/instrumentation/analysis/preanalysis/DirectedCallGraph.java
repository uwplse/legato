package edu.washington.cse.instrumentation.analysis.preanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.G;
import soot.Kind;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.util.HashMultiMap;
import soot.util.MultiMap;

/*
 * Copied from Soot's DirectedCallGraph implementation. Adds generics
 * for sanity.
 */
public class DirectedCallGraph implements DirectedGraph<SootMethod> {
	protected Set<SootMethod> nodes;
	protected Map<SootMethod, List<SootMethod>> succ;
	protected Map<SootMethod, List<SootMethod>> pred;
	protected List<SootMethod> heads;
	protected List<SootMethod> tails;
	protected int size;

	public DirectedCallGraph(final JimpleBasedInterproceduralCFG icfg, final EdgePredicate filter,
			final Iterator<SootMethod> heads) {
		// filter heads by filter
		final List<SootMethod> filteredHeads = new ArrayList<SootMethod>();
		while(heads.hasNext()) {
			final SootMethod m = heads.next();
			if(m.isConcrete()) {
				filteredHeads.add(m);
			}
		}

		this.nodes = new HashSet<SootMethod>(filteredHeads);

		final MultiMap<SootMethod, SootMethod> s = new HashMultiMap<>();
		final MultiMap<SootMethod, SootMethod> p = new HashMultiMap<>();

		// simple breadth-first visit
		Set<SootMethod> remain = new HashSet<SootMethod>(filteredHeads);
		while(!remain.isEmpty()) {
			final Set<SootMethod> newRemain = new HashSet<SootMethod>();
			final Iterator<SootMethod> it = remain.iterator();
			while(it.hasNext()) {
				final SootMethod m = it.next();
				final Iterator<Edge> itt = edgesOutOf(m, icfg);
				while(itt.hasNext()) {
					final Edge edge = itt.next();
					if(!filter.want(edge)) {
						continue;
					}
					final SootMethod mm = edge.tgt();
					final boolean keep = mm.isConcrete();
					if(keep) {
						if(this.nodes.add(mm))
							newRemain.add(mm);
						s.put(m, mm);
						p.put(mm, m);
					}
				}
			}
			remain = newRemain;
		}

		// MultiMap -> Map of List
		this.succ = new HashMap<>();
		this.pred = new HashMap<>();
		this.tails = new ArrayList<>();
		this.heads = new ArrayList<>();
		final Iterator<SootMethod> it = this.nodes.iterator();
		while(it.hasNext()) {
			final SootMethod x = it.next();
			final Set<SootMethod> ss = s.get(x);
			final Set<SootMethod> pp = p.get(x);
			this.succ.put(x, new ArrayList<>(ss));
			this.pred.put(x, new ArrayList<>(pp));
			if(ss.isEmpty())
				this.tails.add(x);
			if(pp.isEmpty())
				this.heads.add(x);
		}

		this.size = this.nodes.size();
	}
	
	private Iterator<Edge> edgesOutOf(final SootMethod m, final JimpleBasedInterproceduralCFG icfg) {
		final Set<Unit> callsFromWithin = icfg.getCallsFromWithin(m);
		return new Iterator<Edge>() {
			final Iterator<Unit> callUnitIt = callsFromWithin.iterator();
			Iterator<SootMethod> callTargetIt = null;
			Edge next = null;
			private Unit caller;
			{
				findNext();
			}
			@Override
			public boolean hasNext() {
				return next != null;
			}

			private void findNext() {
				while(callTargetIt == null || !callTargetIt.hasNext()) {
					if(!callUnitIt.hasNext()) {
						next = null;
						return;
					}
					caller = callUnitIt.next();
					callTargetIt = icfg.getCalleesOfCallAt(caller).iterator();
				}
				final SootMethod nextTarget = callTargetIt.next();
				next = new Edge(m, caller, nextTarget, Kind.VIRTUAL);
			}

			@Override
			public Edge next() {
				final Edge toRet = next;
				findNext();
				return toRet;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	@Override
	public List<SootMethod> getHeads() {
		return heads;
	}

	@Override
	public List<SootMethod> getTails() {
		return tails;
	}

	@Override
	public Iterator<SootMethod> iterator() {
		return nodes.iterator();
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public List<SootMethod> getSuccsOf(final SootMethod s) {
		return succ.get(s);
	}

	@Override
	public List<SootMethod> getPredsOf(final SootMethod s) {
		return pred.get(s);
	}

	public void printGraph() {
  	for (final SootMethod node : this ) {
	    G.v().out.println("Node = "+node);
	    G.v().out.println("Preds:");
	    for (final SootMethod p : getPredsOf(node)) {
				G.v().out.print("     ");
				G.v().out.println(p);
	    }
	    G.v().out.println("Succs:");
	    for (final SootMethod s : getSuccsOf(node)) {
				G.v().out.print("     ");
				G.v().out.println(s);
	    }
		}
	}
}
