package edu.washington.cse.instrumentation.analysis.solver;


public class ContextValueNode<C, N,D> {

	private final C context;
	private final D targetVal;
	private final N target;

	public ContextValueNode(final C context, final N target,
			final D targetVal) {
		this.context = context;
		this.target = target;
		this.targetVal = targetVal;
	}

	public N getTarget() {
		return target;
	}

	public C context() {
		return context;
	}

	public D factAtTarget() {
		return targetVal;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((context == null) ? 0 : context.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		result = prime * result + ((targetVal == null) ? 0 : targetVal.hashCode());
		return result;
	}

	@Override
	public String toString() {
		return "[" + targetVal + "@<" + target + "," + context + ">]";
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(final Object obj) {
		if(this == obj) {
			return true;
		}
		if(obj == null) {
			return false;
		}
		if(getClass() != obj.getClass()) {
			return false;
		}
		final ContextValueNode other = (ContextValueNode) obj;
		if(context == null) {
			if(other.context != null) {
				return false;
			}
		} else if(!context.equals(other.context)) {
			return false;
		}
		if(target == null) {
			if(other.target != null) {
				return false;
			}
		} else if(!target.equals(other.target)) {
			return false;
		}
		if(targetVal == null) {
			if(other.targetVal != null) {
				return false;
			}
		} else if(!targetVal.equals(other.targetVal)) {
			return false;
		}
		return true;
	}
	
}
