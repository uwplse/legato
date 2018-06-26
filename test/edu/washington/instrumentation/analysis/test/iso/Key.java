package edu.washington.instrumentation.analysis.test.iso;

import edu.washington.cse.instrumentation.analysis.dfa.Symbol;

public class Key {
	private final Symbol s;
	private final int phi;
	private final int callId;
	private final int branchId;

	public Key(final Symbol s) {
		this.s = s;
		phi = -1;
		this.branchId = -1;
		this.callId = -1;
	}
	public Key(final int phi) {
		this.phi = phi;
		this.s = null;
		this.callId = -1;
		this.branchId = -1;
	}
	
	public Key(final int callId, final int branchId) {
		this.s = null;
		this.phi = -1;
		this.callId = callId;
		this.branchId = branchId;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + branchId;
		result = prime * result + callId;
		result = prime * result + phi;
		result = prime * result + ((s == null) ? 0 : s.hashCode());
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
		final Key other = (Key) obj;
		if(branchId != other.branchId) {
			return false;
		}
		if(callId != other.callId) {
			return false;
		}
		if(phi != other.phi) {
			return false;
		}
		if(s == null) {
			if(other.s != null) {
				return false;
			}
		} else if(!s.equals(other.s)) {
			return false;
		}
		return true;
	}
	
}
