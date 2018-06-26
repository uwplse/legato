package edu.washington.cse.instrumentation.analysis.dfa;

import edu.washington.cse.instrumentation.analysis.rectree.Node;

public class TransitiveSymbol extends Symbol {
	public TransitiveSymbol(final int cid, final int bid, final int prime) {
		this.callSiteId = cid;
		this.branchId = bid;
		this.prime = prime;
	}
	
	public final int callSiteId, branchId, prime;

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		sb.append("{t").append(callSiteId).append(',').append(branchId).append('}');
		for(int i = 0; i < prime; i++) {
			sb.append('\'');
		}
	}

	@Override
	public void prettyPrint(final StringBuilder sb) {
		sb.append('{').append(callSiteId).append('}');
		Node.subscriptNumber(sb, branchId);
		for(int i = 0; i < prime; i++) {
			sb.append('\'');
		}
	}
	
	@Override
	public Kind getKind() {
		return Kind.TRANSITIVE;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + branchId;
		result = prime * result + callSiteId;
		result = prime * result + this.prime;
		return result;
	}

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
		final TransitiveSymbol other = (TransitiveSymbol) obj;
		if(branchId != other.branchId) {
			return false;
		}
		if(callSiteId != other.callSiteId) {
			return false;
		}
		if(prime != other.prime) {
			return false;
		}
		return true;
	}
	
}
