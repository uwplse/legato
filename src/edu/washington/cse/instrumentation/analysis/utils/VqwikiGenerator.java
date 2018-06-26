package edu.washington.cse.instrumentation.analysis.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.BooleanType;
import soot.IntType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.util.NumberedString;
import soot.util.StringNumberer;

public class VqwikiGenerator {
	public static void main(final String[] args) {
		soot.Main.main(args);
		final StringNumberer sn = Scene.v().getSubSigNumberer();
		final NumberedString stringSetting = sn.findOrAdd("java.lang.String getStringSetting(java.lang.String)");
		final NumberedString booleanSetting = sn.findOrAdd("boolean getBooleanSetting(java.lang.String)");
		final NumberedString intSetting = sn.findOrAdd("int getIntSetting(java.lang.String)");
		
		final List<String> accessMethods = new ArrayList<>();
		final List<Map<String, Object>> toOutput = new ArrayList<>();
		{
			final Map<String, Object> accessEntry = new HashMap<>();
			accessEntry.put("access-sigs", accessMethods);
			toOutput.add(accessEntry);
		}
		
		for(final SootClass cls : Scene.v().getApplicationClasses()) {
			assert cls.getName().equals("vqwiki.Environment") : cls;
			method_loop: for(final SootMethod m : cls.getMethods()) {
				if(!m.isPublic()) {
					continue;
				}
				if(!m.isConcrete()) {
					continue;
				}
				final Type returnType = m.getReturnType();
				if(returnType != IntType.v() && returnType != BooleanType.v() && returnType != RefType.v("java.lang.String")) {
					continue;
				}
				final String methodName = m.getName();
				if(!methodName.startsWith("get") && !methodName.startsWith("is")) {
					continue;
				}
				final Body b = m.retrieveActiveBody();
				String currName = null;
				Type propType = null;
				for(final Unit u : b.getUnits()) {
					final Stmt s = (Stmt) u;
					if(!s.containsInvokeExpr()) {
						continue;
					}
					final InvokeExpr ie = s.getInvokeExpr();
					final NumberedString subSig = ie.getMethodRef().getSubSignature();
					if(subSig != stringSetting && subSig != booleanSetting && subSig != intSetting) {
						continue;
					}
					if(!(ie.getArg(0) instanceof StringConstant)) {
						continue;
					}
					final StringConstant sc = (StringConstant) ie.getArg(0);
					if(currName != null && !sc.value.equals(currName)) {
						continue method_loop;
					}
					if(propType != null && ie.getMethodRef().returnType() != propType) {
						continue method_loop;
					}
					propType = ie.getMethodRef().returnType();
					currName = sc.value;
				}
				if(propType != null && currName != null && propType == m.getReturnType()) {
					accessMethods.add(m.getSignature());
					{
						final Map<String, Object> entry = new HashMap<>();
						entry.put("sig", m.getSignature());
						entry.put("resources", Collections.singletonList(currName));
						toOutput.add(entry);
					}
				}
			}
		}
		YamlUtil.dumpYaml(toOutput);
	}
}
