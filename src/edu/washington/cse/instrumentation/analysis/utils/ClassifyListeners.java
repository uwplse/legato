package edu.washington.cse.instrumentation.analysis.utils;

import java.util.HashMap;
import java.util.Map;

import soot.FastHierarchy;
import soot.Scene;
import soot.SootClass;
import soot.Type;
import soot.options.Options;

public class ClassifyListeners {
	public static void main(final String[] args) {
		final Scene sc = Scene.v();
		final Options o = Options.v();
		o.set_soot_classpath(args[0]);
		o.set_allow_phantom_refs(true);
		o.prepend_classpath();
		o.set_output_format(Options.output_format_none);
		
		for(int i = 1; i < args.length; i++) {
			sc.addBasicClass(args[i]);
		}
		
		final Map<String, String> interface2Tags = new HashMap<>();
		
		interface2Tags.put("javax.servlet.ServletContextAttributeListener", "contextAttr");
		interface2Tags.put("javax.servlet.ServletContextListener", "context");
		
		interface2Tags.put("javax.servlet.http.HttpSessionListener", "session");
		interface2Tags.put("javax.servlet.http.HttpSessionAttributeListener", "sessionAttr");
		interface2Tags.put("javax.servlet.http.HttpSessionActivationListener", "sessionAct");
		
		interface2Tags.put("javax.servlet.ServletRequestListener", "request");
		interface2Tags.put("javax.servlet.ServletRequestAttributeListener", "requestAttr");
		
		for(final String intf : interface2Tags.keySet()) {
			sc.addBasicClass(intf);
		}
		
		sc.loadBasicClasses();
		
		final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		
		System.out.println(">>>");
		for(int i = 1; i < args.length; i++) {
			final SootClass listenerClass = Scene.v().getSootClass(args[i]);
			System.out.print(listenerClass);
			System.out.print(":");
			for(final Map.Entry<String, String> kv : interface2Tags.entrySet()) {
				final Type intfType = sc.getType(kv.getKey());
				if(fh.canStoreType(listenerClass.getType(), intfType)) {
					System.out.print(" ");
					System.out.print(kv.getValue());					
				}
			}
			System.out.println();
		}
		System.out.println(">>>");
	}
}
