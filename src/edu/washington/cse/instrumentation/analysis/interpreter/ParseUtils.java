package edu.washington.cse.instrumentation.analysis.interpreter;

import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol;
import edu.washington.cse.instrumentation.analysis.dfa.CallSymbol.CallRole;
import edu.washington.cse.instrumentation.analysis.dfa.Symbol;
import edu.washington.cse.instrumentation.analysis.dfa.TransitiveSymbol;

public class ParseUtils {
	
	public static Symbol parseSymbol(final char[] c, final int[] idx, final StringBuilder sb) {
		final int start = idx[0];
		assert c[idx[0]] == '{';
		final boolean isTransitive = c[idx[0] + 1] == 't';
		final boolean isSync = c[idx[0] + 1] == 's';
		if(isTransitive) {
			idx[0]+=2;
			final int callId = consumeUntil(c, idx, ',', sb, start);
			final int branchId = consumeUntil(c, idx, '}', sb, start);
			int prime = 0;
			while(idx[0] < c.length && c[idx[0]] == '\'') {
				prime++;
				idx[0]++;
			}
			return new TransitiveSymbol(callId, branchId, prime);
		} else {
			idx[0]+= isSync ? 2 : 1;
			final int cId = consumeUntil(c, idx, '}', sb, start);
			int prime = 0;
			while(idx[0] < c.length && c[idx[0]] == '\'') {
				prime++;
				idx[0]++;
			}
			return new CallSymbol(cId, prime, isSync ? CallRole.SYNCHRONIZATION : CallRole.NONE);
		}
	}
	
	public static int consumeUntil(final char[] c, final int[] idx, final char breakCharacter, final StringBuilder sb, final int start) {
		sb.setLength(0);
		while(idx[0] < c.length && c[idx[0]] != breakCharacter) {
			final char d = c[idx[0]++];
			assert Character.isDigit(d) : "Bad digit at " + idx[0] + ": " + d;
			sb.append(d);
		}
		if(idx[0] == c.length) {
			throw new RuntimeException("parse error at: " + start + " in " + new String(c));
		}
		assert c[idx[0]] == breakCharacter;
		idx[0]++;
		return Integer.parseInt(sb.toString());
	}
}
