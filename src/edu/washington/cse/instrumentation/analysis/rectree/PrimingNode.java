package edu.washington.cse.instrumentation.analysis.rectree;

import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;

public class PrimingNode extends Node {
	public final PrimeStorage ps;
	public PrimingNode(final PrimeStorage ps) {
		this.ps = ps;
	}

	@Override
	public NodeKind getKind() {
		return NodeKind.IMMEDIATE_PRIME;
	}

	@Override
	public Node joinWith(final Node n) {
		if(n.getKind() == NodeKind.PARAMETER) {
			return this;
		} else if(n.getKind() == NodeKind.CALLSITE || n.getKind() == NodeKind.COMPRESSED_CALLSITE) {
			return n.joinWith(this);
		} else if(n.getKind() != NodeKind.IMMEDIATE_PRIME) {
			return null;
		} else {
			return new PrimingNode(ps.join(((PrimingNode)n).ps));
		}
	}

	@Override
	public String label() {
		return "P";
	}

	@Override
	public boolean equal(final Node root) {
		if(root.getKind() != NodeKind.IMMEDIATE_PRIME) {
			return false;
		}
		return this.ps.equals(((PrimingNode)root).ps);
	}


	@Override
	public void walk(final TreeVisitor v) {
		v.visitPrime(this);
	}

	@Override
	public Node subst(final Node child) {
		return child.prime(ps);
	}

	@Override
	public Node prime(final PrimeStorage ps) {
		return new PrimingNode(this.ps.combine(ps));
	}

	@Override
	protected boolean computeContainsAbstraction() {
		return true;
	}

	@Override
	public void toString(final StringBuilder sb) {
		sb.append(label());
		sb.append(ps.getPrimingString());
	}

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		sb.append(label());
		sb.append(ps.getPrimingString());
	}

	@Override
	public void visit(final TreeVisitor v) {
		v.visitPrime(this);
	}
}
