package edu.washington.cse.instrumentation.analysis.resource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import edu.washington.cse.instrumentation.analysis.utils.ResourceStringGenerator;

public class CachedYamlResourceResolver implements ResourceResolver {
	private final Set<SootMethod> accessMethods = new HashSet<>();
	
	// awful
	private final Set<String> NULL_RESULT = new HashSet<>();
	
	private static class ResourceSpecification {
		private final int tag;
		private final Set<String> result;
		private final String unitString;

		public ResourceSpecification(final String unitString, final int tag, final Set<String> result) {
			this.unitString = unitString;
			this.tag = tag;
			this.result = result;
		}
	}
	
	private final Map<SootMethod, List<ResourceSpecification>> specs = new HashMap<>();
	
	private final LoadingCache<Unit, Optional<Set<String>>> resolver = CacheBuilder.newBuilder().build(new CacheLoader<Unit, Optional<Set<String>>>() {
		@Override
		public Optional<Set<String>> load(final Unit key) throws Exception {
			final Stmt s = (Stmt) key;
			if(!s.containsInvokeExpr()) {
				return Optional.absent();
			}
			if(!accessMethods.contains(s.getInvokeExpr().getMethod())) {
				return Optional.absent();
			}
			final SootMethod containing = icfg.getMethodOf(key);
			if(!specs.containsKey(containing)) {
				return Optional.absent();
			}
			final List<ResourceSpecification> r = specs.get(containing);
			final int tag = ResourceStringGenerator.getUnitTag(s, containing);
			final boolean unique;
			if(tag == 0) {
				unique = ResourceStringGenerator.isUniqueWithin(s, containing);
			} else {
				unique = false;
			}
			final String stringRepr = s.toString();
			for(final ResourceSpecification sp : r) {
				if(sp.unitString.equals(stringRepr) && (unique && sp.tag == -1) || (sp.tag == tag)) {
					if(sp.result == null) {
						return Optional.of(NULL_RESULT);
					} else {
						return Optional.of(sp.result);
					}
				}
			}
			return Optional.absent();
		}
	});
	private final JimpleBasedInterproceduralCFG icfg;
	@SuppressWarnings("unchecked")
	public CachedYamlResourceResolver(final String inputFile, final JimpleBasedInterproceduralCFG icfg) {
		final List<Map<String, Object>> parsed;
		final Yaml y = new Yaml();
		try(Reader r = new FileReader(new File(inputFile))) {
			parsed = (List<Map<String, Object>>) y.load(r);
		} catch (final IOException e) { throw new RuntimeException("womp", e); }
		assert parsed.get(0).containsKey("access-sigs");
		{
			final List<String> sigs = (List<String>) parsed.get(0).get("access-sigs");
			for(final String sig : sigs) {
				accessMethods.add(Scene.v().getMethod(sig));
			}
		}
		for(final Map<String, Object> sp : parsed.subList(1, parsed.size())) {
			final String repr = (String) sp.get("unit");
			final SootMethod containing = Scene.v().getMethod((String) sp.get("method"));
			assert sp.containsKey("res");
			Set<String> r;
			if(sp.get("res") == null) {
				r = null;
			} else {
				r = new HashSet<>();
				for(final String l : ((List<String>)sp.get("res"))) {
					r.add(l);
				}
			}
			int tag;
			if(sp.containsKey("unique")) {
				tag = -1;
			} else {
				tag = (int) sp.get("unique");
			}
			if(!specs.containsKey(containing)) {
				specs.put(containing, new ArrayList<ResourceSpecification>());
			}
			specs.get(containing).add(new ResourceSpecification(repr, tag, r));
		}
		this.icfg = icfg;
	}
	
	@Override
	public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
		if(!accessMethods.contains(ie.getMethod())) {
			return false;
		}
		return resolver.getUnchecked(u).isPresent();
	}

	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		final Optional<Set<String>> resolved = resolver.getUnchecked(u);
		if(!resolved.isPresent()) { 
			throw new RuntimeException("woops");
		}
		final Set<String> r = resolved.get();
		if(r == NULL_RESULT) {
			return null;
		} else {
			return r;
		}
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return accessMethods.contains(m);
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		return Collections.unmodifiableSet(accessMethods);
	}
	
}
