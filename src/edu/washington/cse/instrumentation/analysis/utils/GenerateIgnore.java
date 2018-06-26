package edu.washington.cse.instrumentation.analysis.utils;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class GenerateIgnore {
	public static void main(final String[] args) {
		soot.Main.main(args);
		for(final SootClass cls : Scene.v().getApplicationClasses()) {
			for(final SootMethod m : cls.getMethods()) {
				System.out.println(m.getSignature());
			}
		}
	}
}
