package edu.washington.cse.instrumentation.analysis.resource;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.utils.YamlUtil;

public class StaticResourceResolver implements ResourceResolver {
	
	private final JimpleBasedInterproceduralCFG icfg;
	final Set<SootMethod> accessMethods = new HashSet<>();
	final Map<SootMethod, Set<String>> resourceAbstraction = new HashMap<>();
	final Table<SootMethod, Unit, Set<String>> accessed = HashBasedTable.create();

	@SuppressWarnings("unchecked")
	public StaticResourceResolver(final String f, final JimpleBasedInterproceduralCFG icfg) {
		this(YamlUtil.<List<Map<String, Object>>>unsafeLoadFromFile(f), icfg);
	}
	
	StaticResourceResolver(final List<Map<String, Object>> parsed, final JimpleBasedInterproceduralCFG icfg) {
		this.readSpecs(parsed);
		this.icfg = icfg;
	}

	@SuppressWarnings("unchecked")
	private void readSpecs(final List<Map<String, Object>> parsed) {
		if(parsed.size() == 0) {
			return;
		}
		assert parsed.get(0).containsKey("access-sigs");
		for(final String m : (List<String>)parsed.get(0).get("access-sigs")) {
			accessMethods.add(Scene.v().getMethod(m));
		}
		outer_parse: for(final Map<String, Object> sp : parsed.subList(1	, parsed.size())) {
			if(sp.containsKey("sig")) {
				final SootMethod m = Scene.v().getMethod((String) sp.get("sig"));
				if(resourceAbstraction.containsKey(m)) {
					throw new IllegalArgumentException();
				}
				resourceAbstraction.put(m, new HashSet<String>((List<String>)sp.get("resources")));
			} else if(sp.containsKey("unit")) {
				final SootMethod container = Scene.v().getMethod((String) sp.get("container"));
				final int targetId = (Integer)sp.get("tag");
				final String s = (String) sp.get("unit");
				int id = 0;
				for(final Unit u : container.getActiveBody().getUnits()) {
					if(u.toString().equals(s)) {
						if(id == targetId) {
							accessed.put(container, u, new HashSet<String>((List<String>)sp.get("resources")));
							continue outer_parse;
						} else {
							id++;
						}
					}
				}
				throw new IllegalArgumentException();
			} else {
				throw new IllegalArgumentException();
			}
		}
	}

	@Override
	public boolean isResourceAccess(final InvokeExpr ie, final Unit u) {
		final SootMethod method = ie.getMethod();
		if(!accessMethods.contains(method)) {
			return false;
		}
		if(resourceAbstraction.containsKey(method)) {
			return true;
		}
		return accessed.contains(icfg.getMethodOf(u), u);
	}

	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		assert isResourceAccess(ie, u);
		final SootMethod method = ie.getMethod();
		if(resourceAbstraction.containsKey(method)) {
			return resourceAbstraction.get(method);
		}
		return accessed.get(icfg.getMethodOf(u), u);
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return accessMethods.contains(m);
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		return accessMethods;
	}

}
