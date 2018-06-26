package edu.washington.cse.instrumentation.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;

public class LegatoConfigurer {
	private final List<String> appPackages;
	private final String includeExcludeFile;
	private final String cp;
	private final String mainClass;

	public LegatoConfigurer(final String includeExcludeFile, final List<String> appPackages, final String mainClass, final String cp) {
		this.includeExcludeFile = includeExcludeFile;
		this.appPackages = appPackages;
		this.cp = cp;
		this.mainClass = mainClass;
	}
	
	public void doConfigure(final boolean useSpark) {
		final Options o = Options.v();
		Legato.standardSetup(useSpark);
		
		if(includeExcludeFile != null) {
			parseIncludeExclude(o, includeExcludeFile);
		}
		
		Scene.v().addBasicClass(mainClass);
		o.set_soot_classpath(cp);
		o.set_main_class(mainClass);
		if(useSpark) {		
			o.setPhaseOption("cg", "types-for-invoke:true");
		}
		o.set_no_bodies_for_excluded(true);
		o.set_prepend_classpath(true);
		o.set_dynamic_package(appPackages);
	}
	
	public static void parseIncludeExclude(final Options o, final String inclExclFile) {
		final List<String> include = new ArrayList<>();
		final List<String> exclude = new ArrayList<>();
		try(BufferedReader br = new BufferedReader(new FileReader(new File(inclExclFile)))) {
			String l = null;
			while((l = br.readLine()) != null) {
				if(l.trim().isEmpty()) {
					continue;
				}
				if(l.startsWith("-")) {
					exclude.add(l.substring(1));
				} else if(l.startsWith("+")) {
					include.add(l.substring(1));
				} else {
					throw new RuntimeException("Illegal line format: " + l);
				}
			}
		} catch (final IOException e) { }
		G.v().out.println("Include: " + include);
		G.v().out.println("Exclude: " + exclude);
		o.set_include(include);
		o.set_exclude(exclude);
	}

	public SootMethod configureEntryPoints() {
		final Scene s = Scene.v();
		final SootClass ltKlass = s.getSootClass(mainClass);
		s.setMainClass(ltKlass);
		final List<SootMethod> entryPoint = new ArrayList<>();
		final SootMethod mainMethod = ltKlass.getMethodByName("main"); 
		entryPoint.add(mainMethod);
		s.setEntryPoints(entryPoint);
		return mainMethod;
	}
}
