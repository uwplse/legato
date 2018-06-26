package edu.washington.cse.instrumentation.analysis.preanalysis;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.logic.Loop;
import soot.jimple.toolkits.annotation.logic.LoopFinder;
import soot.jimple.toolkits.callgraph.EdgePredicate;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.StronglyConnectedComponentsFast;
import soot.util.queue.QueueReader;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.LegatoEdgePredicate;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;


public class AtMostOnceExecutionAnalysis {
	private final ResourceResolver resourceResolver;
	private final FieldPreAnalysis fpa;
	private final Collection<SootMethod> ignoredMethods;
	
	private Set<SootField> untaggedStatics;
	private Set<String> mustTrackResource;
	private Set<String> untrackedResources;
	private final JimpleBasedInterproceduralCFG icfg;
	private HashSet<SootMethod> atMostOnceMethods;

	public AtMostOnceExecutionAnalysis(final FieldPreAnalysis fpa, final Collection<SootMethod> ignoredMethods,
			final ResourceResolver resourceResolver, final JimpleBasedInterproceduralCFG icfg) {
		this.resourceResolver = resourceResolver;
		this.fpa = fpa;
		this.ignoredMethods = ignoredMethods;
		this.icfg = icfg;
		doAnalysis();
	}
	
	private final LoadingCache<SootMethod, Collection<Unit>> loopyUnits = CacheBuilder.newBuilder().build(new CacheLoader<SootMethod, Collection<Unit>>() {
		@Override
		public Collection<Unit> load(final SootMethod key) throws Exception {
			final LoopFinder lf = new LoopFinder();
			lf.transform(key.getActiveBody());
			final HashSet<Unit> toReturn = new HashSet<>();
			for(final Loop l : lf.loops()) {
				toReturn.addAll(l.getLoopStatements());
			}
			return toReturn;
		} 
		
	});
	
	private void doAnalysis() {
		final DirectedGraph<SootMethod> dcg = AtMostOnceProblem.makeDirectedCallGraph(icfg);
		final StronglyConnectedComponentsFast<SootMethod> sccs = new StronglyConnectedComponentsFast<>(dcg);
		final HashSet<SootMethod> recursive = new HashSet<>();
		final EdgePredicate synthPredicate = new LegatoEdgePredicate();
		for(final List<SootMethod> it : sccs.getTrueComponents()) {
			recursive.addAll(it);
		}
		for(final SootMethod m : dcg) {
			if(m.isStaticInitializer()) {
				continue;
			}
			final Collection<Unit> callers = icfg.getCallersOf(m);
			if(callers.size() == 1) {
				final Unit u = callers.iterator().next();
				final SootMethod caller = icfg.getMethodOf(u);
				if(loopyUnits.getUnchecked(caller).contains(u)) {
					recursive.add(m);
				}
			} else if(callers.size() > 0) {
				recursive.add(m);
			}
		}
		final ReachableMethods rm = new ReachableMethods(Scene.v().getCallGraph(), recursive.iterator(), new Filter(synthPredicate));
		rm.update();
		atMostOnceMethods = new HashSet<SootMethod>();
		for(final SootMethod m : dcg) {
			if(recursive.contains(m)) {
				continue;
			}
			atMostOnceMethods.add(m);
		}
		for(final QueueReader<MethodOrMethodContext> it = rm.listener(); it.hasNext(); ) {
			final SootMethod toRemove = it.next().method();
			atMostOnceMethods.remove(toRemove);
		}
		
		mustTrackResource = new HashSet<>();
		untrackedResources = new HashSet<>();
		final Map<String, Unit> accessSite = new HashMap<>();
		resource_search: for(final SootMethod accessMethod : resourceResolver.getResourceAccessMethods()) {
			callee_search: for(final Unit u : icfg.getCallersOf(accessMethod)) {
				final SootMethod containingMethod = icfg.getMethodOf(u);
				if(resourceResolver.getResourceAccessMethods().contains(containingMethod)) {
					continue;
				}
				if(ignoredMethods.contains(containingMethod)) {
					continue;
				}
				if(loopyUnits.getUnchecked(containingMethod).contains(u)) {
					continue callee_search;
				}
				final Stmt cs = (Stmt) u;
				if(resourceResolver.isResourceAccess(cs.getInvokeExpr(), cs)) {
					final Set<String> res = resourceResolver.getAccessedResources(cs.getInvokeExpr(), u);
					if(res == null) {
						System.out.println("Found non-deterministic option access: " + u + " in " + containingMethod + ". Optimization failed");
						untrackedResources.clear();
						break resource_search;
					}
					untrackedResources.addAll(res);
					if(!atMostOnceMethods.contains(containingMethod)) {
						mustTrackResource.addAll(res);
						continue;
					}
					for(final String r : res) {
						if(accessSite.containsKey(r)) {
							mustTrackResource.add(r);
						} else {
							accessSite.put(r, u);
						}
					}
				}
			}
		}
		
		untaggedStatics = new HashSet<>();
		for(final SootField f : fpa.writerMethods().keySet()) {
			if(atMostOnceMethods.containsAll(fpa.writerMethods().get(f))) {
				untaggedStatics.add(f);
			}
		}
		
		untrackedResources.removeAll(mustTrackResource);
		System.out.println("Not tracking: " + untrackedResources);
	}
	
	public Set<String> filterResource(final Set<String> res) {
		final HashSet<String> toReturn = new HashSet<>(res);
		toReturn.removeAll(untrackedResources);
		return toReturn;
	}
	
	public Set<SootMethod> atMostOnceMethods() {
		return Collections.unmodifiableSet(atMostOnceMethods);
	}
	
	public boolean isAtMostOnceUnit(final Unit u) {
		final SootMethod hostMethod = icfg.getMethodOf(u);
		return this.atMostOnceMethods.contains(hostMethod) && !this.loopyUnits.getUnchecked(hostMethod).contains(u);
	}

	public boolean isAtMostOnceField(final SootField field) {
		return untaggedStatics.contains(field);
	}
}
