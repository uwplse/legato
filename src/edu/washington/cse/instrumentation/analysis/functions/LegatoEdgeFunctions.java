package edu.washington.cse.instrumentation.analysis.functions;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.edgefunc.EdgeIdentity;
import heros.solver.Pair;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.Modifier;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.FlowSet;
import boomerang.AliasFinder;
import boomerang.accessgraph.AccessGraph;
import boomerang.accessgraph.WrappedSootField;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.SummaryMode;
import edu.washington.cse.instrumentation.analysis.EverythingIsInconsistentException;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.functions.CallParamDeciderProvider.CallParameterDecider;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import edu.washington.cse.instrumentation.analysis.preanalysis.AtMostOnceExecutionAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.MethodSynchronizationInfo;
import edu.washington.cse.instrumentation.analysis.preanalysis.SyncPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.TransitivityAnalysis;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationSpec;
import edu.washington.cse.instrumentation.analysis.rectree.CallNode;
import edu.washington.cse.instrumentation.analysis.rectree.CompressedTransitiveNode;
import edu.washington.cse.instrumentation.analysis.rectree.EffectEdgeIdentity;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.ParamNode;
import edu.washington.cse.instrumentation.analysis.rectree.PrependFunction;
import edu.washington.cse.instrumentation.analysis.rectree.PrimingNode;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.cse.instrumentation.analysis.rectree.TreeFunction;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction.EdgeFunctionEffect;
import edu.washington.cse.instrumentation.analysis.utils.MultiTable;

public class LegatoEdgeFunctions extends AbstractPathFunctions implements EdgeFunctions<Unit, AccessGraph, SootMethod, RecTreeDomain> {
	private final SummaryMode summaryStrategy;
	private final CallParamDeciderProvider paramDeciderCache;
	
	private final Map<SootMethod, MethodSynchronizationInfo> methodSyncInfo ;
	private final Map<Unit, Set<Object>> havocGraphs;
	private final Set<Unit> monitorEnterPoints;
	
	private final WaitCallDecider waitDecider = new WaitCallDecider();
	private final AtMostOnceExecutionAnalysis atmoFields;
	private final boolean enableSyncHavoc;
	private final MultiTable<SootMethod, Unit, AccessGraph> warnedMethods = new MultiTable<>();
	private final TransitivityAnalysis transitivityAnalysis;

	public LegatoEdgeFunctions(final AnalysisConfiguration conf,
			final AccessGraph zeroValue,
			final SyncPreAnalysis spa,
			final FieldPreAnalysis fpa,
			final Table<Unit, AccessGraph, Set<AccessGraph>> synchReadLookup, final CallParamDeciderProvider cpdp,
			final AtMostOnceExecutionAnalysis atmoFields,
			final TransitivityAnalysis ta) {
		super(conf, zeroValue, spa, fpa, synchReadLookup, cpdp);
		this.summaryStrategy = conf.summaryMode;
		this.paramDeciderCache = cpdp;
		this.atmoFields = atmoFields;
		
		methodSyncInfo = spa.getMethodSyncInfo();
		havocGraphs = spa.getHavocGraphs();
		monitorEnterPoints = spa.getMonitorEnterPoints();
		this.transitivityAnalysis = ta;
		this.enableSyncHavoc = conf.syncHavoc;
	}

	@Override
	public EdgeFunction<RecTreeDomain> getReturnEdgeFunction(final Unit callSite,
			final SootMethod calleeMethod, final Unit exitStmt, final AccessGraph exitNode, final Unit returnSite,
			final AccessGraph retNode) {
		if(exitNode == zeroValue() && retNode == zeroValue()) {
			return EdgeIdentity.v();
		}
		// there is no such thing as "multiple calls" to a static initializer
		if(calleeMethod.isStaticInitializer()) {
			return EffectEdgeIdentity.id();
		}
		if(isUntaggedGraph(retNode)) {
			return EffectEdgeIdentity.id();
		}
		resourceAccessSites.add(callSite);
		final List<SootMethod> sortedCallees = sortedCalleeList(callSite);
		if(sortedCallees.size() == 1 && !this.transitivityAnalysis.methodNeedsLabel(sortedCallees.get(0))) {
			return EffectEdgeIdentity.id();
		}
		final int cs = getUnitNumber(callSite);
		final ParamNode pn = new ParamNode();
		final Node n;
		if(AnalysisConfiguration.FLOW_SENSITIVE_TRANSITIVITY) {
			final int branchId = sortedCallees.indexOf(calleeMethod);
			n = new CompressedTransitiveNode(cs, 0, branchId, pn, sortedCallees.size());
		} else {
			n = new CompressedTransitiveNode(cs, 0, 0, pn, 1);
		}
		return new PrependFunction(new RecTreeDomain(n), EdgeFunctionEffect.NONE);
	}

	private AccessGraph zeroValue() {
		return zeroValue;
	}

	@Override
	public EdgeFunction<RecTreeDomain> getNormalEdgeFunction(final Unit curr,
			final AccessGraph currNode, final Unit succ, final AccessGraph succNode) {
		if(succNode == zeroValue()) {
			return EdgeIdentity.v();
		}
		boolean isSynchReadFlow = isSynchedUnit(curr);
		final boolean isVolatileRead = isVolatileRead(curr);
		if(isSynchReadFlow) {
			synchronized(synchReadLookup) {
				isSynchReadFlow = synchReadLookup.contains(curr, currNode) && synchReadLookup.get(curr, currNode).contains(succNode) && !isUntaggedGraph(currNode);
			}
		}
		assert !isVolatileRead || !isSynchReadFlow;
		if(!isSynchReadFlow && !isVolatileRead) {
			if(isFieldWriteFlow(curr, currNode, succNode)) {
				return EffectEdgeIdentity.write();
			} else if(monitorEnterPoints.contains(curr)) {
				return new PrependFunction(new RecTreeDomain(new PrimingNode(new PrimeStorage(getUnitNumber(curr)))), EdgeFunctionEffect.NONE);
			} else {
				return EffectEdgeIdentity.id();
			}
		} else if(isVolatileRead) {
			if(isFieldReadFlow(curr, currNode, succNode)) {
				return new PrependFunction(new CallSymbol(getUnitNumber(curr), 0, CallRole.SYNCHRONIZATION), EdgeFunctionEffect.NONE);
			} else {
				return new PrependFunction(new RecTreeDomain(new PrimingNode(new PrimeStorage(getUnitNumber(curr)))), EdgeFunctionEffect.NONE);
			}
		} else {
			return new PrependFunction(new CallSymbol(getSynchMarker(curr), CallRole.SYNCHRONIZATION), EdgeFunctionEffect.WRITE);
		}
	}
	
	protected boolean isVolatileRead(final Unit curr) {
		if(!this.enableSyncHavoc) {
			return false;
		}
		if(!(curr instanceof AssignStmt)) { 
			return false;
		}
		final AssignStmt as = (AssignStmt) curr;
		if(!(as.getRightOp() instanceof FieldRef)) {
			return false;
		}
		final FieldRef fr = (FieldRef) as.getRightOp();
		return Modifier.isVolatile(fr.getField().getModifiers());
	}
	@Override
	public EdgeFunction<RecTreeDomain> getCallToReturnEdgeFunction(
			final Unit callSite, final AccessGraph callNode, final Unit returnSite, final AccessGraph returnSideNode) {
		if(callNode == zeroValue() && returnSideNode == zeroValue()) {
			return EdgeIdentity.v();
		}
		final Stmt callStmt = (Stmt) callSite;
		final InvokeExpr ie = callStmt.getInvokeExpr();
		final SootMethod sm = ie.getMethod();
		final boolean isPhantom = isPhantomMethodCall(callSite, sm);
		if(resourceResolver.isResourceAccess(ie, callSite) && callNode == zeroValue()) {
			final Set<String> accessedResources = resourceResolver.getAccessedResources(ie, callStmt);
			final CallNode node = new CallNode(getUnitNumber(callSite), 0, CallRole.GET);
			resourceAccessSites.add(callSite);
			if(accessedResources == null) {
				return new TreeFunction(new RecTreeDomain(node), EdgeFunctionEffect.NONE);
			} else {
				return new TreeFunction(new RecTreeDomain(accessedResources, node), EdgeFunctionEffect.NONE); 
			}
		} else if(propagationManager.isPropagationMethod(sm)) {
			final PropagationSpec ps = propagationManager.getPropagationSpec(callSite);
			assert ps != null;
			if(ps.getPropagationTarget().isContainerAbstraction()) {
				if(!isPropagationFlow(callStmt, callNode, returnSideNode)) {
					return EffectEdgeIdentity.id();
				}
				if(callStmt instanceof AssignStmt && returnSideNode.baseMatches(((AssignStmt)callStmt).getLeftOp())) {
					return EffectEdgeIdentity.id();
				}
				return EffectEdgeIdentity.write();
			} else if(isPropagationFlow(callStmt, callNode, returnSideNode)) {
				return EffectEdgeIdentity.propagate();
			} else {
				return EffectEdgeIdentity.id();
			}
		} else if(waitDecider.isWaitCall(sm)) {
			final SootMethod containingMethod = interproceduralCFG().getMethodOf(callSite);
			if(methodSyncInfo.containsKey(containingMethod) && 
					methodSyncInfo.get(containingMethod).failStmts != null && 
					methodSyncInfo.get(containingMethod).failStmts.contains(callStmt) && 
					(callNode != zeroValue() || returnSideNode != zeroValue())) {
				throw new EverythingIsInconsistentException();
			}
			final Set<Object> readHavoc = havocGraphs.get(callSite);
			assert readHavoc != null;
			boolean needsHavoc = false;
			if(returnSideNode.isStatic()) {
				needsHavoc = readHavoc.contains(returnSideNode.getFirstField().getField());
			} else if(readHavoc.contains(returnSideNode.getBase())) {
				needsHavoc = true;
			} else if(returnSideNode.getFieldCount() > 0 && readHavoc.contains(new Pair<Local, SootField>(returnSideNode.getBase(), returnSideNode.getFirstField().getField()))) {
				needsHavoc = true;
			}
			final RecTreeDomain r;
			final int callId = getUnitNumber(callSite);
			final EdgeFunctionEffect effect;
			if(needsHavoc) {
				r = new RecTreeDomain(new CallNode(callId, 0, new PrimingNode(new PrimeStorage(callId)), CallRole.SYNCHRONIZATION));
				effect = EdgeFunctionEffect.PROPAGATE;
			} else {
				r = new RecTreeDomain(new PrimingNode(new PrimeStorage(callId)));
				effect = EdgeFunctionEffect.NONE;
			}
			return new PrependFunction(r, effect);
		} else if(isPhantom && summaryStrategy != SummaryMode.IGNORE && !propagationManager.isIdentityMethod(sm)) {
			final CallParameterDecider cpd = paramDeciderCache.getUnchecked(callStmt);
			final boolean isArgument = cpd.isArgument(callNode);
			if(!isArgument || isNullaryMethod(sm)) {
				return EffectEdgeIdentity.id();
			}
			if(summaryStrategy == SummaryMode.BOTTOM) {
				return TreeFunction.bottomTree();
			} else if(summaryStrategy == SummaryMode.FAIL) {
				throw new EverythingIsInconsistentException();
			} else if(summaryStrategy == SummaryMode.WARN) {
				synchronized(warnedMethods) {
					warnedMethods.put(sm, callSite, callNode);
				}
				return EffectEdgeIdentity.id();
			} else {
				throw new RuntimeException("Unexpected case: " + callSite + " in (" + interproceduralCFG().getMethodOf(callStmt) + ") "
						+ callNode + " -> " + returnSideNode + " " + summaryStrategy);
			}
		} else if(isUntaggedGraph(returnSideNode)) {
			return EdgeIdentity.v();
		} else {
			if(resourceResolver.isResourceAccess(ie, callSite)) {
				return getPrimingMethod(callSite);
			}
			if(icfg.getCalleesOfCallAt(callSite).size() == 0 || (icfg.getCalleesOfCallAt(callSite).size() == 1 && !transitivityAnalysis.methodNeedsLabel(sm))) {
				return EdgeIdentity.v();
			}
			return getPrimingMethod(callSite);
		}
	}


	private JimpleBasedInterproceduralCFG interproceduralCFG() {
		return icfg;
	}

	private EdgeFunction<RecTreeDomain> getPrimingMethod(final Unit callSite) {
		final PrimeStorage ps = new PrimeStorage(getUnitNumber(callSite));
		return new PrependFunction(new RecTreeDomain(new PrimingNode(ps)), EdgeFunctionEffect.NONE);
	}
	
	public boolean isPropagationFlow(final Stmt callStmt, final AccessGraph callNode,
			final AccessGraph returnSideNode) {
		final SootMethod sm = callStmt.getInvokeExpr().getMethod();
		if(!propagationManager.isPropagationMethod(sm)) {
			return false;
		}
		final PropagationSpec propagationSpec = propagationManager.getPropagationSpec(callStmt);
		if(propagationSpec == null) {
			return false;
		}
		if(callNode.isStatic()) {
			return false;
		}
		if(propagationSpec.getPropagationTarget().isContainerAbstraction()) {
			return propagationSpec.getLocalPropagation().contains(callNode.getBase()) && !returnSideNode.equals(callNode);
		} else {
			if(!propagationSpec.getSubFieldPropagation().contains(callNode.getBase()) &&
					(callNode.getFieldCount() != 0 || !propagationSpec.getLocalPropagation().contains(callNode.getBase()))) {
				return false;
			}
			// assume any non-identity flows here are part of propagation
			return !callNode.equals(returnSideNode);
		}
	}

	private boolean isNullaryMethod(final SootMethod m) {
		return m.getReturnType() instanceof VoidType && m.getParameterCount() == 0; 
	}
	
	private int getUnitNumber(final Unit callSite) {
		return AtMostOnceProblem.getUnitNumber(callSite);
	}

	private int getSynchMarker(final Unit callStmt) {
		return spa.getUnitToSynchMarker().get(callStmt);
	}
	
	protected boolean isUntaggedGraph(final AccessGraph ag) {
		if(!ag.isStatic()) {
			return false;
		}
		if(ag.getFieldCount() > 1) {
			return false;
		}
		return atmoFields.isAtMostOnceField(ag.getFirstField().getField());
	}
	
	protected boolean isFieldWriteFlow(final Unit curr, final AccessGraph currNode, final AccessGraph succNode) {
		if(curr instanceof DefinitionStmt) {
			final DefinitionStmt ds = (DefinitionStmt) curr;
			if(!(ds.getLeftOp() instanceof InstanceFieldRef) && !(ds.getLeftOp() instanceof ArrayRef)) {
				return false;
			}
			if(succNode.getFieldCount() == 0 || (succNode.isStatic() && succNode.getFieldCount() == 1)) {
				return false;
			}
			final SootField writtenField = ds.containsArrayRef() ? AliasFinder.ARRAY_FIELD : ds.getFieldRef().getField();
			if(!(ds.getRightOp() instanceof Local)) {
				return false;
			}
			final Local rhs = (Local) ds.getRightOp();
			return currNode.baseMatches(rhs) && succNode.getFirstField().getField().equals(writtenField);
		}
		return false;
	}
	
	protected boolean isFieldReadFlow(final Unit curr, final AccessGraph currNode, final AccessGraph succNode) {
		assert curr instanceof AssignStmt;
		final AssignStmt as = (AssignStmt) curr;
		final Value lhs = as.getLeftOp();
		final Value rhs = as.getRightOp();
		assert lhs instanceof Local && rhs instanceof FieldRef : lhs + " " + rhs;
		if(!succNode.baseMatches(lhs)) {
			return false;
		}
		if(rhs instanceof InstanceFieldRef) {
			final InstanceFieldRef ifr = (InstanceFieldRef) rhs;
			return !currNode.isStatic() && currNode.baseAndFirstFieldMatches(ifr.getBase(), ifr.getField());
		} else if(rhs instanceof StaticFieldRef) {
			final StaticFieldRef sfr = (StaticFieldRef) rhs;
			return currNode.isStatic() && currNode.firstFieldMatches(sfr.getField());
		}
		return false;
	}

	@Override
	public EdgeFunction<RecTreeDomain> getCallEdgeFunction(final Unit callStmt,
			final AccessGraph srcNode, final SootMethod destinationMethod, final AccessGraph destNode) {
		if(srcNode == zeroValue() && destNode == zeroValue()) {
			return EdgeIdentity.v();
		}
		if(isUntaggedGraph(destNode)) {
			return EdgeIdentity.v();
		}
		final List<Symbol> toPrepend = new ArrayList<>();
		EdgeFunctionEffect effect = EdgeFunctionEffect.NONE;
		if(isSynchedUnit(callStmt) && srcNode.getFieldCount() > 0 && hasSyncWriteField(callStmt, srcNode) && fieldGraphUsed(srcNode, destinationMethod)) {
			toPrepend.add(new CallSymbol(getSynchMarker(callStmt), CallRole.SYNCHRONIZATION));
			effect = EdgeFunctionEffect.NONE;
		}
//		toPrepend.add(0, getParamPhiForMethod(callStmt, destinationMethod));
		return new PrependFunction(toPrepend, effect);
	}


	private boolean fieldGraphUsed(final AccessGraph srcNode, final SootMethod destinationMethod) {
		final FlowSet<SootField> used = this.fpa.usedInstanceFields(destinationMethod);
		for(final WrappedSootField f : srcNode.getRepresentative()) {
			if(used.contains(f.getField())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasSyncWriteField(final Unit u, final AccessGraph srcNode) {
		for(final WrappedSootField wsf : srcNode.getRepresentative()) {
			if(isSyncedFieldAt(u, wsf.getField())) {
				return true;
			}
		}
		return false;
	}


	public Set<Unit> resourceAccessSites = new HashSet<Unit>();
	
	private final Comparator<SootMethod> SIGNATURE_COMP = new Comparator<SootMethod>() {
		@Override
		public int compare(final SootMethod o1, final SootMethod o2) {
			return o1.getSignature().compareTo(o2.getSignature());
		}
	};
	
	private final LoadingCache<Unit, List<SootMethod>> sortedCalleeCache = CacheBuilder.newBuilder().build(new CacheLoader<Unit, List<SootMethod>>() {
		@Override
		public List<SootMethod> load(final Unit key) throws Exception {
			final ArrayList<SootMethod> toRet = new ArrayList<>(interproceduralCFG().getCalleesOfCallAt(key));
			Collections.sort(toRet, SIGNATURE_COMP);
			return toRet;
		}
		
	});
	
	private List<SootMethod> sortedCalleeList(final Unit callSite) {
		return sortedCalleeCache.getUnchecked(callSite);
	}
	
	public void printWarnings(final PrintStream out) {
		if(warnedMethods.isEmpty()) {
			return;
		}
		out.println("WARNING: Unsummarized stub functions encountered! The analysis results may be imprecise/unsound");
		for(final Map.Entry<SootMethod, Map<Unit, Set<AccessGraph>>> row : warnedMethods.rowMap().entrySet()) {
			out.print(">>> Call to ");
			out.print(row.getKey());
			out.println(" in contexts: ");
			for(final Map.Entry<Unit, Set<AccessGraph>> kv : row.getValue().entrySet()) {
				out.print("  + Call ");
				out.print(kv.getKey());
				out.print(" in method ");
				out.print(interproceduralCFG().getMethodOf(kv.getKey()));
				out.println(". Argument graphs: ");
				for(final AccessGraph ag : kv.getValue()) {
					out.print("    - ");
					out.println(ag);
				}
			}
		}
	}

	public void dumpResourceSites(final PrintWriter pw) {
		for(final Unit u : resourceAccessSites) {
			pw.println(u + " in " + interproceduralCFG().getMethodOf(u) + " -> " + getUnitNumber(u));
		}
	}
}
