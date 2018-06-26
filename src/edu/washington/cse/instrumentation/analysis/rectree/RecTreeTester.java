package edu.washington.cse.instrumentation.analysis.rectree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.interpreter.AbstractInterpreter;
import edu.washington.cse.instrumentation.analysis.interpreter.OperationProvider;
import edu.washington.cse.instrumentation.analysis.interpreter.ParseUtils;

public class RecTreeTester {
	private static List<Symbol> parseSymbolList(final String s) {
		final char[] c = s.toCharArray();
		final int[] idx = new int[]{0};
		final StringBuilder sb = new StringBuilder();
		final ArrayList<Symbol> toReturn = new ArrayList<>();
		while(idx[0] < c.length) {
			toReturn.add(ParseUtils.parseSymbol(c, idx, sb));
		}
		return toReturn;
	}
	
	public static void main(final String[] args) throws IOException {
		new AbstractInterpreter<Node>(new Parser(), new OperationProvider<Node>() {
			@Override
			public Node join(final Node n1, final Node n2) {
				return n1.joinWith(n2);
			}
			
			@Override
			public boolean equal(final Node n1, final Node n2) {
				return n1.equal(n2);
			}

			@Override
			public boolean extCommand(final String[] tokens, final List<Node> stack,
					final Map<String, Node> varMap) {
				switch(tokens[0]) {
				case "prepend":
					final List<Symbol> toPrepend = parseSymbolList(tokens[1]);
					Node out;
					if(tokens.length == 2) {
						out = RecTreeDomain.prepend(toPrepend, stack.remove(stack.size() - 1));
					} else {
						assert varMap.containsKey(tokens[2]);
						out = RecTreeDomain.prepend(toPrepend, varMap.get(tokens[2]));
					}
					stack.add(out);
					return true;
				case "dump":
					if(tokens.length == 2) {
						System.out.println(varMap.get(tokens[1]).dumpSyntax());
					} else {
						final Node n = stack.remove(stack.size() - 1);
						System.out.println(n.dumpSyntax());
					}
					return true;
				case "depth":
					if(tokens.length == 2) {
						System.out.println(RecTreeDomain.checkDepth(varMap.get(tokens[1])));
					} else {
						System.out.println(RecTreeDomain.checkDepth(stack.remove(stack.size() - 1)));
					}
					return true;
				default:
					return false;
				}
			}
		}).interpret(args[0]);
	}
}
