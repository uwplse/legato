package edu.washington.cse.instrumentation.analysis.rectree;

import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.list.PrimeStorage;
import gnu.trove.TIntCollection;


public abstract class Node {
	public abstract NodeKind getKind();
	public abstract Node joinWith(Node n);
	public abstract String label();
	public abstract boolean equal(final Node root);
	public abstract void walk(TreeVisitor v);
	public abstract Node subst(Node child);
	public abstract Node prime(PrimeStorage ps);
	public abstract void visit(TreeVisitor v);
	
	@Override
	public boolean equals(final Object obj) {
		if(obj == null) {
			return false;
		}
		if(!(obj instanceof Node)) {
			return false;
		}
		return this.equal((Node)obj);
	}
	
	
	
	public abstract void toString(StringBuilder sb);
	public abstract void toConcreteSyntax(StringBuilder sb);
	
	public interface NodeSerializer {
		public void handleNode(Node n, StringBuilder sb);
		public void handleSymbol(Symbol s, StringBuilder sb);
	}
	
	public static final NodeSerializer PP = new NodeSerializer() {
		@Override
		public void handleSymbol(final Symbol s, final StringBuilder sb) {
			s.prettyPrint(sb);
		}
		@Override
		public void handleNode(final Node n, final StringBuilder sb) {
			n.toString(sb);
		}
	};
	
	public static final NodeSerializer CONCRETE = new NodeSerializer() {
		@Override
		public void handleNode(final Node n, final StringBuilder sb) {
			n.toConcreteSyntax(sb);
		}
		@Override
		public void handleSymbol(final Symbol s, final StringBuilder sb) {
			s.toConcreteSyntax(sb);
		}
	};
	
	
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		toString(sb);
		return sb.toString();
	}
	public String dumpSyntax() {
		final StringBuilder sb = new StringBuilder();
		toConcreteSyntax(sb);
		return sb.toString();
	}

	
	private volatile int _abstractionCache = -1;
	public final boolean containsAbstraction() {
		if(_abstractionCache == -1) {
			_abstractionCache = computeContainsAbstraction() ? 1 : 0;
		}
		return _abstractionCache == 1;
	}
	protected abstract boolean computeContainsAbstraction();
	
	private static char[] convert(final char s, final boolean isSub) {
		assert s >= '0' && s <= '9';
		int codePointShift = 16;
		int codePoint = 0;
		if(s == '2' || s == '3') {
			codePointShift = 0x1FD0;
			codePoint = 0xB2 + (s - '2');
		} else {
			codePoint = 0x2070 + (s - '0');
		}
		if(isSub) {
			codePoint += codePointShift;
		}
		return Character.toChars(codePoint);
	}
	public static void superscriptNumber(final StringBuilder sb, final int i) {
		final String s = i + "";
		for(final char c : s.toCharArray()) {
			sb.append(Node.convert(c, false));
		}
	}
	public static void subscriptNumber(final StringBuilder sb, final int i) {
		final String s = i + "";
		for(final char c : s.toCharArray()) {
			sb.append(Node.convert(c, true));
		}
	}
	
	public static String prettyToConcreteSyntax(final String input) {
		final StringBuilder sb = new StringBuilder();
		int i = 0;
		while(i < input.length()) {
			if(input.charAt(i) != '\u03D5') {
				sb.append(input.charAt(i));
				i++;
				continue;
			}
			i++;
			final StringBuilder phiIdString = new StringBuilder();
			while(true) {
				final char c = input.charAt(i++);
				if(c >= '0' && c <= '9') {
					phiIdString.append(c);
				} else {
					i--;
					break;
				}
			}
			final StringBuilder branchIdString = new StringBuilder();
			outer: while(true) {
				final char c = input.charAt(i);
				switch(c) {
				case '⁰':
					branchIdString.append('0');
					break;
				case 'ⁱ':
					branchIdString.append('1');
					break;
				case '²':
					branchIdString.append('2');
					break;
				case '³':
					branchIdString.append('3');
					break;
				case '⁴':
					branchIdString.append('4');
					break;
				case '⁵':
					branchIdString.append('5');
					break;
				case '⁶':
					branchIdString.append('6');
					break;
				case '⁷':
					branchIdString.append('7');
					break;
				case '⁸':
					branchIdString.append('8');
					break;
				case '⁹':
					branchIdString.append('9');
					break;
				default:
					break outer;
				}
				i++;
			}
			sb.append("{p").append(phiIdString).append(',').append(branchIdString).append('}');
		}
		return sb.toString();
	}
	
	public Node tryAnnihilate(final TIntCollection trSites, final TIntCollection accessSites, final TIntCollection transitiveSynchLabels) {
		return this;
	}
}
