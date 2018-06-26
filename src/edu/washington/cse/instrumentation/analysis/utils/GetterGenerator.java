package edu.washington.cse.instrumentation.analysis.utils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class GetterGenerator {
	public static void main(final String[] args) {
		final List<Object> def = new ArrayList<>();
		final Set<SootMethod> accessMethods = new HashSet<>();
		soot.Main.main(args);
		for(final SootClass sc : Scene.v().getApplicationClasses()) {
			for(final SootMethod m : sc.getMethods()) {
				if(m.getName().length() <= 3) {
					continue;
				}
				if(!m.getName().startsWith("get")) {
					continue;
				}
				if(!m.isPublic()) {
					continue;
				}
				final String rawPropertyName = m.getName().substring(3);
				final String propertyName = rawPropertyName.substring(0, 1).toLowerCase() + rawPropertyName.substring(1);
				final Map<String, Object> entry = new HashMap<>();
				entry.put("sig", m.getSignature());
				entry.put("resources", Collections.singletonList(propertyName));
				accessMethods.add(m);
				def.add(entry);
			}
		}
		final List<String> methodSigs = new ArrayList<>();
		for(final SootMethod am : accessMethods) {
			methodSigs.add(am.getSignature());
		}
		final Map<String, Object> entry = new HashMap<>();
		entry.put("access-sigs", methodSigs);
		def.add(0, entry);
		final DumperOptions dOpt = new DumperOptions();
		dOpt.setWidth(Integer.MAX_VALUE);
		final Yaml y = new Yaml(dOpt);
		y.dump(def, new PrintWriter(System.err));
	}
}
