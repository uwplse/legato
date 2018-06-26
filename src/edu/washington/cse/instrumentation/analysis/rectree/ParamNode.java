package edu.washington.cse.instrumentation.analysis.rectree;

import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;

public class ParamNode extends Node {

	public static final char PARAM_SYMBOL = 'L';
	public static final String PARAM_STRING = PARAM_SYMBOL + "";

	@Override
	public NodeKind getKind() {
		return NodeKind.PARAMETER;
	}

	@Override
	public Node joinWith(final Node n) {
		if(n.getKind() == NodeKind.CALLSITE || n.getKind() == NodeKind.COMPRESSED_CALLSITE) {
			return n.joinWith(this);
		}
		if(n.getKind() == NodeKind.PARAMETER) {
			return this;
		} else if(n.getKind() == NodeKind.IMMEDIATE_PRIME) {
			return n;
		} else {
			return null;
		}
	}

	@Override
	public String label() {
		return PARAM_STRING;
	}

	@Override
	public boolean equal(final Node root) {
		return root.getKind() == NodeKind.PARAMETER;
	}

	@Override
	public void toString(final StringBuilder sb) {
		sb.append("L");
	}

	@Override
	public Node subst(final Node child) {
		return child;
	}

	@Override
	public void walk(final TreeVisitor v) {
		v.visitParamNode(this);
	}

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		sb.append(ParamNode.PARAM_SYMBOL);
	}

	@Override
	protected boolean computeContainsAbstraction() {
		return true;
	}
	
	@Override
	public Node prime(final PrimeStorage ps) {
		return new PrimingNode(ps);
	}

	@Override
	public void visit(final TreeVisitor v) {
		v.visitParamNode(this);
	}
}
