package edu.washington.cse.instrumentation.analysis.rectree;

import static edu.washington.cse.instrumentation.analysis.interpreter.ParseUtils.parseSymbol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.dfa.Kind;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.dfa.TransitiveSymbol;
import edu.washington.cse.instrumentation.analysis.interpreter.DomainParser;

public class Parser implements DomainParser<Node> {
	private final List<ParseToken> parseStack = new ArrayList<>();
	private final StringBuilder sb = new StringBuilder();
	
	@Override
	public Node parse(final String s) {
		assert parseStack.size() == 0;
		int idx = 0;
		final int[] idxByVal = new int[1];
		final char[] chars = s.toCharArray();
		
		while(idx < chars.length) {
			switch(chars[idx]) {
			case '{':
				idxByVal[0] = idx;
				final Symbol sym = parseSymbol(chars, idxByVal, sb);
				idx = idxByVal[0];
				parseStack.add(new ParseToken(sym));
				break;
			case '|':
				parseStack.add(new ParseToken(Syntax.CHOICE_BAR));
				idx++;
				break;
			case '(':
				parseStack.add(new ParseToken(Syntax.OPEN_PAREN));
				idx++;
				break;
			case ')':
				final Node r = closeParen();
				parseStack.add(new ParseToken(r));
				idx++;
				break;
			case ParamNode.PARAM_SYMBOL:
				final Node p = RecTreeDomain.paramTree().restRoot;
				parseStack.add(new ParseToken(p));
				idx++;
				break;
			default:
				throw new RuntimeException("Illegal token " + chars[idx] + " at: " + idx + " in " + s);	
			}
		}
		return reverseRead();
	}
	
	private Node reverseRead(final Syntax... ss) {
		final List<Syntax> stopSymbols = Arrays.asList(ss);
		int stackPtr = parseStack.size() - 1;
		Node branch = null;
		while(stackPtr >= 0) {
			final ParseToken tok = parseStack.remove(stackPtr--);
			assert stackPtr == parseStack.size() - 1;
			if(tok.syntax != null) {
				if(stopSymbols.contains(tok.syntax)) {
					parseStack.add(tok);
					return branch;
				} else {
					throw new RuntimeException("Parse error, unexpected syntax: " + tok.syntax);
				}
			} else if(tok.node != null) {
				assert branch == null;
				branch = tok.node;
			} else {
				assert tok.sym != null;
				if(tok.sym.getKind() == Kind.CALL) {
					final CallSymbol cs = (CallSymbol)tok.sym;
					if(branch != null && cs.getRole() != CallRole.SYNCHRONIZATION) {
						branch = new TransitiveNode(1, cs.getCallId(), cs.getPrime(), 0, branch);
					} else if(branch != null && cs.getRole() == CallRole.SYNCHRONIZATION) {
						branch = new CallNode(cs.getCallId(), cs.getPrime(), branch, cs.getRole());
					} else {
						branch = new CallNode(cs.getCallId(), cs.getPrime(), null, CallRole.GET);
					}
				} else if((tok.sym.getKind() == Kind.TRANSITIVE)) {
					final TransitiveSymbol ts = (TransitiveSymbol) tok.sym;
					branch = new TransitiveNode(ts.branchId + 1, ts.callSiteId, ts.prime, ts.branchId, branch);
				}
			}
		}
		if(stopSymbols.size() == 0) {
			return branch;
		} else {
			throw new RuntimeException("Parse error, did not find one of expected: " + stopSymbols);
		}
	}
	
	private Node closeParen() {
		final ArrayList<Node> sequences = new ArrayList<>();
		while(true) {
			final Node n = reverseRead(Syntax.CHOICE_BAR, Syntax.OPEN_PAREN);
			sequences.add(n);
			final ParseToken br = parseStack.remove(parseStack.size() - 1);
			if(br.syntax == Syntax.OPEN_PAREN) {
				break;
			}
		}
		if(sequences.get(0).getKind() == NodeKind.CONST) {
			throw new RuntimeException("parse error");
		} else {
			assert sequences.get(0).getKind() == NodeKind.CALLSITE;
			final TransitiveNode fstTransNode = (TransitiveNode)sequences.get(0);
			final int callId = fstTransNode.callId;
			final int prime = fstTransNode.prime;
			assert fstTransNode.size == 1;
			final int maxBranch = fstTransNode.getKeyIterator().next();
			Node[] tr = new Node[maxBranch + 1];
			tr[maxBranch] = fstTransNode.transitions[maxBranch];
			for(int i = 1; i < sequences.size(); i++) {
				final Node n = sequences.get(i);
				assert n.getKind() == NodeKind.CALLSITE: "Non-transitive node in choice";
				final TransitiveNode bn = (TransitiveNode)n;
				assert bn.callId == callId && bn.prime == prime : "Non-matching callid/prime: " + callId + " and " + bn.callId + "/" + prime + " and " + bn.prime;
				assert bn.size == 1 : "Transitions too big?! " + bn.transitions;
				final int branchId = bn.getKeyIterator().next();
				if(branchId < tr.length) {
					assert tr[branchId] == null : "Duplicate branches";
					tr[branchId] = bn.transitions[branchId];
				} else {
					tr = Arrays.copyOf(tr, branchId + 1);
					tr[branchId] = bn.transitions[branchId];
				}
			}
			return new TransitiveNode(callId, prime, tr, sequences.size());

		}
	}
}

enum Syntax {
	OPEN_PAREN,
	CHOICE_BAR
}

class ParseToken {
	final Symbol sym;
	final Syntax syntax;
	final Node node;
	public ParseToken(final Symbol n) {
		this.sym = n;
		this.syntax = null;
		this.node = null;
	}
	public ParseToken(final Syntax s) {
		this.sym = null;
		this.syntax = s;
		this.node = null;
	}
	public ParseToken(final Node r) {
		this.sym = null;
		this.node = r;
		this.syntax = null;
	}
	@Override
	public String toString() {
		return "ParseToken [node=" + sym + ", syntax=" + syntax + "]";
	}
}
