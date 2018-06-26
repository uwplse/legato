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

import soot.Body;
import soot.BodyTransformer;
import soot.PackManager;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.toolkits.scalar.Pair;

public class SubsonicGenerator {
	public static void main(final String[] args) {
		final List<Object> def = new ArrayList<>();
		final Set<SootMethod> accessMethods = new HashSet<>();
		PackManager.v().getPack("jtp").add(new Transform("jtp.subsonic", new BodyTransformer() {
			@Override
			protected void internalTransform(final Body b, final String phaseName,
					final Map<String, String> options) {
				final SootMethod m = b.getMethod();
				if(!m.getName().startsWith("get")) {
					return;
				}
				if(!m.isPublic()) {
					return;
				}
				final Map<Pair<Unit, Integer>, String> accesses = new HashMap<>(); 
				for(final Unit u : b.getUnits()) {
					final Stmt s = (Stmt) u;
					if(s.containsInvokeExpr() && s.getInvokeExpr() instanceof InstanceInvokeExpr) {
						final InstanceInvokeExpr instanceCall = (InstanceInvokeExpr) s.getInvokeExpr();
						final String methodName = instanceCall.getMethod().getName();
						final String baseType = instanceCall.getBase().getType().toString();
						final boolean isAccess = (baseType.equals("java.util.Properties") && methodName.equals("getProperty")) ||
									(baseType.equals("net.sourceforge.subsonic.service.SettingsService") && methodName.equals("getInt")) ||
									(baseType.equals("net.sourceforge.subsonic.service.SettingsService") && methodName.equals("getLong")) ||
									(baseType.equals("net.sourceforge.subsonic.service.SettingsService") && methodName.equals("getBoolean"));
						if(isAccess) {
							if(!(instanceCall.getArg(0) instanceof StringConstant)) {
								System.out.println("Nope 2: " + b.getMethod());
								return;
							}
							final String accessed = ((StringConstant)instanceCall.getArg(0)).value;
							accesses.put(new Pair<Unit,Integer>(u, ResourceStringGenerator.getUnitTag(u, m)), accessed);
							accessMethods.add(instanceCall.getMethod());
							accessMethods.add(m);
						}
					}
				}
				if(accesses.isEmpty()) {
					return;
				}
				if(accesses.size() == 1) {
					final Map<String, Object> entry = new HashMap<>();
					entry.put("sig", m.getSignature());
					entry.put("resources", new ArrayList<String>(accesses.values()));
					def.add(entry);
				} else {
					for(final Map.Entry<Pair<Unit, Integer>, String> kv : accesses.entrySet()) {
						final Map<String, Object> entry = new HashMap<>();
						entry.put("container", m.getSignature());
						entry.put("unit", kv.getKey().getO1().toString());
						entry.put("tag", kv.getKey().getO2());
						entry.put("resources", Collections.singletonList(kv.getValue()));
						def.add(entry);
					}
				}
			}
		}));
		soot.Main.main(args);
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
