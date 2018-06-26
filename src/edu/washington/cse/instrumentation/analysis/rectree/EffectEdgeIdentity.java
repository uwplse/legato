package edu.washington.cse.instrumentation.analysis.rectree;

import heros.EdgeFunction;
import heros.edgefunc.AllBottom;
import heros.edgefunc.AllTop;
import heros.edgefunc.EdgeIdentity;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction;

public class EffectEdgeIdentity implements EdgeFunction<RecTreeDomain>, EffectTrackingFunction {
	
	private static final EffectEdgeIdentity write = new EffectEdgeIdentity(EffectTrackingFunction.EdgeFunctionEffect.WRITE);
	private static final EffectEdgeIdentity none = new EffectEdgeIdentity(EffectTrackingFunction.EdgeFunctionEffect.NONE);
	private static final EffectEdgeIdentity propagate = new EffectEdgeIdentity(EffectTrackingFunction.EdgeFunctionEffect.PROPAGATE);
	private static final EffectEdgeIdentity propagate_and_write  = new EffectEdgeIdentity(EffectTrackingFunction.EdgeFunctionEffect.WRITE_AND_PROPAGATE);

	private final EdgeFunctionEffect effect;
	
	private EffectEdgeIdentity(final EdgeFunctionEffect effect) {
		this.effect = effect;
	}

	@Override
	public RecTreeDomain computeTarget(final RecTreeDomain source) {
		return source;
	}

	@Override
	public EdgeFunction<RecTreeDomain> composeWith(final EdgeFunction<RecTreeDomain> secondFunction) {
		if(secondFunction instanceof EffectEdgeIdentity) {
			return joinWith(secondFunction);
		} else if(secondFunction instanceof PrependFunction) {
			final PrependFunction pf = (PrependFunction) secondFunction;
			return new PrependFunction(pf.paramTree, effect.join(pf.getEffect()));
		} else if(secondFunction instanceof EffectTrackingFunction) {
			return secondFunction.composeWith(this);
		} else if(secondFunction instanceof EdgeIdentity) {
			return this;
		} else {
			System.out.println("WARNING: " + secondFunction + " " + this);
			return secondFunction;
		}
	}

	@Override
	public EdgeFunction<RecTreeDomain> joinWith(final EdgeFunction<RecTreeDomain> otherFunction) {
		if(otherFunction == this || otherFunction.equalTo(this)) return this;
		if(otherFunction instanceof EffectEdgeIdentity) {
			final EdgeFunctionEffect otherEffect = ((EffectEdgeIdentity) otherFunction).getEffect();
			final EdgeFunctionEffect join = effect.join(otherEffect);
			if(join == effect) {
				return this;
			} else if(join == otherEffect) {
				return otherFunction;
			} else {
				switch(join) {
				case NONE:
					return none;
				case PROPAGATE:
					return propagate;
				case WRITE:
					return write;
				case WRITE_AND_PROPAGATE:
					return propagate_and_write;
				}
			}
		} else if(otherFunction instanceof EdgeIdentity) {
			return this;
		}
		if(otherFunction instanceof AllBottom) {
			return otherFunction;
		}
		if(otherFunction instanceof AllTop) {
			return this;
		}
		//do not know how to join; hence ask other function to decide on this
		return otherFunction.joinWith(this);
	}
	
	@Override
	public boolean equalTo(final EdgeFunction<RecTreeDomain> other) {
		//singleton
		return other==this;
	}

	@Override
	public String toString() {
		return "id[" + effect + "]";
	}

	public static EffectEdgeIdentity write() {
		return write;
	}
	
	public static EffectEdgeIdentity id() {
		return none;
	}
	
	public static EffectEdgeIdentity propagate() {
		return propagate;
	}
	
	public static EffectEdgeIdentity propagate_and_write() {
		return propagate_and_write;
	}

	@Override
	public EdgeFunctionEffect getEffect() {
		return effect;
	}

}
