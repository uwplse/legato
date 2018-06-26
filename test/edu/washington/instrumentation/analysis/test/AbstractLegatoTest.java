package edu.washington.instrumentation.analysis.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import soot.G;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.internal.JReturnStmt;
import soot.jimple.toolkits.callgraph.TransitiveTargets;
import soot.options.Options;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.Sets;

import edu.washington.cse.instrumentation.analysis.AnalysisCompleteListener;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.SummaryMode;
import edu.washington.cse.instrumentation.analysis.InconsistentReadAnalysis;
import edu.washington.cse.instrumentation.analysis.InconsistentReadSolver;
import edu.washington.cse.instrumentation.analysis.Legato;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.Parser;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.instrumentation.analysis.test.iso.Isomorphism;

public abstract class AbstractLegatoTest {
	private final Set<String> testedMethods = new HashSet<>();
	private final Set<String> transitivelyTestedMethods = new HashSet<>();
	private Set<String> allTestClassMethods = null;
	protected Set<String> resultMethods = new HashSet<>();
	protected Map<String, RecTreeDomain> analysisResults;
	private final String analysisOptions;
	private final String testClass;
	
	protected boolean enableSyncHavoc = false;
	protected AtMostOnceProblem.SummaryMode mode = SummaryMode.IGNORE;
	
	public AbstractLegatoTest(final String testClass, final String analysisOptions) {
		this.testClass = testClass;
		this.analysisOptions = analysisOptions;
	}

	private void runAnalysisForMethod(final String testMethodName, final AnalysisCompleteListener l) {
		G.reset();
		Legato.standardSetup();
		AtMostOnceProblem.instance = null;
		
		final Scene s = Scene.v();
		s.setSootClassPath(System.getProperty("legato.classpath"));
		s.addBasicClass(testClass);
		s.loadNecessaryClasses();
		final SootClass ltKlass = s.getSootClass(testClass);
		s.setMainClass(ltKlass);
		if(allTestClassMethods == null) {
			allTestClassMethods = new HashSet<>();
			for(final SootMethod m : ltKlass.getMethods()) {
				allTestClassMethods.add(m.getName());
			}
		}
		final List<SootMethod> entryPoint = new ArrayList<>();
		final SootMethod testMethod= ltKlass.getMethodByName(testMethodName); 
		entryPoint.add(testMethod);
		s.setEntryPoints(entryPoint);
		PackManager.v().getPack("wjtp").add(new InconsistentReadAnalysis(new AnalysisCompleteListener() {
			@Override
			public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
				l.analysisCompleted(solver, problem);
				problem.dispose();
			}
		}));
		String optionString = "enabled:true," + analysisOptions;
		if(!enableSyncHavoc) {
			optionString += ",sync-havoc:false";
		}
		if(mode != SummaryMode.IGNORE) {
			optionString += ",summary-mode:" + mode;
		}
		Options.v().setPhaseOption("wjtp.ic-read", optionString);
		PackManager.v().runPacks();
	}
	
	private SootMethod getTestMethodByName(final String nm) {
		return Scene.v().getMainClass().getMethodByNameUnsafe(nm);
	}
	
	public void assertAnalysisThrows(final String testMethod, final Class<?>... exceptions) {
		try {
			runAnalysisForMethod(testMethod, new AnalysisCompleteListener() {
				@Override
				public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) { }
			});
		} catch(final Throwable t) {
			// clear the interrupted flag that can be set if the analysis proper throws an exception
			// (this is a real thing...)
			Thread.interrupted();
			final Class<? extends Throwable> tClass = t.getClass();
			for(final Class<?> kls : exceptions) {
				if(tClass == kls) {
					testedMethods.add(testMethod);
					return;
				}
			}
			Assert.fail("Expected analysis of: " + testMethod + " to throw one of " + Arrays.toString(exceptions) + ", got: " + t);
		}
		Assert.fail("Expected analysis for " + testMethod + " to fail");
	}
	
	private RecTreeDomain runReturnAnalysisForMethod(final String testMethodName) {
		System.out.println(">>>> Analyzing: " + testMethodName);
		testedMethods.add(testMethodName);
		final RecTreeDomain[] ref = new RecTreeDomain[1];
		runAnalysisForMethod(testMethodName, new AnalysisCompleteListener() {
			@Override
			public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
				for(final Unit u : getTestMethodByName(testMethodName).getActiveBody().getUnits()) {
					if(u instanceof JReturnStmt) {
						final JReturnStmt jReturnStmt = (JReturnStmt) u;
						assert jReturnStmt.getOp() instanceof Local;
						assert ref[0] == null;
						final Local retLoc = (Local)jReturnStmt.getOp();
						for(final Map.Entry<AccessGraph, RecTreeDomain> kv : solver.resultsAt(jReturnStmt).entrySet()) {
							if(kv.getKey().baseMatches(retLoc) && kv.getKey().getFieldCount() == 0) {
								ref[0] = kv.getValue();
								break;
							}
						}
					}
				}
			}
		});
		final TransitiveTargets tt = new TransitiveTargets(Scene.v().getCallGraph());
		final Iterator<MethodOrMethodContext> it = tt.iterator(Scene.v().getSootClass(testClass).getMethodByNameUnsafe(testMethodName));
		while(it.hasNext()) {
			final SootMethod m = it.next().method();
			if(m.getDeclaringClass().getName().equals(testClass)) {
				transitivelyTestedMethods.add(m.getName());
			}
		}
		return ref[0];
	}
	
	private Map<String,RecTreeDomain> runAnalysisForMethod(final String testMethodName) {
		final Map<String,RecTreeDomain> toReturn = new HashMap<>();
		runAnalysisForMethod(testMethodName, new AnalysisCompleteListener() {
			@Override
			public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
				final SootMethod m = Scene.v().getMainClass().getMethodByNameUnsafe(testMethodName);
				final Map<String, Local> methodToLocal = new HashMap<>();
				for(final Unit u : m.getActiveBody().getUnits()) {
					if(u instanceof ReturnVoidStmt) {
						for(final Map.Entry<String, Local> kv : methodToLocal.entrySet()) {
							toReturn.put(kv.getKey(), solver.resultAt(u, new AccessGraph(kv.getValue(), kv.getValue().getType())));
						}
						continue;
					}
					if(!(u instanceof AssignStmt)) {
						continue;
					}
					final AssignStmt as = (AssignStmt) u;
					if(!(as.getLeftOp() instanceof Local)) { 
						continue;
					}
					if(!as.containsInvokeExpr()) {
						continue;
					}
					final InvokeExpr ie = as.getInvokeExpr();
					final String mName = ie.getMethod().getName();
					if(mName.equals("get") || ie.getMethod().isConstructor()) {
						continue;
					}
					if(methodToLocal.containsKey(mName)) {
						throw new RuntimeException("Duplicate call to: " + mName);
					}
					methodToLocal.put(mName, ((Local)as.getLeftOp()));
				}
			}
		});
		return toReturn;
	}
	
	private boolean isResultApiTest = false;
	
	protected void runAnalyses(final String testMethod) {
		this.analysisResults = runAnalysisForMethod(testMethod);
		this.resultMethods.addAll(analysisResults.keySet());
		isResultApiTest = true;
	}
	
	@Test(dependsOnGroups={"legatoTests.*"})
	public void warnUntestedMethod() {
		if(isResultApiTest) {
			resultMethods.removeAll(testedMethods);
			Assert.assertTrue(resultMethods.isEmpty(), "Untested method(s): " + resultMethods);
		} else if(!testedMethods.isEmpty()) {
			final Set<String> ignoredTestMethods = testMethods();
			final Set<String> testClassMethodNames = new HashSet<>(allTestClassMethods);
			testClassMethodNames.removeAll(ignoredTestMethods);
			testClassMethodNames.removeAll(testedMethods);
			testClassMethodNames.removeAll(transitivelyTestedMethods);
			Assert.assertTrue(testClassMethodNames.isEmpty(), "Expected to find test(s) for: " + testClassMethodNames);
		}
	}
	
	protected Set<String> testMethods() {
		return Sets.newHashSet("get", "main", "<init>", "<clinit>", "nondetBool");
	}
	
	private void assertIsomorphicTree(final RecTreeDomain r, final String repr, final String m, final String... points) {
		Assert.assertNotNull(r, "Received null analysis result for method: " + m);
		Assert.assertNotSame(r, RecTreeDomain.BOTTOM, "Expected non-bottom  for method: " + m);
		final Parser p = new Parser();
		final Node n = p.parse(repr);
		if(points.length == 0) {
			Assert.assertNotNull(r.restRoot, "Expected rest tree result");
			Assert.assertNull(r.pointwiseRoots, "Unexpected pointwise results");
			final boolean iso = Isomorphism.isomorphic(r.restRoot, n);
			Assert.assertTrue(iso, "Expected isomorphic to: " + n + ", got " + r.restRoot + " for " + m);
		} else {
			Assert.assertNull(r.restRoot, "Unexpected rest result");
			Assert.assertNotNull(r.pointwiseRoots, "Expected pointwise result");
			for(final String point : points) {
				Assert.assertTrue(r.pointwiseRoots.containsKey(point), "Expected to find point: " + point);
				final boolean iso = Isomorphism.isomorphic(n, r.pointwiseRoots.get(point));
				Assert.assertTrue(iso, "Expected isomorphic to: " + n + ", got " + r.pointwiseRoots.get(point));
			}
		}
		
	}
	
	private void assertNotBottomTree(final RecTreeDomain r, final String m, final String... points) {
		Assert.assertNotNull(r, "Received null analysis result for method: " + m);
		Assert.assertNotSame(r, RecTreeDomain.BOTTOM, "Did not expect bottom result for: " + m);
		if(points.length > 0) {
			Assert.assertNotNull(r.pointwiseRoots, "Expected pointwise result");
			for(final String p : points) {
				Assert.assertTrue(r.pointwiseRoots.containsKey(p), "Expected pointwise result: " + p);
			}
		}
	}
	
	private void assertBottomTree(final RecTreeDomain r, final String m) {
		Assert.assertNotNull(r, "Received null analysis result for: " + m);
		Assert.assertSame(r, RecTreeDomain.BOTTOM);
	}
	
	// result API
	
	public void assertIsomorphicTreeResult(final String m, final String repr) {
		final RecTreeDomain r = analysisResults.get(m);
		assertIsomorphicTree(r, repr, m);
		testedMethods.add(m);
	}
	
	public void assertBottomResult(final String m) {
		final RecTreeDomain r = analysisResults.get(m);
		assertBottomTree(r, m);
		testedMethods.add(m);
	}
	
	public void assertNotBottomResult(final String m) {
		final RecTreeDomain r = analysisResults.get(m);
		assertNotBottomTree(r, m);
		testedMethods.add(m);
	}
	
	// return API
	
	public void assertBottomReturn(final String methodName) {
		assertBottomTree(runReturnAnalysisForMethod(methodName), methodName);
	}
	
	public void assertIsomorphicTreeReturn(final String methodName, final String repr) {
		assertIsomorphicTree(runReturnAnalysisForMethod(methodName), repr, methodName);
	}
	
	public void assertIsomorphicTreeReturn(final String methodName, final String repr, final String... points) {
		assertIsomorphicTree(runReturnAnalysisForMethod(methodName), repr, methodName, points);
	}
	
	public void assertNotBottomReturn(final String m) {
		assertNotBottomTree(runReturnAnalysisForMethod(m), m);
	}
	
	public void assertNotBottomReturn(final String m, final String... points) {
		assertNotBottomTree(runReturnAnalysisForMethod(m), m, points);
	}
	
	public void assertTopReturn(final String string) {
		final RecTreeDomain d = runReturnAnalysisForMethod(string);
		Assert.assertNull(d, "Found analysis result for method: " + string + " where none was expected");
	}
}
