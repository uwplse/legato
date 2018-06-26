package edu.washington.cse.instrumentation.analysis.rectree;

public interface TreeVisitor {
	void visitCallNode(CallNode callNode);
	void visitParamNode(ParamNode paramNode);
	void visitPrime(PrimingNode primingNode);
	void visitTransitionNode(TransitiveNode transitiveNode);
	void visitCompressedNode(CompressedTransitiveNode compressedTransitiveNode);
}
