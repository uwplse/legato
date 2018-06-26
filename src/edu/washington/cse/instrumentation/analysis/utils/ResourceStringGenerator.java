package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;

import soot.G;
import soot.PackManager;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;
import soot.jimple.toolkits.callgraph.reflection.ComposableReflectionHandlers;
import soot.jimple.toolkits.callgraph.reflection.PluggableReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.TypeStateReflectionHandler;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.jimple.toolkits.pointer.MemoryEfficientRasUnion;
import soot.jimple.toolkits.pointer.Union;
import soot.jimple.toolkits.pointer.UnionFactory;
import edu.washington.cse.instrumentation.analysis.resource.YamlResourceResolver;

public class ResourceStringGenerator {
	public static class DeferredHandler implements PluggableReflectionHandler {
		
		private final Class<? extends PluggableReflectionHandler> impl;
		private PluggableReflectionHandler delegate;

		public DeferredHandler(final Class<? extends PluggableReflectionHandler> impl) {
			this.impl = impl;
		}
		
		private PluggableReflectionHandler getDelegate() {
			if(this.delegate == null) {
				try {
					return this.delegate = this.impl.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					System.exit(1);
					return null;
				}
			} else {
				return this.delegate;
			}
		}

		@Override
		public boolean handleForNameCall(final SootMethod container, final Stmt s,
				final CallGraphBuilderBridge bridge) {
			return getDelegate().handleForNameCall(container, s, bridge);
		}

		@Override
		public boolean handleNewInstanceCall(final SootMethod container, final Stmt s,
				final CallGraphBuilderBridge bridge) {
			return getDelegate().handleNewInstanceCall(container, s, bridge);
		}

		@Override
		public boolean handleInvokeCall(final SootMethod container, final Stmt s,
				final CallGraphBuilderBridge bridge) {
			return getDelegate().handleInvokeCall(container, s, bridge);
		}

		@Override
		public boolean handleConstructorNewInstanceCall(final SootMethod container,
				final Stmt s, final CallGraphBuilderBridge bridge) {
			return getDelegate().handleConstructorNewInstanceCall(container, s, bridge);
		}

		@Override
		public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
			getDelegate().handleNewMethod(m, bridge);
		}
	}
	
	public static boolean isUniqueWithin(final Unit u, final SootMethod m) {
		for(final Unit u1 : m.getActiveBody().getUnits()) {
			if(u == u1) {
				continue;
			}
			if(u.toString().equals(u1.toString())) {
				return false;
			}
		}
		return true;
	}
	
	public static int getUnitTag(final Unit u, final SootMethod m) {
		int i = 0;
		for(final Unit u1 : m.getActiveBody().getUnits()) {
			if(u1 == u) {
				return i;
			}
			if(u1.toString().equals(u.toString())) {
				i++;
			}
		}
		throw new RuntimeException();
	}

	public static void main(final String[] args) {
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.rr-cache", new SceneTransformer() {
			@Override
			protected void internalTransform(final String phaseName, final Map<String, String> options) {
				final YamlResourceResolver yrr = new YamlResourceResolver(options.get("input"));
				final JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
				final List<Map<String, Object>> resolve = new ArrayList<>();
				{
					final List<String> repr = new ArrayList<>();
					for(final SootMethod acc : yrr.getResourceAccessMethods()) {
						repr.add(acc.getSignature());
					}
					final Map<String, Object> entry = new HashMap<>();
					entry.put("access-sigs", repr);
					resolve.add(entry);
				}
				for(final SootMethod access : yrr.getResourceAccessMethods()) {
					for(final Unit u : icfg.getCallersOf(access)) {
						final Stmt s = (Stmt) u;
						final InvokeExpr ie = s.getInvokeExpr();
						if(!yrr.isResourceAccess(ie, s)) {
							continue;
						}
						final Set<String> l = yrr.getAccessedResources(ie, u);
						final Map<String, Object> entry = new HashMap<>();
						final SootMethod methodOf = icfg.getMethodOf(u);
						entry.put("method", methodOf.getSignature());
						if(l == null) {
							entry.put("res", l);
						} else {
							final ArrayList<String> rList = new ArrayList<>();
							for(final String r : l) {
								rList.add(r);
							}
							entry.put("res", rList);
						}
						if(isUniqueWithin(u, methodOf)) {
							entry.put("unique", true);
						} else {
							entry.put("tag", getUnitTag(u, methodOf));
						}
						entry.put("unit", u.toString());
						resolve.add(entry);
					}
				}
				final DumperOptions dOptions = new DumperOptions();
				dOptions.setDefaultFlowStyle(FlowStyle.BLOCK);
				dOptions.setWidth(Integer.MAX_VALUE);
				final Yaml y = new Yaml(dOptions);
				try(PrintWriter pw = new PrintWriter(new File(options.get("output"))))  {
					y.dump(resolve, pw);
				} catch (final FileNotFoundException e) { }
				System.exit(0);
			}
		}) {
			{
				setDeclaredOptions("enabled input output");
			}
		});
		G.v().Union_factory = new UnionFactory() {
			@Override
			public Union newUnion() {
				return new MemoryEfficientRasUnion();
			}
		};
		ComposableReflectionHandlers.v().addHandler(new DeferredHandler(TypeStateReflectionHandler.class));
		soot.Main.main(args);
	}
}
