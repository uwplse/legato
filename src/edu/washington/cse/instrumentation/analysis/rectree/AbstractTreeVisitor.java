package edu.washington.cse.instrumentation.analysis.rectree;

public class AbstractTreeVisitor implements TreeVisitor {
	@Override
	public void visitCallNode(final CallNode callNode) {}

	@Override
	public void visitParamNode(final ParamNode paramNode) {	}

	@Override
	public void visitPrime(final PrimingNode primingNode) { }

	@Override
	public void visitTransitionNode(final TransitiveNode transitiveNode) { }

	@Override
	public void visitCompressedNode(final CompressedTransitiveNode compressedTransitiveNode) { }
}
