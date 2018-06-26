package edu.washington.cse.instrumentation.analysis.list;

import java.util.Arrays;

public class PrimeStorage {
	private static final int PACK_FACTOR = 8;
	private static final int PACK_SIZE = 64 / PACK_FACTOR;
	private static final int MASK = 0b11111111;
	
	public class ProxyMap {

		public boolean contains(final int callId) {
			final int ind = Arrays.binarySearch(keys, callId);
			if(ind < 0) {
				return false;
			}
			return true;
		}

		public int get(final int callId) {
			final int ind = Arrays.binarySearch(keys, callId);
			assert ind > -1 : Arrays.toString(keys) + " " + callId;
			return unpack(ind); 
		}
	}
	
	private final int[] keys;
	private final long[] values;
	public ProxyMap m = new ProxyMap();

	public PrimeStorage(final int p) {
		this.keys = new int[]{p};
		this.values = new long[]{1L};
	}
	
	private PrimeStorage(final int[] keys, final long[] values) {
		this.keys = keys;
		this.values = values;
	}

	public PrimeStorage combine(final PrimeStorage ps) {
		final int newSize = ps.keys.length + this.keys.length;
		final int valueSize = computeStorageSize(newSize);
		final long[] newValue = new long[valueSize];
		final int[] keys = new int[newSize];
		int outIt = 0;
		int it1 = 0, it2 = 0;
		while(it1 < this.keys.length && it2 < ps.keys.length) {
			final int k1 = this.keys[it1];
			final int k2 = ps.keys[it2];
			final byte toPut;
			final int outKey;
			if(k1 == k2) {
				final byte v1 = this.unpack(it1);
				final byte v2 = ps.unpack(it2);
				if(v1 == -1 || v2 == -1) {
					toPut = -1;
				} else {
					toPut = (byte) (v1 + v2);
				}
				outKey = k1;
				it1++;
				it2++;
			} else if(k1 < k2) {
				toPut = unpack(it1);
				outKey = k1;
				it1++;
			} else {
				outKey = k2;
				toPut = ps.unpack(it2);
				it2++;
			}
			updateKey(newValue, keys, outIt, outKey, toPut);
			outIt++;
		}
		while(it1 < this.keys.length) {
			assert it2 == ps.keys.length;
			final int outKey = this.keys[it1];
			final byte toPut = unpack(it1);
			updateKey(newValue, keys, outIt, outKey, toPut);
			it1++;
			outIt++;
		}
		while(it2 < ps.keys.length) {
			final int outKey = ps.keys[it2];
			final byte toPut = ps.unpack(it2);
			updateKey(newValue, keys, outIt, outKey, toPut);
			it2++;
			outIt++;
		}
		final int valueInd = computeStorageSize(outIt);
		final PrimeStorage toReturn = new PrimeStorage(Arrays.copyOf(keys, outIt), Arrays.copyOf(newValue, valueInd));
		return toReturn;	
	}

	private void updateKey(final long[] newValue, final int[] keys, final int outIt, final int outKey, final byte toPut) {
		keys[outIt] = outKey;
		final int valuePosition = outIt / PACK_FACTOR;
		newValue[valuePosition] |= shiftUp(outIt, toPut);
	}

	private byte unpack(final int index) {
		return (byte) ((this.values[index / PACK_FACTOR] >>> ((index % PACK_FACTOR) * PACK_SIZE)) & MASK);
	}

	public String getPrimingString() {
		final StringBuilder sb = new StringBuilder("{");
		for(int i = 0; i < keys.length; i++) {
			final int key = keys[i];
			final int value = unpack(i);
			if(i != 0) {
				sb.append(",");
			}
			if(value == 1) {
				sb.append(key);
			} else {
				sb.append(key).append(":").append(value);
			}
		}
		sb.append("}");
		return sb.toString();
	}
	
	public PrimeStorage join(final PrimeStorage ps) {
		final int newSize = ps.keys.length + this.keys.length;
		final int valueSize = computeStorageSize(newSize);
		final long[] newValue = new long[valueSize];
		final int[] keys = new int[newSize];
		int outIt = 0;
		int it1 = 0, it2 = 0;
		while(it1 < this.keys.length && it2 < ps.keys.length) {
			final int k1 = this.keys[it1];
			final int k2 = ps.keys[it2];
			final byte toPut;
			final int outKey;
			if(k1 == k2) {
				final byte v1 = this.unpack(it1);
				final byte v2 = ps.unpack(it2);
				if(v1 == -1 || v2 == -1 || v1 != v2) {
					toPut = -1;
				} else {
					toPut = v1;
				}
				outKey = k1;
				it1++;
				it2++;
			} else if(k1 < k2) {
				toPut = unpack(it1);
				outKey = k1;
				it1++;
			} else {
				outKey = k2;
				toPut = ps.unpack(it2);
				it2++;
			}
			updateKey(newValue, keys, outIt, outKey, toPut);
			outIt++;
		}
		while(it1 < this.keys.length) {
			assert it2 == ps.keys.length;
			final int outKey = this.keys[it1];
			final byte toPut = this.unpack(it1);
			updateKey(newValue, keys, outIt, outKey, toPut);
			it1++;
			outIt++;
		}
		while(it2 < ps.keys.length) {
			final int outKey = ps.keys[it2];
			final byte toPut = ps.unpack(it2);
			updateKey(newValue, keys, outIt, outKey, toPut);
			it2++;
			outIt++;
		}
		final int valueInd = computeStorageSize(outIt);
		return new PrimeStorage(Arrays.copyOf(keys, outIt), Arrays.copyOf(newValue, valueInd));	
	}

	private int computeStorageSize(final int outIt) {
		return (outIt / PACK_FACTOR) + (outIt % PACK_FACTOR == 0 ? 0 : 1);
	}

	private long shiftUp(final int outIt, final byte toPut) {
		return ((long)toPut) << ((outIt % PACK_FACTOR) * PACK_SIZE);
	}

	@Override
	public String toString() {
		return this.getPrimingString();
	}
	
	@Override
	public boolean equals(final Object obj) {
		if(!(obj instanceof PrimeStorage)) {
			return false;
		}
		final PrimeStorage ps = (PrimeStorage) obj;
		return Arrays.equals(keys, ps.keys) && Arrays.equals(values, ps.values);
	}
}
