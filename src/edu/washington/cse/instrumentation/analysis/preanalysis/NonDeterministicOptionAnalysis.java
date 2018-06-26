package edu.washington.cse.instrumentation.analysis.preanalysis;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.edgefunc.EdgeIdentity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.ArrayPackedSet;
import soot.toolkits.scalar.CollectionFlowUniverse;
import soot.toolkits.scalar.FlowSet;
import soot.toolkits.scalar.FlowUniverse;
import boomerang.accessgraph.AccessGraph;
import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.functions.LegatoEdgeFunctions;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.rectree.CallNode;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.NodeKind;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.cse.instrumentation.analysis.rectree.TreeFunction;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction.EdgeFunctionEffect;

public class NonDeterministicOptionAnalysis extends AtMostOnceProblem {
	private static final int nonDetCall = 1;
	private static final int detCall = 2;
	
	public final Set<String> combinedWithNonDet = new ConcurrentSkipListSet<>();
	
	private class PseudoNode extends CallNode {
		private final FlowSet<String> set;
		
		public PseudoNode(final int callId, final CallRole role, final FlowSet<String> set) {
			super(callId, role);
			this.set = set;
		}
		
		@Override
		public Node prime(final PrimeStorage ps) {
			return this;
		}
		
		@Override
		public Node joinWith(final Node n) {
			if(n.getKind() != NodeKind.CONST) {
				return super.joinWith(n);
			}
			assert n instanceof PseudoNode;
			final PseudoNode pn = (PseudoNode) n;
			if(pn.callId != this.callId) {
				if(pn.callId != nonDetCall) {
					combinedWithNonDet.addAll(pn.set.toList());
				}
				if(this.callId != nonDetCall) {
					combinedWithNonDet.addAll(this.set.toList());
				}
				return nondet;
			}
			if(pn.callId == nonDetCall) {
				return this;
			}
			final FlowSet<String> copy = set.clone();
			copy.union(pn.set);
			return new PseudoNode(callId, CallRole.GET, copy);
		}
		
		@Override
		public boolean equal(final Node root) {
			if(!super.equal(root)) {
				return false;
			}
			if(!(root instanceof PseudoNode)) {
				return false;
			}
			final PseudoNode pn = (PseudoNode) root;
			if(!Objects.equals(pn.set, this.set)) {
				return false;
			}
			return true;
		}
		
		@Override
		public int hashCode() {
			return super.hashCode() + (set == null ? 31 : set.hashCode() * 71);
		}
	}
	private final PseudoNode nondet = new PseudoNode(nonDetCall, CallRole.GET, null);

	
	final FlowUniverse<String> UNIVERSE;
	
	public NonDeterministicOptionAnalysis(final JimpleBasedInterproceduralCFG icfg, final ResourceResolver r, final PropagationManager pm) {
		super(AnalysisConfiguration.newBuilder(icfg)
				.withPropagationManager(pm)
				.withResourceResolver(r)
				.withSyncHavoc(false)
				.withTrackAll(true).build()
		);
		final Set<String> accessedStrings = new HashSet<>();
		for(final SootMethod accessMethods : r.getResourceAccessMethods()) {
			for(final Unit u : icfg.getCallersOf(accessMethods)) {
				final Stmt cs = (Stmt) u;
				if(resourceResolver.isResourceAccess(cs.getInvokeExpr(), cs)) {
					final Set<String> res = resourceResolver.getAccessedResources(cs.getInvokeExpr(), u);
					if(res == null) {
						continue;
					}
					accessedStrings.addAll(res);
				}
			}
		}
		UNIVERSE = new CollectionFlowUniverse<String>(accessedStrings);
	}
	
	@Override
	protected EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> createEdgeFunctionsFactory() {
		final EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> delegate = super.createEdgeFunctionsFactory();
		return new EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain>() {
			
			@Override
			public EdgeFunction<RecTreeDomain> getReturnEdgeFunction(final Unit callSite,
					final SootMethod calleeMethod, final Unit exitStmt, final AccessGraph exitNode,
					final Unit returnSite, final AccessGraph retNode) {
				return delegate.getReturnEdgeFunction(callSite, calleeMethod, exitStmt, exitNode, returnSite, retNode);
			}
			
			@Override
			public EdgeFunction<RecTreeDomain> getNormalEdgeFunction(final Unit curr,
					final AccessGraph currNode, final Unit succ, final AccessGraph succNode) {
				return delegate.getNormalEdgeFunction(curr, currNode, succ, succNode);
			}
			
			@Override
			public EdgeFunction<RecTreeDomain> getCallToReturnEdgeFunction(final Unit callSite,
					final AccessGraph callNode, final Unit returnSite, final AccessGraph returnSideNode) {
				final Stmt s = (Stmt) callSite;
				final InvokeExpr ie = s.getInvokeExpr();
				final SootMethod calledMethod = ie.getMethod();
				if(callNode == zeroValue() && resourceResolver.isResourceAccess(ie, callSite) && returnSideNode != zeroValue()) {
					final Set<String> accessedResources = resourceResolver.getAccessedResources(ie, s);
					if(accessedResources == null) {
						return new TreeFunction(new RecTreeDomain(nondet), EdgeFunctionEffect.NONE);
					} else {
						return new TreeFunction(new RecTreeDomain(new PseudoNode(detCall, CallRole.GET, getFlowSet(accessedResources))), EdgeFunctionEffect.NONE);
					}
				} else if(isUntaggedGraph(returnSideNode)) {
					return EdgeIdentity.v();
				} else if(propagationManager.isPropagationMethod(calledMethod) && ((LegatoEdgeFunctions)delegate).isPropagationFlow(s, callNode, returnSideNode)) {
					return delegate.getCallToReturnEdgeFunction(callSite, callNode, returnSite, returnSideNode);
				} else {
					return EdgeIdentity.v();
				}
			}
			
			private FlowSet<String> getFlowSet(final Set<String> accessedResources) {
				final ArrayPackedSet<String> toReturn = new ArrayPackedSet<>(UNIVERSE);
				for(final String toAdd : accessedResources) {
					toReturn.add(toAdd);
				}
				return toReturn;
			}

			@Override
			public EdgeFunction<RecTreeDomain> getCallEdgeFunction(final Unit callStmt,
					final AccessGraph srcNode, final SootMethod destinationMethod, final AccessGraph destNode) {
				return delegate.getCallEdgeFunction(callStmt, srcNode, destinationMethod, destNode);
			}
		};
	}
	
}
