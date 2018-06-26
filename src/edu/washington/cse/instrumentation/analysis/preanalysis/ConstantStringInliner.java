package edu.washington.cse.instrumentation.analysis.preanalysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.Local;
import soot.Modifier;
import soot.PackManager;
import soot.PatchingChain;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.ParameterRef;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.toolkits.scalar.Pair;

public class ConstantStringInliner {
	private final Map<String, Integer> toInlineSigs;
	
	private int counter = 0;

	public ConstantStringInliner(final Collection<Pair<String, Integer>> toInline) {
		this.toInlineSigs = new HashMap<>();
		for(final Pair<String, Integer> kv : toInline) {
			final String targetSig = kv.getO1();
			toInlineSigs.put(targetSig, kv.getO2());
		}
	}
	
	public void rewriteCallSite(final SootMethod m, final Stmt callSite) {
		if(!callSite.containsInvokeExpr()) {
			return;
		}
		final String invokedSig = callSite.getInvokeExpr().getMethod().getSignature();
		if(!toInlineSigs.containsKey(invokedSig)) {
			return;
		}
		final int pos = toInlineSigs.get(invokedSig);
		final String staticStringArg = getStaticString(m, callSite, pos);
		if(staticStringArg == null) {
			return;
		}
		inlineCall(m, callSite, staticStringArg, callSite.getInvokeExpr().getMethod(), pos);
	}

	private void inlineCall(final SootMethod container, final Stmt callSite,
			final String staticStringArg, final SootMethod target, final int targetPos) {
		if(!target.isConcrete()) {
			return;
		}
		final Body b = target.retrieveActiveBody();
		// clone the body
		final List<Type> rewrittenTypes = new ArrayList<>(target.getParameterTypes());
		rewrittenTypes.remove(targetPos);
		final Type returnType = target.getReturnType();
		final List<SootClass> exceptions = target.getExceptions();
		final SootMethod inlined_method = new SootMethod(
				target.getName() + "_inline_" + (counter++),
				rewrittenTypes, returnType, target.getModifiers() | Modifier.SYNTHETIC, exceptions
		);
		final JimpleBody newBody = Jimple.v().newBody(inlined_method);
		newBody.importBodyContentsFrom(b);
		final Local overwriteParam = b.getParameterLocal(targetPos);
		final PatchingChain<Unit> units = newBody.getUnits();
		final Iterator<Unit> it = units.snapshotIterator();
		int state = 0;
		Unit lastStatement = null;
		Unit defStatement = null;
		while(it.hasNext()) {
			final Unit u = it.next();
			final boolean isParamDef = u instanceof DefinitionStmt && ((DefinitionStmt)u).getRightOp() instanceof ParameterRef;
			if(!isParamDef && state == 1) {
				if(defStatement == null) {
					throw new RuntimeException("could not find definition of inlined local");
				}
				if(lastStatement == null) {
					units.addFirst(defStatement);
				} else {
					units.insertAfter(defStatement, lastStatement);
				}
				break;
			} else if(isParamDef) {
				state = 1;
				final DefinitionStmt ds = (DefinitionStmt) u;
				if(((Local)ds.getLeftOp()).getName().equals(overwriteParam.getName())) {
					final Local overwrite = (Local) ds.getLeftOp();
					defStatement = Jimple.v().newAssignStmt(overwrite, StringConstant.v(staticStringArg));
					units.remove(u);
				} else {
					final ParameterRef pr = (ParameterRef) ds.getRightOp();
					if(pr.getIndex() > targetPos) {
						pr.setIndex(pr.getIndex() - 1);
					}
					lastStatement = u;
				}
			} else {
				assert state == 0;
				lastStatement = u;
			}
		}

		inlined_method.setActiveBody(newBody);
		target.getDeclaringClass().addMethod(inlined_method);
		PackManager.v().getTransform("jb.cp").apply(newBody);
		PackManager.v().getTransform("jb.cp-ule").apply(newBody);
		PackManager.v().getTransform("jop.cpf").apply(newBody);
		
		{
			final ValueBox ieb = callSite.getInvokeExprBox();
			final InvokeExpr ie = callSite.getInvokeExpr();
			final List<Value> arguments = new ArrayList<>(ie.getArgs());
			arguments.remove(targetPos);
			final Jimple jj = Jimple.v();
			final Local base = (Local) (ie instanceof InstanceInvokeExpr ? ((InstanceInvokeExpr) ie).getBase() : null);
			if(ie instanceof InterfaceInvokeExpr) {
				ieb.setValue(jj.newInterfaceInvokeExpr(base, inlined_method.makeRef(), arguments));
			} else if(ie instanceof VirtualInvokeExpr) {
				ieb.setValue(jj.newVirtualInvokeExpr(base, inlined_method.makeRef(), arguments));
			}	else if(ie instanceof SpecialInvokeExpr) {
				ieb.setValue(jj.newSpecialInvokeExpr(base, inlined_method.makeRef(), arguments));
			} else if(ie instanceof StaticInvokeExpr) {
				ieb.setValue(jj.newStaticInvokeExpr(inlined_method.makeRef(), arguments));
			} else {
				throw new RuntimeException();
			}
		}
	}

	public static String getStaticString(final SootMethod m, final Stmt callSite, final int pos) {
		if(!(callSite.getInvokeExpr().getArg(pos) instanceof StringConstant)) {
			return null;
		}
		final StringConstant sc = (StringConstant) callSite.getInvokeExpr().getArg(pos);
		return sc.value;
	}
}
