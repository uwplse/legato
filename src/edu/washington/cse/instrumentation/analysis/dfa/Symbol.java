package edu.washington.cse.instrumentation.analysis.dfa;

abstract public class Symbol {
	public abstract Kind getKind();
	public abstract void toConcreteSyntax(StringBuilder sb);
	public abstract void prettyPrint(StringBuilder sb);
	public String prettyPrint() {
		final StringBuilder sb = new StringBuilder();
		prettyPrint(sb);
		return sb.toString();
	}
	public String toConcreteSyntax() {
		final StringBuilder sb = new StringBuilder();
		toConcreteSyntax(sb);
		return sb.toString();
	}
}
