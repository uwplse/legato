package edu.washington.cse.instrumentation.analysis.functions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import boomerang.accessgraph.AccessGraph;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis;

public class CallParamDeciderProvider {
	public class CallParameterDecider {
		private final HashSet<Local> argLocals;
		private final Unit callUnit;
		private final boolean isGetter;
		private SootField accessedField;
		private boolean isSetter;

		private CallParameterDecider(final Unit callUnit) {
			this.argLocals = new HashSet<>();
			final Stmt s = (Stmt) callUnit;
			assert s.containsInvokeExpr();
			for(final Value v : s.getInvokeExpr().getArgs()) {
				if(!(v instanceof Local)) {
					continue;
				}
				final Local l = (Local) v;
				this.argLocals.add(l);
			}
			if(s.getInvokeExpr() instanceof InstanceInvokeExpr) {
				final InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
				argLocals.add((Local)iie.getBase());
			}
			this.callUnit = callUnit;
			if(icfg.getCalleesOfCallAt(callUnit).size() == 1) {
				final SootMethod soleCallee = icfg.getCalleesOfCallAt(callUnit).iterator().next();
				this.isGetter = fpa.isGetterMethod(soleCallee);
				this.isSetter = fpa.isSetterMethod(soleCallee);
				if(this.isGetter || this.isSetter) {
					this.accessedField = fpa.getRetrievedField(soleCallee);
				}
			} else {
				this.isGetter = this.isSetter = false;
			}
		}

		public boolean passesOverCall(final AccessGraph g) {
			if(g.isStatic()) {
				return !passesThroughCall(g);
			} else if(this.isSetter && g.baseAndFirstFieldMatches(getReceiverLocalUnsafe(), accessedField)) {
				return false;
			} else if(!passesThroughCall(g)) {
				return true;
			} else {
				// passes through call. But if any parameters in any callers are overwritten, we have
				// to conservatively propagate over the call
				for(final SootMethod m : icfg.getCalleesOfCallAt(callUnit)) {
					if(overwritesParam(m)) {
						return true;
					}
				}
				return false;
			}
		}
		
		public boolean passesThroughCall(final AccessGraph g) {
			if(g == zeroValue) {
				return false;
			}
			if(icfg.getCalleesOfCallAt(callUnit).size() == 0) {
				return false;
			}
			if(g.isStatic()) {
				final SootField f = g.getFirstField().getField();
				for(final SootMethod m : icfg.getCalleesOfCallAt(callUnit)) {
					if(fpa.usedStaticFields(m).contains(f)) {
						return true;
					}
				}
				return false;
			} else {
				final Local baseLocal = g.getBase();
				if(this.isGetter) {
					return argLocals.contains(baseLocal) && g.firstFieldMatches(accessedField);
				} else if(this.isSetter) {
					return argLocals.contains(baseLocal) && getReceiverLocalUnsafe() != baseLocal;
				} else {
					return argLocals.contains(baseLocal) && AtMostOnceProblem.propagateThroughCall(g);
				}
			}
		}

		private Value getReceiverLocalUnsafe() {
			return ((InstanceInvokeExpr)((Stmt)callUnit).getInvokeExpr()).getBase();
		}

		public boolean isArgument(final AccessGraph g) {
			return argLocals.contains(g.getBase());
		}
		
		public boolean isSetter() {
			return isSetter;
		}

		public boolean isGetter() {
			return isGetter;
		}
	}
	
	private final LoadingCache<Unit, CallParameterDecider> paramDeciderCache = CacheBuilder.newBuilder().maximumSize(25).build(new CacheLoader<Unit, CallParameterDecider>() {
		@Override
		public CallParameterDecider load(final Unit key) throws Exception {
			return new CallParameterDecider(key);
		}
	});
	
	private final AccessGraph zeroValue;
	private final JimpleBasedInterproceduralCFG icfg;
	private final FieldPreAnalysis fpa;
	
	public CallParamDeciderProvider(final JimpleBasedInterproceduralCFG icfg, final AccessGraph zeroValue, final FieldPreAnalysis fpa) {
		this.icfg = icfg;
		this.zeroValue = zeroValue;
		this.fpa = fpa;
	}
	
	public CallParameterDecider getUnchecked(final Unit key) {
		return paramDeciderCache.getUnchecked(key);
	}
	
	public static Set<Local> methodOverwriteSet(final SootMethod m) {
		final List<Local> pLocals = m.getActiveBody().getParameterLocals();
		final HashSet<Local> out = new HashSet<>();
		for(final Unit u : m.getActiveBody().getUnits()) {
			if(u instanceof AssignStmt && pLocals.contains(((AssignStmt)u).getLeftOp())) {
				out.add((Local) ((AssignStmt)u).getLeftOp());
			}
		}
		return out;
	}
	
	private final LoadingCache<SootMethod, Boolean> overwriteParamCache = CacheBuilder.newBuilder().build(new CacheLoader<SootMethod, Boolean>() {
		@Override
		public Boolean load(final SootMethod key) throws Exception {
			assert key.hasActiveBody();
			final Set<Local> methodOverwriteSet = methodOverwriteSet(key);
			for(final Local o : methodOverwriteSet) {
				if(AtMostOnceProblem.mayAliasType(o.getType())) {
					return true;
				}
			}
			return false;
		}
	});
	
	public boolean overwritesParam(final SootMethod m) {
		return overwriteParamCache.getUnchecked(m);
	}

}
