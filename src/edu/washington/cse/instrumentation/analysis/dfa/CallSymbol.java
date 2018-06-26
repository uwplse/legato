package edu.washington.cse.instrumentation.analysis.dfa;


public final class CallSymbol extends Symbol {
	public enum CallRole {
		NONE,
		GET,
		SYNCHRONIZATION
	}
	
	private final int siteId;
	private final int prime;
	private final CallRole role;
	
	public CallSymbol(final int sid) {
		this(sid, CallRole.NONE);
	}

	public CallSymbol(final int unitNumber, final CallRole transitive) {
		this(unitNumber, 0, transitive);
	}

	public CallSymbol(final int unitNumber, final int prime, final CallRole transitive) {
		this.role = transitive;
		this.siteId = unitNumber;
		this.prime = prime;
	}

	public CallSymbol(final int sid, final int prime) {
		this(sid, prime, CallRole.NONE);
	}

	@Override
	public Kind getKind() {
		return Kind.CALL;
	}
	
	public int getCallId() {
		return siteId;
	}
	
	public CallRole getRole() {
		return role;
	}
	
	public int getPrime() {
		return prime;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((role == null) ? 0 : role.hashCode());
		result = prime * result + siteId;
		result = prime * result + this.prime;
		return result;
	}

	@Override
	public String toString() {
		return "Call(siteId=" + siteId + ")";
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
		final CallSymbol other = (CallSymbol) obj;
		if(role != other.role) {
			return false;
		}
		if(siteId != other.siteId) {
			return false;
		}
		if(other.prime != this.prime) {
			return false;
		}
		return true;
	}

	@Override
	public void prettyPrint(final StringBuilder sb) {
		toConcreteSyntax(sb);
	}

	@Override
	public void toConcreteSyntax(final StringBuilder sb) {
		sb.append("{");
		if(this.role == CallRole.SYNCHRONIZATION) {
			sb.append('s');
		}
		sb.append(siteId).append("}");
		for(int i = 0; i < prime; i++) {
			sb.append('\'');
		}
	}
}
