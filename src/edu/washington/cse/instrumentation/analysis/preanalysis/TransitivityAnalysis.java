package edu.washington.cse.instrumentation.analysis.preanalysis;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;

public class TransitivityAnalysis {
	private static class ByValuePair {
		boolean transitiveCall = false;
		boolean accessResource = false;
		boolean accessesSync = false;
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (accessResource ? 1231 : 1237);
			result = prime * result + (accessesSync ? 1231 : 1237);
			result = prime * result + (transitiveCall ? 1231 : 1237);
			return result;
		}
		@Override
		public boolean equals(final Object obj) {
			if(this == obj) {
				return true;
			}
			if(obj == null) {
				return false;
			}
			if(getClass() != obj.getClass()) {
				return false;
			}
			final ByValuePair other = (ByValuePair) obj;
			if(accessResource != other.accessResource) {
				return false;
			}
			if(accessesSync != other.accessesSync) {
				return false;
			}
			if(transitiveCall != other.transitiveCall) {
				return false;
			}
			return true;
		}
	}
	private final BackwardFlowAnalysis<SootMethod, ByValuePair> analysis;
	public TransitivityAnalysis(final ResourceResolver rr,
		final DirectedGraph<SootMethod> directedGraph, final JimpleBasedInterproceduralCFG icfg, final SyncPreAnalysis spa) {
		this.analysis = new BackwardFlowAnalysis<SootMethod, ByValuePair>(directedGraph) {

			@Override
			protected void flowThrough(final ByValuePair in, final SootMethod d, final ByValuePair out) {
				copy(in, out);
				if(out.transitiveCall && out.accessResource && out.accessesSync) {
					return;
				}
				if(spa.getMethodSyncInfo().containsKey(d)) {
					final MethodSynchronizationInfo msi = spa.getMethodSyncInfo().get(d);
					if(msi.syncPoints != null && msi.syncPoints.size() > 0 ||
						(msi.volatileReads != null && msi.volatileReads.size() > 0) ||
						(msi.waitStmts != null && msi.waitStmts.size() > 0)) {
						out.accessesSync = true;
					}
				}
				if(!d.hasActiveBody()) {
					return;
				}
				for(final Unit callUnit : icfg.getCallsFromWithin(d)) {
					if(rr.isResourceAccess(((Stmt)callUnit).getInvokeExpr(), callUnit) && !rr.isResourceMethod(d)) {
						out.accessResource = true;
					}
					if(icfg.getCalleesOfCallAt(callUnit).size() > 1) {
						out.transitiveCall = true;
					}
					if(out.accessResource && out.transitiveCall && out.accessesSync) {
						return;
					}
				}
			}

			@Override
			protected ByValuePair newInitialFlow() {
				return new ByValuePair();
			}

			@Override
			protected void merge(final ByValuePair in1, final ByValuePair in2, final ByValuePair out) {
				out.accessResource = in1.accessResource || in2.accessResource;
				out.transitiveCall = in1.transitiveCall || in2.transitiveCall;
				out.accessesSync = in1.accessesSync || in2.accessesSync;
			}

			@Override
			protected void copy(final ByValuePair source, final ByValuePair dest) {
				dest.accessResource = source.accessResource;
				dest.transitiveCall = source.transitiveCall;
				dest.accessesSync = source.accessesSync;
			}
			
			{
				doAnalysis();
			}
		};
	}
	
	public boolean methodNeedsLabel(final SootMethod m) {
		final ByValuePair flowBefore = this.analysis.getFlowBefore(m);
		return flowBefore.accessResource || flowBefore.transitiveCall || flowBefore.accessesSync;
	}

	public boolean methodAccessesResource(final SootMethod m) {
		return this.analysis.getFlowBefore(m).accessResource;
	}
}
