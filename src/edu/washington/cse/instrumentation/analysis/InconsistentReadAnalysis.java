package edu.washington.cse.instrumentation.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import soot.G;
import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.PointsToAnalysis;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.spark.pag.PAG;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.pointer.MemoryEfficientRasUnion;
import soot.jimple.toolkits.pointer.Union;
import soot.jimple.toolkits.pointer.UnionFactory;
import soot.util.queue.QueueReader;
import boomerang.preanalysis.NopTransformer;
import edu.washington.cse.instrumentation.analysis.utils.MethodResolver;

public class InconsistentReadAnalysis extends Transform {
	public InconsistentReadAnalysis(final AnalysisCompleteListener l) {
		this(l, new NullAnalysisModelExtension());
	}
		
	public InconsistentReadAnalysis(final AnalysisCompleteListener l, final AnalysisModelExtension ext) {
		super("wjtp.ic-read", new SceneTransformer() {
			@Override
			protected void internalTransform(final String phaseName, final Map<String, String> options) {
				if(options.containsKey("time") && Boolean.parseBoolean(options.get("time"))) {
					Legato.totalTime.start();
				}
				final AnalysisConfiguration config = AnalysisConfiguration.parseConfiguration(options, ext);
				ext.postProcessScene();
				{
					final PointsToAnalysis pointsToAnalysis = Scene.v().getPointsToAnalysis();
					if(pointsToAnalysis instanceof PAG) {
						((PAG) pointsToAnalysis).cleanPAG();
					}
					if(!AnalysisConfiguration.VERY_QUIET) {
						System.out.println("Preloading method bodies and stripping bytecode...");
					}
					final HashSet<Unit> megamorphicCallsites = new HashSet<>();
					final LegatoEdgePredicate synth = new LegatoEdgePredicate();
					final ReachableMethods rm = new ReachableMethods(Scene.v().getCallGraph(), Scene.v().getEntryPoints().iterator(), new Filter(synth));
					rm.update();
					
					int isMultiDispatch = 0;
					int noMultiDispatch = 0;
					int nMethods = 0;
					for(final QueueReader<MethodOrMethodContext> it = rm.listener(); it.hasNext(); ) {
						final SootMethod m = it.next().method();
						nMethods++;
						m.freeze();
						if((nMethods % 1000) == 0 && !AnalysisConfiguration.VERY_QUIET) {
							System.out.println("  ..." + nMethods);
						}
						if(!m.hasActiveBody()) {
							continue;
						}
						config.icfg.getOrCreateUnitGraph(m);
						m.setSource(null);
						for(final Unit u : config.icfg.getCallsFromWithin(m)) {
							final Stmt s = (Stmt)u;
							final String methodName = s.getInvokeExpr().getMethod().getName();
							if(methodName.equals("forName") || methodName.equals("class$")) {
								continue;
							}
							final Collection<SootMethod> calls = config.icfg.getCalleesOfCallAt(u);
							if(calls.size() == 0) {
								continue;
							} else if(calls.size() == 1) {
								noMultiDispatch++;
							} else {
								if(calls.size() > 5) {
									megamorphicCallsites.add(u);
								}
								isMultiDispatch++;
							}
						}
					}
					if(!AnalysisConfiguration.VERY_QUIET) {
						System.out.println("...Done");
						System.out.println("Call graph methods: " + nMethods);
						System.out.println(isMultiDispatch + " vs " + noMultiDispatch);
						for(final Unit u : megamorphicCallsites) {
							System.out.println("Mega-morphic call site: " + u + " in " + config.icfg.getMethodOf(u) + " callees: " + config.icfg.getCalleesOfCallAt(u).size());
						}
					}
				}
				final AtMostOnceProblem prob = new AtMostOnceProblem(config);
				final InconsistentReadSolver solver = new InconsistentReadSolver(prob, config);
				prob.setSolver(solver);
				try {
					solver.solve();
				} catch(final RuntimeException e) {
					RuntimeException eIt = e;
					while(eIt.getCause() != null) {
						final Throwable t = eIt.getCause();
						if(t instanceof RuntimeException) {
							eIt = (RuntimeException) t;
						} else {
							throw eIt;
						}
					}
					if(eIt instanceof EverythingIsInconsistentException) {
						throw eIt;
					} else {
						throw e;
					}
				}
				l.analysisCompleted(solver, prob);
				if(config.warnLog != null) { 
					try(PrintStream ps = new PrintStream(new File(config.warnLog))) {
						prob.printWarnings(ps);
					} catch (final FileNotFoundException e) { 
						prob.printWarnings(System.out);
					}
				} else {
					prob.printWarnings(System.out);
				}
				solver.finishHandler();
				if(config.trackSites) {
					try(PrintWriter pw = new PrintWriter(new File(config.siteLog != null ? config.siteLog : "sites.yml"))) {
						solver.dumpRelevantSites(pw);
					} catch (final FileNotFoundException e) { }
				}
				if(options.containsKey("time") && Boolean.parseBoolean(options.get("time"))) {
					Legato.totalTime.stop();	
				}
			}
		});
		
		setDeclaredOptions("enabled resolver resolver-options pm pm-options sync-havoc summary-mode output " +
				"hofe output-opt warn-log track-all ignore-file track-sites site-log ignore-file stats time");
		setDefaultOptions("resolver:simple-get pm:simple sync-havoc:true output:console track-all:false track-sites:false stats:false time:false");
	}
	
	public static void main(final String[] args) {
		PackManager.v().getPack("wjtp").add(new InconsistentReadAnalysis(new AnalysisCompleteListener() {
			@Override
			public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
				final HashSet<SootMethod> x = new HashSet<>();
				{
					for(final Edge e : Scene.v().getCallGraph()) {
						final MethodOrMethodContext mc1 = e.getSrc();
						final MethodOrMethodContext mc2 = e.getTgt();
						assert mc1 instanceof SootMethod;
						assert mc2 instanceof SootMethod;
						x.add((SootMethod) mc1);
						x.add((SootMethod) mc2);
					}
				}
				for(final SootMethod m : x) {
					if(m.equals(Scene.v().getMainMethod())) {
						continue;
					}
					System.out.println("!!! " + m.getSignature());
					if(!m.isConcrete()) {
						System.out.println("[[ NO BODY ]]");
						continue;
					}
					for(final Unit u : m.getActiveBody().getUnits()) {
						System.out.println(u.toString() + " -> " + solver.resultsAt(u));	
					}
				}
				System.out.println("main");
				for(final Unit u : Scene.v().getMainMethod().getActiveBody().getUnits()) {
					System.out.println(u.toString() + " -> " + solver.resultsAt(u));
				}
				Legato.dumpData(solver, problem);
			}
		}));
		PackManager.v().getPack("jb").add(new Transform("jb.nop-adder", new NopTransformer()));
		PackManager.v().getPack("jb").add(new Transform("jb.mres", new MethodResolver()));
		G.v().Union_factory = new UnionFactory() {
			@Override
			public Union newUnion() {
				return new MemoryEfficientRasUnion();
			}
		};
		soot.Main.main(args);
	}
}
