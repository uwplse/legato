package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import soot.Scene;
import soot.SootClass;
import soot.options.Options;

public class CountPluginImplementers {
	public static void main(final String[] args) throws IOException {
		final Options o = Options.v();
		o.set_soot_classpath(args[0]);
		o.set_allow_phantom_refs(true);
		o.set_process_dir(Arrays.asList(args[0].split(File.pathSeparator)));
		Scene.v().loadNecessaryClasses();
		final SootClass pluginClass = Scene.v().getSootClass("org.blojsom.plugin.Plugin");
		int count = 0;
		for(final SootClass cls : Scene.v().getApplicationClasses()) {
			if(implementsPlugin(cls, pluginClass)) {
				count++;
			}
		}
		System.out.println(count);
	}
	
	private static boolean implementsPlugin(SootClass cls, final SootClass pluginClass) {
		while(cls != null) {
			if(cls.getInterfaces().contains(pluginClass)) {
				return true;
			}
			if(cls.hasSuperclass()) {
				cls = cls.getSuperclass();
			} else {
				cls = null;
			}
		}
		return false;
	}
}
