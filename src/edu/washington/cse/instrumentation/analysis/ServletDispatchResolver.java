package edu.washington.cse.instrumentation.analysis;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.Jimple;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.toolkits.callgraph.reflection.AbstractReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;
import soot.toolkits.scalar.Pair;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import edu.washington.cse.instrumentation.analysis.utils.JspUrlRouter;

public final class ServletDispatchResolver extends AbstractReflectionHandler {
	private final JspUrlRouter router;
	private final Map<Local, Set<String>> deferredResolution;
	
	private long allocator = 0L;

	public ServletDispatchResolver(final JspUrlRouter router, final Map<Local, Set<String>> deferredResolution) {
		this.router = router;
		this.deferredResolution = deferredResolution;
	}

	@Override
	public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
		m.unfreeze();
		final Iterator<Unit> it = m.getActiveBody().getUnits().snapshotIterator();
		final Multimap<Local, Pair<Stmt, String>> localResolution = HashMultimap.create();
		while(it.hasNext()) {
			final Unit u = it.next();
			if(!(u instanceof Stmt)) {
				continue;
			}
			final Stmt stmt = (Stmt) u;
			if(!stmt.containsInvokeExpr()) {
				continue;
			}
			final SootMethod callee = stmt.getInvokeExpr().getMethod();
			if(!callee.getSubSignature().equals("javax.servlet.RequestDispatcher getRequestDispatcher(java.lang.String)") &&
					!callee.getSubSignature().equals("javax.servlet.RequestDispatcher getNamedDispatcher(java.lang.String)")) {
				continue;
			}
			if(!(stmt instanceof AssignStmt)) {
				continue;
			}
			final AssignStmt as = (AssignStmt) stmt;
			assert as.getLeftOp() instanceof Local;
			final Local lhs = (Local) as.getLeftOp();
			if(!(as.getInvokeExpr().getArg(0) instanceof StringConstant)) {
				continue;
			}
			final String arg = ((StringConstant) stmt.getInvokeExpr().getArg(0)).value;
			final String klass;
			if(callee.getName().equals("getNamedDispatcher")) {
				klass = router.resolveNamedDispatcher(arg);
			} else {
				klass = router.resolveDispatcher(arg);
			}
			if(klass == null) {
				localResolution.put(lhs, null);
				continue;
			}
			localResolution.put(lhs, new Pair<Stmt, String>(stmt, klass));
		}
		for(final Map.Entry<Local, Collection<Pair<Stmt, String>>> kvs : localResolution.asMap().entrySet()) {
			if(kvs.getValue().contains(null)) {
				continue;
			}
			final Collection<Pair<Stmt, String>> resolutions = kvs.getValue();
			final Local lhs = kvs.getKey();
			if(resolutions.size() == 1) {
				final Pair<Stmt, String> s = resolutions.iterator().next();
				final Stmt stmt = s.getO1();
				final AssignStmt as = (AssignStmt) stmt;
				final String klass = s.getO2();
				if(!AnalysisConfiguration.VERY_QUIET) {
					System.out.println("Resolved indirect flow: " + stmt);
				}
				final String id = "_legato_internal_" + (allocator++);
				final Jimple jimple = Jimple.v();
				final Local tmp = jimple.newLocal(id, Scene.v().getType("javax.servlet.RequestDispatcher"));
				m.getActiveBody().getLocals().add(tmp);
				as.setLeftOp(tmp);
				final RefType handlerType = Scene.v().getRefType(klass);
				lhs.setType(handlerType);
				final AssignStmt downCast = jimple.newAssignStmt(lhs,
						jimple.newCastExpr(tmp, handlerType));
				if(stmt.hasTag("LineNumberTag")) {
					downCast.addAllTagsOf(stmt);
				}
				m.getActiveBody().getUnits().insertAfter(downCast, stmt);
			} else {
				final Set<String> dispatchees = new HashSet<>();
				if(!AnalysisConfiguration.VERY_QUIET) {
					System.out.println("Deferring resolution of " + lhs + " in " + m.getSignature());
				}
				for(final Pair<Stmt, String> res : resolutions) {
					dispatchees.add(res.getO2());
				}
				deferredResolution.put(lhs, dispatchees);
			}
		}
	}
}