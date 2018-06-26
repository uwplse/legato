package edu.washington.cse.instrumentation.analysis.cfg;

import java.util.HashSet;

import soot.Body;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

public class CompressedInterproceduralCFG extends JimpleBasedInterproceduralCFG {
	public static final boolean VALIDATE_GRAPHS = false;
	
	private void validateGraphs(final DirectedGraph<Unit> compressedGraph, final DirectedGraph<Unit> inputGraph) {
		assert inputGraph.size() == compressedGraph.size();
		assert new HashSet<Unit>(inputGraph.getHeads()).equals(new HashSet<Unit>(compressedGraph.getHeads()));
		assert new HashSet<Unit>(inputGraph.getTails()).equals(new HashSet<Unit>(compressedGraph.getTails()));
		
		final HashSet<Unit> giantUnits = new HashSet<>();
		final HashSet<Unit> origUnits = new HashSet<>();
		for(final Unit u : compressedGraph) {
			giantUnits.add(u);
		}
		for(final Unit u : inputGraph) {
			origUnits.add(u);
		}
		assert origUnits.equals(giantUnits);
		for(final Unit u : compressedGraph) {
			final HashSet<Unit> prev = new HashSet<>(compressedGraph.getPredsOf(u));
			assert prev.equals(new HashSet<Unit>(inputGraph.getPredsOf(u)));
			
			final HashSet<Unit> succs = new HashSet<>(compressedGraph.getSuccsOf(u));
			assert succs.equals(new HashSet<Unit>(inputGraph.getSuccsOf(u)));
		}
	}
	
	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(final Body body) {
		final DirectedGraph<Unit> toReturn = super.getOrCreateUnitGraph(body);
		assert !(toReturn instanceof ExceptionalUnitGraph) : body; 
		return toReturn;
	}
	
	@Override
	protected DirectedGraph<Unit> makeGraph(final Body body) {
		final DirectedGraph<Unit> grph = super.makeGraph(body);
		DirectedGraph<Unit> toReturn;
		if(grph.size() <= 8) {
			toReturn = (new CompactUnitGraph_byte(grph));
		} else if(grph.size() <= 16) {
			toReturn = (new CompactUnitGraph_short(grph));
		} else if(grph.size() <= 32) {
			toReturn = (new CompactUnitGraph_int(grph));
		} else if(grph.size() <= 64) {
			toReturn = (new CompactUnitGraph_long(grph));
		} else {
			toReturn = (new GiantGraph(grph));
		}
		if(VALIDATE_GRAPHS) {
			validateGraphs(toReturn, grph);
		}
		assert !(toReturn instanceof ExceptionalUnitGraph) : body;
		return toReturn;
	}
}
