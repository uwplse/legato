package edu.washington.cse.instrumentation.analysis;

import heros.EdgeFunction;
import heros.IDETabulationProblem;
import heros.InterproceduralCFG;
import heros.edgefunc.EdgeIdentity;
import heros.solver.IDESolver;
import soot.SootMethod;
import soot.Unit;

public class PathDebuggingSolver<N extends Unit,D,M extends SootMethod,V,I extends InterproceduralCFG<N, M>> extends IDESolver<N,D,M,V,I> {

	public PathDebuggingSolver(final IDETabulationProblem<N, D, M, V, I> problem) {
		super(problem);
	}
	
	private boolean isPointlessFlow(final D src, final D dest) {
		return src.equals(zeroValue) && dest.equals(zeroValue);
	}
	
	private boolean isIdentityFunction(final EdgeFunction<V> f) {
		return (f instanceof EdgeIdentity);
	}
	
	@SuppressWarnings("unused")
	@Override
	protected void propagate(final D sourceVal, final N target, final D targetVal,
			final EdgeFunction<V> f, final N relatedCallSite, final boolean isUnbalancedReturn) {
//		System.out.println(sourceVal + " " + target + " " + targetVal + " " + relatedCallSite);
		final EdgeFunction<V> forig = jumpFn.reverseLookup(target, targetVal).get(sourceVal);
		super.propagate(sourceVal, target, targetVal, f, relatedCallSite,
				isUnbalancedReturn);
		if(false) {
			return;
		}
		if(!isPointlessFlow(sourceVal, targetVal) && !isIdentityFunction(f)) {
			final EdgeFunction<V> f_prime = jumpFn.reverseLookup(target, targetVal).get(sourceVal);
			String s = "<" + sourceVal + "> -> <" + target + "," + targetVal + ">";
			if(forig == null) {
				s += " ==> " + f_prime;
			} else {
				s += " with " + f + " \u2A05 " + forig + " ==> " + f_prime; 
			}
			s += " (in "  + icfg.getMethodOf(target).getName() + ")";
			System.out.println(s);
		}
	}
}
