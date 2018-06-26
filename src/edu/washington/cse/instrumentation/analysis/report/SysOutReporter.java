package edu.washington.cse.instrumentation.analysis.report;

import heros.EdgeFunction;
import heros.solver.PathEdge;

import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;

public class SysOutReporter implements Reporter {
	private JimpleBasedInterproceduralCFG icfg;
	private List<Unit> initialContext;
	public SysOutReporter() { }

	@Override
	public void handleInconsistentFlow(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal, 
			final EdgeFunction<RecTreeDomain> firstTree, final EdgeFunction<RecTreeDomain> secondTree, final boolean pathSensitivity) {
		System.out.println("Inconsistent" + (pathSensitivity ? " (path insensitive)" : "") + 
				" flow discovered for edge " + new PathEdge<Unit, AccessGraph>(sourceVal, target, targetVal) + " in " +
				icfg.getMethodOf(target) + " with values: " + firstTree + " and " + secondTree);
	}
	
	@Override
	public void handleInconsistentValue(final List<Unit> context, final Unit u, final AccessGraph ag,
			final Table<List<Unit>, AccessGraph, RecTreeDomain> joinedValues, final boolean isPath, final List<List<Unit>> dupContexts) {
		System.out.println("input facts: " + joinedValues.columnKeySet());
		final SootMethod m = icfg.getMethodOf(u);
		String contextString;
		contextString = getContextString(context);
		System.out.println("Inconsistent values discovered at: " + u + " for fact " + ag + " in method " + m 
				+ " (inputs: " + joinedValues.values() + ") from context: " + contextString + " PS: " + isPath);
		if(dupContexts.size() > 0) {
			System.out.println("This error also occurs under contexts:");
			for(final List<Unit> dup : dupContexts) {
				System.out.println("+ " + getContextString(dup));
			}
		}
	}

	private String getContextString(final List<Unit> context) {
		String contextString;
		if(context == initialContext) {
			contextString = "[INITIAL]";
		} else {
			contextString = context.toString();//"<" + context + "," + icfg.getMethodOf(context).toString() + ">"; TODO fix me
		}
		return contextString;
	}

	@Override
	public void setIcfg(final JimpleBasedInterproceduralCFG icfg) {
		this.icfg = icfg;
	}

	@Override
	public void finish() { }

	@Override
	public void setInitialContext(final List<Unit> u) {
		this.initialContext = u;
	}

	@Override
	public void handleHeapTimeout(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) {
		final SootMethod m = icfg.getMethodOf(unit);
		System.out.println("Configuration value lost into the heap through: " + fact + " at " + unit + " in method " + m + " with value " + val
				+ " in context " + getContextString(context));
	}
	
	@Override
	public void handleLostStaticFlow(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) {
		final SootMethod m = icfg.getMethodOf(unit);
		System.out.println("Configuration flows into static field: " + fact + " at " + unit + " in method " + m + " with value " + val
				+ " in context " + getContextString(context));
	}
}
