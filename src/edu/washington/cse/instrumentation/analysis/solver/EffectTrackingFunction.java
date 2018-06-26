package edu.washington.cse.instrumentation.analysis.solver;

import heros.EdgeFunction;


public interface EffectTrackingFunction {
	public static enum EdgeFunctionEffect {
		WRITE,
		PROPAGATE,
		WRITE_AND_PROPAGATE,
		NONE;
		
		public EdgeFunctionEffect join(final EdgeFunctionEffect e) {
			if(this == NONE || e == WRITE_AND_PROPAGATE) {
				return e;
			} else if(e == this || e == NONE || this == WRITE_AND_PROPAGATE){
				return this;
			} else {
				assert e != this && (e == WRITE || e == PROPAGATE) && (this == WRITE || this == PROPAGATE) : e + " " + this;
				return WRITE_AND_PROPAGATE;
			}
		}
		
		/*
		 * XXX(jtoman): really this SHOULD be in the interface def, but we can't do that until java 8 so
		 */
		public static EdgeFunctionEffect joinFunctionEffects(final EdgeFunction<?> e1, final EdgeFunction<?> e2) {
			final boolean e1Inst = e1 instanceof EffectTrackingFunction;
			final boolean e2Inst = e2 instanceof EffectTrackingFunction;
			if(!e1Inst && !e2Inst) {
				return NONE;
			} else if(e1Inst && e2Inst) {
				return ((EffectTrackingFunction)e1).getEffect().join(((EffectTrackingFunction)e2).getEffect());
			} else if(e1Inst) {
				return ((EffectTrackingFunction)e1).getEffect();
			} else {
				assert e2Inst;
				return ((EffectTrackingFunction)e2).getEffect();
			}
		}
	}
	public EdgeFunctionEffect getEffect();
}
