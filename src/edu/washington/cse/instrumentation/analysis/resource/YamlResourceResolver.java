package edu.washington.cse.instrumentation.analysis.resource;

import edu.washington.cse.instrumentation.analysis.utils.YamlUtil;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

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

public class YamlResourceResolver extends PointsToMethodResourceResolver {
	
	private final TObjectIntMap<SootMethod> methodSigs = new TObjectIntHashMap<>();
	private final Map<String, String> keyToGroup = new HashMap<>();
	private final Set<String> whitelist;
	private int groupCounter = 0;
	private final HashSet<String> blacklist;
	
	@SuppressWarnings("unchecked")
	public YamlResourceResolver(final String fileName) {
		this(YamlUtil.<List<Map<String, Object>>>unsafeLoadFromFile(fileName));
		
	}

	@SuppressWarnings("unchecked")
	YamlResourceResolver(final List<Map<String, Object>> parsed) {
		Set<String> whitelistTmp = null;
		HashSet<String> blacklistTmp = null;
		for(final Map<String, Object> spec : parsed) {
			if(spec.containsKey("whitelist")) {
				whitelistTmp = new HashSet<String>((List<String>)spec.get("whitelist"));
				continue;
			}
			if(spec.containsKey("blacklist")) {
				blacklistTmp = new HashSet<String>((List<String>)spec.get("blacklist"));
				continue;
			}
			if(!spec.containsKey("sig") && !spec.containsKey("group")) {
				throw new IllegalArgumentException("Invalid entry: " + spec);
			}
			if(spec.containsKey("group")) {
				assert !spec.containsKey("sig");
				handleGroup((List<String>)spec.get("group"));
				continue;
			}
			assert spec.containsKey("sig");
			final String sig = (String) spec.get("sig");
			if(methodSigs.containsKey(sig)) {
				throw new IllegalArgumentException("Duplicate signature: " + spec + " " + methodSigs.get(sig));
			}
			int p = -1;
			if(spec.containsKey("pos")) {
				p = (int) spec.get("pos");
			}
			methodSigs.put(Scene.v().getMethod(sig), p);
		}
		whitelist = whitelistTmp;
		blacklist = blacklistTmp;
	}

	private void handleGroup(final List<String> memberList) {
		final String groupName = "AbstractGroup-" + (groupCounter++);
		for(final String l : memberList) {
			if(keyToGroup.containsKey(l)) {
				throw new IllegalArgumentException("Duplicate group membership for key: " + l + " " + memberList + " " + keyToGroup.get(l));
			}
			keyToGroup.put(l, groupName);
		}
	}
	
	@Override
	public Set<String> getAccessedResources(final InvokeExpr ie, final Unit u) {
		final Set<String> accessedResources = super.getAccessedResources(ie, u);
		if(accessedResources == null) {
			return null;
		}
		final Set<String> toReturn = new HashSet<>();
		for(final String key : accessedResources) {
			if(keyToGroup.containsKey(key)) {
				toReturn.add(keyToGroup.get(key));
			} else if(blacklist != null && blacklist.contains(key)) {
				continue;
			} else if(whitelist == null || whitelist.contains(key)) {
				toReturn.add(key);
			}
		}
		return toReturn;
	}

	@Override
	public boolean isResourceMethod(final SootMethod m) {
		return methodSigs.containsKey(m);
	}

	@Override
	protected int getArgumentPosition(final SootMethod m) {
		return methodSigs.get(m);
	}

	@Override
	public Collection<SootMethod> getResourceAccessMethods() {
		return methodSigs.keySet();
	}
}
