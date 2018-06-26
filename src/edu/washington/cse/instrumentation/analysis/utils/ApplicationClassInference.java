package edu.washington.cse.instrumentation.analysis.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApplicationClassInference {
	public static class Results {
		public final Set<String> applicationClassPrefixes;
		public final Set<String> libraryClassPrefixes;
		
		Results(final Set<String> applicationClassPrefixes, final Set<String> libraryClassPrefixes) {
			this.applicationClassPrefixes = applicationClassPrefixes;
			this.libraryClassPrefixes = libraryClassPrefixes;
		}
	}
	
	private static class PackageTrie {
		private final Map<String, PackageTrie> children;
		public PackageTrie() {
			 children = new HashMap<>();
		}
		
		public static PackageTrie of(final List<String> comp) {
			final PackageTrie root = new PackageTrie();
			PackageTrie currNode = root;
			for(final String c : comp) {
				final PackageTrie newNode = new PackageTrie();
				currNode.children.put(c, newNode);
				currNode = newNode;
			}
			return root;
		}
		
		void update(final List<String> mergeIn) {
			PackageTrie currNode = this;
			for(int i = 0; i < mergeIn.size(); i++) {
				final String comp = mergeIn.get(i);
				if(!currNode.children.containsKey(comp)) {
					currNode.children.put(comp, PackageTrie.of(mergeIn.subList(i+1, mergeIn.size())));
					return;
				} else {
					currNode = currNode.children.get(comp);
				}
			}
		}
		
		public Set<String> findPackageStrings() {
			return this.findSingleChains(new ArrayList<String>());
		}
		
		private Set<String> findSingleChains(final List<String> accum) { 
			if(this.children.size() == 1) {
				return toChainEnd(accum);
			} else if(this.children.size() == 0) {
				return Collections.singleton(accumulatorToPackage(accum));
			}
			final Set<String> toReturn = new HashSet<>();
			for(final Map.Entry<String, PackageTrie> kv : children.entrySet()) {
				final List<String> subAccum = new ArrayList<>(accum);
				subAccum.add(kv.getKey());
				toReturn.addAll(kv.getValue().findSingleChains(subAccum));
			}
			return toReturn;
		}
		
		private Set<String> toChainEnd(final List<String> acc) { 
			PackageTrie it = this;
			while(it.children.size() == 1) {
				final Map.Entry<String, PackageTrie> entry = it.children.entrySet().iterator().next();
				if(entry.getKey().equals("$")) {
					break;
				}
				acc.add(entry.getKey());
				it = entry.getValue();
			}
			return Collections.singleton(accumulatorToPackage(acc));
		}
		
		private String accumulatorToPackage(final List<String> accum) {
			assert accum.size() > 0;
			final StringBuilder toReturn = new StringBuilder(accum.get(0));
			for(int i = 1; i < accum.size(); i++) {
				toReturn.append(".").append(accum.get(i));
			}
			return toReturn.toString();
		}
		
		@Override
		public String toString() {
			return this.children.toString();
		}
	}
	
	public static Results inferApplicationClasses(final Iterator<String> positiveExample, final Iterator<String> negativeExamples) {
		final PackageTrie accum = new PackageTrie();
		while(positiveExample.hasNext()) {
			final String s = positiveExample.next();
			final String[] comp = s.split("\\.");
			comp[comp.length - 1] = "$";
			accum.update(Arrays.asList(comp));
		}
		final Set<String> applicationPackage = accum.findPackageStrings();
		while(negativeExamples.hasNext()) {
			final String s = negativeExamples.next();
			for(final String ap : applicationPackage) {
				assert !s.startsWith(ap);
			}
		}
		return new Results(Collections.unmodifiableSet(applicationPackage), Collections.<String>emptySet());
	}
}
