package edu.washington.cse.instrumentation.analysis.preanalysis;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.MethodOrMethodContext;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.NewExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.Pair;
import soot.util.queue.QueueReader;

import com.google.common.collect.HashMultimap;

import edu.washington.cse.instrumentation.analysis.LegatoEdgePredicate;

public class SingletonTypeAnalysis {
	private final HashMultimap<RefType, SootField> singletonTypeFieldMap = HashMultimap.create();
	private final Set<RefType> singletonTypes;

	public SingletonTypeAnalysis(final AtMostOnceExecutionAnalysis amoea, final JimpleBasedInterproceduralCFG icfg) {
		final LegatoEdgePredicate synth = new LegatoEdgePredicate();
		final ReachableMethods rm = new ReachableMethods(Scene.v().getCallGraph(), Scene.v().getEntryPoints().iterator(), new Filter(synth));
		rm.update();
		final Map<RefType, Unit> atMostOnceAllocations = new HashMap<>();
		final Set<RefType> multiAllocType = new HashSet<>();
		for(final QueueReader<MethodOrMethodContext> it = rm.listener(); it.hasNext(); ) {
			final SootMethod m = it.next().method();
			if(!m.hasActiveBody()) {
				continue;
			}
			for(final Unit u : m.getActiveBody().getUnits()) {
				final Stmt s = (Stmt) u;
				if(s instanceof AssignStmt && ((AssignStmt) s).getRightOp() instanceof NewExpr) {
					final NewExpr ne = (NewExpr) ((AssignStmt) s).getRightOp();
					final RefType allocated = ne.getBaseType();
					if(multiAllocType.contains(allocated)) {
						continue;
					}
					if(!amoea.isAtMostOnceUnit(u) || atMostOnceAllocations.containsKey(allocated)) {
						atMostOnceAllocations.remove(allocated);
						multiAllocType.add(allocated);
						continue;
					}
					atMostOnceAllocations.put(allocated, u);
				}
			}
		}
		outer_loop: for(final Map.Entry<RefType, Unit> kv : atMostOnceAllocations.entrySet()) {
			final AssignStmt s = (AssignStmt) kv.getValue();
			final Local startLocal = (Local) s.getLeftOp();
			final LinkedList<Pair<Set<Local>, Unit>> worklist = new LinkedList<>();
			worklist.add(new Pair<Set<Local>, Unit>(Collections.singleton(startLocal), s));
			final HashSet<Pair<Set<Local>, Unit>> visited = new HashSet<>();
			final Set<SootField> aliasedFields = new HashSet<>(); 
			while(!worklist.isEmpty()) {
				final Pair<Set<Local>, Unit> currItem = worklist.removeFirst();
				if(!visited.add(currItem)) {
					continue;
				}
				final Unit curr = currItem.getO2();
				final Set<Local> mustAliases = currItem.getO1();
				final List<Unit> succs = icfg.getSuccsOf(curr);
				if(succs.size() == 0) {
					continue outer_loop;
				}
				for(final Unit succ : succs) {
					final Stmt currStmt = (Stmt) succ;
					if(!(currStmt instanceof AssignStmt)) {
						worklist.add(new Pair<>(mustAliases, succ));
						continue;
					}
					final AssignStmt as = (AssignStmt) currStmt;
					final Value rhs = as.getRightOp();
					final Value lhs = as.getLeftOp();
					if(rhs instanceof Local && mustAliases.contains(rhs) && lhs instanceof Local) {
						final Set<Local> newAlias = new HashSet<>(mustAliases);
						newAlias.add((Local) lhs);
						worklist.add(new Pair<>(newAlias, succ));
					} else if(lhs instanceof Local && mustAliases.contains(lhs)) {
						final Set<Local> newAlias = new HashSet<>(mustAliases);
						newAlias.remove(lhs);
						if(newAlias.size() == 0) {
							continue outer_loop;
						}
					} else if(lhs instanceof StaticFieldRef && mustAliases.contains(rhs)) {
						final StaticFieldRef sfr = (StaticFieldRef) lhs;
						// success!
						aliasedFields.add(sfr.getField());
						continue;
					} else {
						worklist.add(new Pair<>(mustAliases, succ));
					}
				}
			}
			singletonTypeFieldMap.putAll(kv.getKey(), aliasedFields);
		}
		this.singletonTypes = Collections.unmodifiableSet(singletonTypeFieldMap.keySet());
	}
	
	public Set<SootField> getFieldForType(final RefType t) {
		return singletonTypeFieldMap.get(t);
	}

	public Set<RefType> getSingletonTypes() {
		return singletonTypes;
	}
}
