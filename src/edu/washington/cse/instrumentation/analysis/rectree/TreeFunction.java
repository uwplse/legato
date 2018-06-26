package edu.washington.cse.instrumentation.analysis.rectree;

import heros.EdgeFunction;
import heros.edgefunc.AllTop;
import heros.edgefunc.EdgeIdentity;
import edu.washington.cse.instrumentation.analysis.solver.EffectTrackingFunction;

public class TreeFunction implements TreeEncapsulatingFunction, EffectTrackingFunction {
	public final RecTreeDomain tree;
	private final EdgeFunctionEffect effect;

	public TreeFunction(final RecTreeDomain tree, final EdgeFunctionEffect effect) {
		this.tree = tree;
		this.effect = effect;
	}
	
	@Override
	public EdgeFunction<RecTreeDomain> composeWith(final EdgeFunction<RecTreeDomain> other) {
		if(other instanceof EdgeIdentity) {
			return this;
		} else if(other instanceof EffectEdgeIdentity) {
			final EdgeFunctionEffect je = effect.join(((EffectEdgeIdentity) other).getEffect());
			if(je != effect) {
				return new TreeFunction(tree, je);
			} else {
				return this;
			}
		} else if(other instanceof PrependFunction) {
			final EdgeFunctionEffect composedEffect = EdgeFunctionEffect.joinFunctionEffects(other, this);
			final RecTreeDomain paramTree = ((PrependFunction) other).paramTree;
			return new TreeFunction(paramTree.subst(tree), composedEffect);
		} else {
			// this may or may not ever happen?
			return new TreeFunction(RecTreeDomain.BOTTOM, effect);
		}
	}

	@Override
	public RecTreeDomain computeTarget(final RecTreeDomain ignored) {
		return tree;
	}

	@Override
	public boolean equalTo(final EdgeFunction<RecTreeDomain> other) {
		if(other instanceof TreeFunction) {
			return ((TreeFunction) other).tree.equal(this.tree) && ((TreeFunction)other).getEffect() == effect;
		} else {
			return false;
		}
	}

	@Override
	public EdgeFunction<RecTreeDomain> joinWith(final EdgeFunction<RecTreeDomain> other) {
		if(other instanceof AllTop) {
			return this;
		}
		final EdgeFunctionEffect joinedEffect = EdgeFunctionEffect.joinFunctionEffects(this, other);
		if(!(other instanceof TreeFunction)) {
			if(joinedEffect == EdgeFunctionEffect.NONE) {
				return bottomTree();
			}
			return new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
		}
		final TreeFunction treeOther = (TreeFunction) other;
		if(treeOther.isBottom() || this.isBottom()) {
			return new TreeFunction(RecTreeDomain.BOTTOM, joinedEffect);
		}
		final RecTreeDomain joined = this.tree.joinWith(treeOther.tree);
		return new TreeFunction(joined, joinedEffect);
	}
	
	@Override
	public String toString() {
		return "\u03BB_." + tree.toString();
	}
	
	public static TreeFunction bottomTree() {
		return new TreeFunction(RecTreeDomain.BOTTOM, EdgeFunctionEffect.NONE);
	}

	public boolean isBottom() {
		return this.tree == RecTreeDomain.BOTTOM;
	}

	@Override
	public EdgeFunctionEffect getEffect() {
		return effect;
	}

	@Override
	public RecTreeDomain getTree() {
		return tree;
	}

}
