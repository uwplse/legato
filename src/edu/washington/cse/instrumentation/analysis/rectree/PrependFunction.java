package edu.washington.cse.instrumentation.analysis.rectree;

import heros.EdgeFunction;
import heros.edgefunc.AllBottom;
import heros.edgefunc.AllTop;
import heros.edgefunc.EdgeIdentity;

import java.util.List;

import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction;

public class PrependFunction implements TreeEncapsulatingFunction, EffectTrackingFunction {
	
	final static boolean STRICT_PREPEND = false;
	
	public final RecTreeDomain paramTree;
	final EdgeFunctionEffect effect;
	
	public PrependFunction(final Symbol s, final EdgeFunctionEffect e) {
		paramTree = RecTreeDomain.paramTree().prepend(s);
		this.effect = e;
	}
	
	public PrependFunction(final List<Symbol> s, final EdgeFunctionEffect e) {
		paramTree = RecTreeDomain.paramTree().prepend(s);
		this.effect = e;
	}
	
	public PrependFunction(final RecTreeDomain pt, final EdgeFunctionEffect e) {
		this.paramTree = pt;
		this.effect = e;
	}

	@Override
	public EdgeFunction<RecTreeDomain> composeWith(
			final EdgeFunction<RecTreeDomain> other) {
		if(other instanceof AllBottom || other instanceof AllTop) {
			throw new UnsupportedOperationException("Totally unexpected: " + other);
		} else if(other instanceof EdgeIdentity) {
			return this;
		} else if(other instanceof EffectEdgeIdentity) {
			return new PrependFunction(paramTree, effect.join(((EffectEdgeIdentity) other).getEffect()));
		} else if(other instanceof PrependFunction) {
			final PrependFunction pf = (PrependFunction)other;
			return new PrependFunction(pf.paramTree.subst(paramTree), pf.effect.join(effect));
		} else if(other instanceof TreeFunction && ((TreeFunction)other).isBottom()) {
			return other;
		} else {
			throw new IllegalArgumentException("Unsupported compose with: " + other + " " + other.getClass() + " " + this);
		}
	}

	@Override
	public RecTreeDomain computeTarget(final RecTreeDomain value) {
		return paramTree.subst(value);
	}

	@Override
	public boolean equalTo(final EdgeFunction<RecTreeDomain> other) {
		return other instanceof PrependFunction && ((PrependFunction)other).paramTree.equal(this.paramTree) && ((PrependFunction)other).getEffect() == effect;
	}
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\u03BBL.");
		paramTree.toString(sb);
		return sb.toString();
	}

	@Override
	public EdgeFunction<RecTreeDomain> joinWith(final EdgeFunction<RecTreeDomain> other) {
		if(other instanceof AllTop) {
			return this;
		}
		if(other instanceof AllBottom) {
			throw new IllegalArgumentException("Unexpected edge function: " + other + " "  + this);
		}
		final EdgeFunctionEffect joinedEffect = EdgeFunctionEffect.joinFunctionEffects(this, other);
		if(other instanceof EdgeIdentity || other instanceof EffectEdgeIdentity) {
			final RecTreeDomain j = paramTree.joinWith(RecTreeDomain.paramTree());
			if(j == RecTreeDomain.BOTTOM) {
				return new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
			} else {
				return new PrependFunction(j, joinedEffect);
			}
		}
		if(!(other instanceof PrependFunction)) {
			// now what?
			return new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
		}
		final PrependFunction pf = (PrependFunction) other;
		if(STRICT_PREPEND) {
			return pf.paramTree.equal(paramTree) ? this : new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
		}
		final RecTreeDomain j = paramTree.joinWith(pf.paramTree);
		if(j == RecTreeDomain.BOTTOM) {
			return new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
		} else {
			return new PrependFunction(j, joinedEffect);
		}
	}

	@Override
	public EdgeFunctionEffect getEffect() {
		return effect;
	}

	@Override
	public RecTreeDomain getTree() {
		return paramTree;
	}
}
