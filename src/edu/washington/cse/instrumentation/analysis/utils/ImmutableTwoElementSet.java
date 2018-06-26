package edu.washington.cse.instrumentation.analysis.utils;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ImmutableTwoElementSet<T> extends AbstractSet<T> {
	
	private final T e2;
	private final T e1;

	public ImmutableTwoElementSet(final T elem1, final T elem2) {
		if(elem1 == null || elem2 == null) {
			throw new NullPointerException();
		}
		this.e1 = elem1;
		this.e2 = elem2;
	}
	
	@Override
	public boolean contains(final Object o) {
		return e2.equals(o) || e1.equals(o);
	}
	
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			int idx = 0;

			@Override
			public boolean hasNext() {
				return idx < 2;
			}

			@Override
			public T next() {
				if(idx == 0) {
					idx++;
					return e1;
				} else if(idx == 1) {
					idx++;
					return e2;
				} else {
					throw new NoSuchElementException();
				}
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}
	
	@Override
	public boolean remove(final Object o) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean removeAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public boolean retainAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public int size() {
		return 2;
	}

}
