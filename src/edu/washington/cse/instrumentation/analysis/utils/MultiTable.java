package edu.washington.cse.instrumentation.analysis.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table.Cell;
import com.google.common.collect.Tables;

public class MultiTable<R, C, V> {
	private final HashBasedTable<R, C, Set<V>> backingTable;

	public MultiTable() {
		this.backingTable = HashBasedTable.<R, C, Set<V>>create();
	}
	
	public boolean contains(final R rowKey, final C columnKey) {
		return backingTable.contains(rowKey, columnKey);
	}

	public boolean containsRow(final R rowKey) {
		return backingTable.containsRow(rowKey);
	}

	public boolean containsColumn(final C columnKey) {
		return backingTable.containsColumn(columnKey);
	}

	public Set<V> get(final R rowKey, final C columnKey) {
		return backingTable.get(rowKey, columnKey);
	}

	public boolean isEmpty() {
		return backingTable.isEmpty();
	}

	public void clear() {
		backingTable.clear();
	}


	public void put(final R rowKey, final C columnKey, final V value) {
		if(!backingTable.contains(rowKey, columnKey)) {
			backingTable.put(rowKey, columnKey, new HashSet<V>());
		}
		backingTable.get(rowKey, columnKey).add(value);
	}

	public Set<V> remove(final R rowKey, final C columnKey) {
		return backingTable.remove(rowKey, columnKey);
	}

	public boolean remove(final R rowKey, final C columnKey, final V value) {
		if(!contains(rowKey, columnKey)) {
			return false;
		}
		return this.get(rowKey, columnKey).remove(value);
	}
	
	public Map<C, Set<V>> row(final R rowKey) {
		return backingTable.row(rowKey);
	}

	public Map<R, Set<V>> column(final C columnKey) {
		return backingTable.column(columnKey);
	}

	public Set<R> rowKeySet() {
		return backingTable.rowKeySet();
	}

	public Set<C> columnKeySet() {
		return backingTable.columnKeySet();
	}

	public Map<R, Map<C, Set<V>>> rowMap() {
		return backingTable.rowMap();
	}

	public Map<C, Map<R, Set<V>>> columnMap() {
		return backingTable.columnMap();
	}
	
	public Iterable<Cell<R, C, V>> cellSet() {
		final Set<Cell<R, C, V>> toIter = new HashSet<>();
		for(final R row : backingTable.rowKeySet()) {
			for(final Map.Entry<C, Set<V>> kv : backingTable.row(row).entrySet()) {
				final C column = kv.getKey();
				for(final V val : kv.getValue()) {
					toIter.add(Tables.immutableCell(row, column, val));
				}
			}
		}
		return toIter;
	}
	
	@Override
	public String toString() {
		return backingTable.toString();
	}
}