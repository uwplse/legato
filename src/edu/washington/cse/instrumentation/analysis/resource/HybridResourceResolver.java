package edu.washington.cse.instrumentation.analysis.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import edu.washington.cse.instrumentation.analysis.utils.YamlUtil;

public class HybridResourceResolver implements ResourceResolver {
	private final StaticResourceResolver staticDelegate;
	private final YamlResourceResolver yamlDelegate;
	private final Set<SootMethod> accessMethods;

	public HybridResourceResolver(final String file, final JimpleBasedInterproceduralCFG icfg) {
		final List<Map<String, Object>> blob = YamlUtil.unsafeLoadFromFile(file);
		final List<Map<String, Object>> yamlEntries = new ArrayList<>();
		final List<Map<String, Object>> staticEntries = new ArrayList<>();
		for(final Map<String, Object> entry : blob) {
			if(entry.containsKey("whitelist") || entry.containsKey("blacklist") || entry.containsKey("pos") || entry.containsKey("group")) {
				yamlEntries.add(entry);
			} else if(entry.containsKey("access-sigs") || entry.containsKey("resources")) {
				staticEntries.add(entry);
			} else {
				throw new IllegalArgumentException("Could not map entry: " + entry + " to delegate");
			}
		}
		
		this.yamlDelegate = new YamlResourceResolver(yamlEntries);
		this.staticDelegate = new StaticResourceResolver(staticEntries, icfg);
		
		final HashSet<SootMethod> accessMethods = new HashSet<>();
		accessMethods.addAll(this.yamlDelegate.getResourceAccessMethods());
		accessMethods.addAll(this.staticDelegate.getResourceAccessMethods());
		this.accessMethods = Collections.unmodifiableSet(accessMethods);
	}

	@Override
	public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
		return this.yamlDelegate.isResourceAccess(ie, u) || this.staticDelegate.isResourceAccess(ie, u);
	}

	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		if(this.yamlDelegate.isResourceAccess(ie, u)) {
			return this.yamlDelegate.getAccessedResources(ie, u);
		} else if(this.staticDelegate.isResourceAccess(ie, u)) {
			return this.staticDelegate.getAccessedResources(ie, u);
		} else {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return this.yamlDelegate.isResourceMethod(m) || this.staticDelegate.isResourceMethod(m);
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		return accessMethods;
	}
	
}
