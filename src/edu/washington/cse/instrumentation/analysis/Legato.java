package edu.washington.cse.instrumentation.analysis;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.management.MemoryMXBean;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.yaml.snakeyaml.Yaml;

import soot.Body;
import soot.FastHierarchy;
import soot.G;
import soot.Kind;
import soot.Local;
import soot.Modifier;
import soot.PackManager;
import soot.PatchingChain;
import soot.PhaseOptions;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Timers;
import soot.Transform;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JNopStmt;
import soot.jimple.internal.JReturnVoidStmt;
import soot.jimple.internal.JStaticInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.jimple.toolkits.callgraph.VirtualCalls;
import soot.jimple.toolkits.callgraph.reflection.AbstractReflectionHandler;
import soot.jimple.toolkits.callgraph.reflection.CallGraphBuilderBridge;
import soot.jimple.toolkits.callgraph.reflection.ComposableReflectionHandlers;
import soot.jimple.toolkits.callgraph.reflection.TypeStateReflectionHandler;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.jimple.toolkits.pointer.MemoryEfficientRasUnion;
import soot.jimple.toolkits.pointer.Union;
import soot.jimple.toolkits.pointer.UnionFactory;
import soot.options.Options;
import soot.toolkits.scalar.Pair;
import boomerang.preanalysis.NopTransformer;

import com.google.common.base.Stopwatch;

import edu.washington.cse.instrumentation.analysis.aliasing.AliasResolver;
import edu.washington.cse.instrumentation.analysis.preanalysis.ConstantStringInliner;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;
import edu.washington.cse.instrumentation.analysis.utils.ApplicationClassInference;
import edu.washington.cse.instrumentation.analysis.utils.ApplicationClassInference.Results;
import edu.washington.cse.instrumentation.analysis.utils.InlineTargetFinder;
import edu.washington.cse.instrumentation.analysis.utils.JspUrlRouter;
import edu.washington.cse.instrumentation.analysis.utils.MethodResolver;
import edu.washington.cse.instrumentation.analysis.utils.YamlUtil;

public class Legato {
	public static Stopwatch totalTime = Stopwatch.createUnstarted();
	
	public static void dumpData(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
		try(PrintWriter pw = new PrintWriter(new File("sites.log"))) {
			problem.dumpResourceSites(pw);
		} catch (final IOException e) { }
		try(PrintWriter pw = new PrintWriter(new File("incoming.log"))) {
			solver.dumpIncoming(pw);	
		} catch(final IOException e) {}
		problem.dumpStats();
	}
	
	public static void standardSetup(final boolean useSpark) { 
		G.v().Union_factory = new UnionFactory() {
			@Override
			public Union newUnion() {
				return new MemoryEfficientRasUnion();
			}
		};
		final Options o = Options.v();
		o.set_allow_phantom_refs(true);
		o.set_whole_program(true);
		o.setPhaseOption("jb.dae", "off");
		if(useSpark) {
			o.setPhaseOption("cg.spark", "on");
			o.setPhaseOption("cg.spark", "string-constants:true");
		} else {
			o.setPhaseOption("cg.cha", "on");
		}
		o.setPhaseOption("cg", "safe-newinstance:false,safe-forname:false");
		o.set_output_format(Options.output_format_none);
		PackManager.v().getPack("jb").add(new Transform("jb.nop-adder", new NopTransformer()));
		PackManager.v().getPack("jb").add(new Transform("jb.mres", new MethodResolver()));
	}
	
	public static void standardSetup() {
		standardSetup(true);
	}
	
	
	// maybe we should use the negative examples param too for extra confidence
	public static List<String> findApplicationPackages(final String appDir) throws IOException {
		final List<String> classNames = collectApplicationClasses(appDir);
		final Results inference = ApplicationClassInference.inferApplicationClasses(classNames.iterator(), Collections.<String>emptyIterator());
		return new ArrayList<>(inference.applicationClassPrefixes);
	}

	public static List<String> collectApplicationClasses(final String appDir) throws IOException {
		final List<String> classNames = new ArrayList<>();
		for(final String p : appDir.split(File.pathSeparator)) {
			final Path initialPath = new File(p).toPath();
			Files.walkFileTree(initialPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
					if(!file.toString().endsWith(".class")) {
						return FileVisitResult.CONTINUE;
					}
					final Path rel = initialPath.relativize(file);
					final String fileName = rel.toString();
					classNames.add(fileName.substring(0, fileName.length() - 6).replace('/', '.'));
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return classNames;
	}
	
	@SuppressWarnings("unchecked")
	public static void main(final String[] inArgs) throws IOException, InterruptedException {
		final OptionParser parser = new OptionParser("s:r:m::t:ce:h");
		final OptionSet parse = parser.parse(inArgs);
		final String[] args = parse.nonOptionArguments().toArray(new String[0]);
		final boolean dumpStatsOnly = parse.hasArgument("s");
		final String libDirs = args[0];
		final String appDir = args[1];
		final String propagationFile = args[2];
		final String mainClass = args[4];
		final Thread memRecorder;
		final long[] maxMemory = new long[]{-1};
		if(parse.has("m")) {
			memRecorder = new Thread(new Runnable() {
				@Override
				public void run() {
					final MemoryMXBean memoryBean = sun.management.ManagementFactoryHelper.getMemoryMXBean();
					while(true) {
						maxMemory[0] = Math.max(maxMemory[0], memoryBean.getHeapMemoryUsage().getUsed());
						try {
							Thread.sleep(1000);
						} catch (final InterruptedException e) {
							break;
						}
					}
				}
			});
			memRecorder.start();
		} else {
			memRecorder = null;
		}
		final String resolver, resourceFile;
		if(args[3].contains(File.pathSeparator)) {
			final String[] split = args[3].split(File.pathSeparator);
			resolver = split[0];
			resourceFile = split[1];
		} else {
			resolver = "yaml-file";
			resourceFile = args[3];
		}
		final boolean useJsp = parse.has("r");
		final ScheduledExecutorService timeoutExec;
		{
			timeoutExec = Executors.newSingleThreadScheduledExecutor();
			timeoutExec.schedule(new Runnable() {				
				@Override
				public void run() {
					try {
						dumpPerformanceInfo(parse, memRecorder, maxMemory[0]);
						System.exit(11);
					} catch (final IOException | InterruptedException e) { }
				}
			}, Integer.parseInt(System.getProperty("legato.budget", "15")), TimeUnit.MINUTES);
		}
		totalTime.start();
		final List<String> appPackages = findApplicationPackages(appDir);
		
		final String includeExcludeFile = parse.has("e") ? ((String)parse.valueOf("e")) : null;
		final String cp = constructClassPath(libDirs, appDir);
		final LegatoConfigurer lc = new LegatoConfigurer(includeExcludeFile, appPackages, mainClass, cp);
		
		final List<Pair<String, Integer>> unrollTargets;
		
		{
			final List<Pair<String, Integer>> indirectFlowSites = new ArrayList<>();
			indirectFlowSites.add(new Pair<>("<java.lang.Class: java.lang.Class forName(java.lang.String)>", 0));
			if(useJsp) {
				indirectFlowSites.add(new Pair<>("<javax.servlet.ServletContext: javax.servlet.RequestDispatcher getRequestDispatcher(java.lang.String)>",0));
				indirectFlowSites.add(new Pair<>("<javax.servlet.ServletRequest: javax.servlet.RequestDispatcher getRequestDispatcher(java.lang.String)>",0));
				indirectFlowSites.add(new Pair<>("<javax.servlet.ServletContext: javax.servlet.RequestDispatcher getNamedDispatcher(java.lang.String)>",0));
			}
			if(resolver.equals("yaml-file") || resolver.equals("hybrid")) {
				final List<Map<String, Object>> loaded = YamlUtil.unsafeLoadFromFile(resourceFile);
				for(final Map<String, Object> m : loaded) {
					if(!m.containsKey("sig") || !m.containsKey("pos")) {
						continue;
					}
					indirectFlowSites.add(new Pair<String, Integer>((String)m.get("sig"), (Integer) m.get("pos")));
				}
			}
			if(!AnalysisConfiguration.VERY_QUIET) {
				System.out.println("Searching for unrollings of: ");
				for(final Pair<String, Integer> sp : indirectFlowSites) {
					System.out.println(" - " + sp);
				}
			}
			unrollTargets = InlineTargetFinder.findUnrollTargets(lc, indirectFlowSites);
			if(!AnalysisConfiguration.VERY_QUIET) {
				System.out.println("Will unroll:");
				for(final Pair<String, Integer> ut : unrollTargets) {
					System.out.println(" * " + ut);
				}
			}
		}
		
		lc.doConfigure(!parse.has("h"));
		final Scene s = Scene.v();
		
		// handle the JSP model and the router
		if(useJsp) {
			PackManager.v().getPack("jb").add(new Transform("jb.jsp-remove", new JasperTransformer()));
			PackManager.v().getPack("jb").add(new Transform("jb.jsp-simple", new JSPSimplification()));
			setupJspModel((String) parse.valueOf("r"));
		}
		
		final AnalysisModelExtension ame = new AttributeModelExtension();
		ame.setupScene(s);
		
		s.loadNecessaryClasses();
		final SootMethod mainMethod = lc.configureEntryPoints();
		
		if(ame.supportsCodeRewrite()) {
			ComposableReflectionHandlers.v().addHandler(ame.getReflectionHandler());
		}
		
		if(dumpStatsOnly) {
			 PackManager.v().getPack("wjtp").add(new Transform("wjtp.stats", new SceneTransformer() {
				@Override
				protected void internalTransform(final String phaseName, final Map<String, String> options) {
					final Set<SootMethod> nMethods = new HashSet<>();
					final Set<SootClass> nClasses = new HashSet<>();
					int edges = 0;
					final LegatoEdgePredicate pred = new LegatoEdgePredicate();
					final CallGraph cg = Scene.v().getCallGraph();
					final Set<SootMethod> concreteMethods = new HashSet<>();
					for(final Edge e : cg) { 
						if(!pred.want(e)) {
							continue;
						}
						edges++;
						nMethods.add(e.getSrc().method());
						nMethods.add(e.getTgt().method());
						if(e.getSrc().method().hasActiveBody()) {
							concreteMethods.add(e.getSrc().method());
						}
						if(e.getTgt().method().hasActiveBody()) {
							concreteMethods.add(e.getTgt().method());
						}
						
						nClasses.add(e.getSrc().method().getDeclaringClass());
						nClasses.add(e.getTgt().method().getDeclaringClass());
					}
					final JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
					final ResourceResolver rr = AnalysisConfiguration.parseResolver(icfg, resolver, resourceFile);
					final Set<String> nOptions = new HashSet<>();
					long irCount = 0L;
					for(final SootMethod m : concreteMethods) {
						assert m.hasActiveBody();
						irCount += m.retrieveActiveBody().getUnits().size();
						for(final Unit u : icfg.getCallsFromWithin(m)) {
							assert u instanceof Stmt;
							final Stmt stmt = (Stmt) u;
							assert stmt.containsInvokeExpr();
							final InvokeExpr ie = stmt.getInvokeExpr();
							if(!rr.isResourceAccess(ie, u)) {
								continue;
							}
							final Set<String> accessed = rr.getAccessedResources(ie, u);
							if(accessed == null) {
								nOptions.add("*");
							} else {
								nOptions.addAll(accessed);
							}
						}
					}
					final Yaml y = new Yaml();
					final Map<String, Object> out = new HashMap<>();
					out.put("edges", edges);
					out.put("classes", nClasses.size());
					out.put("methods", nMethods.size());
					out.put("resources", nOptions.size());
					out.put("stmts", irCount);
					try(FileWriter fos = new FileWriter(new File((String)parse.valueOf("s")))) {
						y.dump(out, fos);
					} catch (final IOException e1) { }
				}
			}));
		} else {
			PackManager.v().getPack("wjtp").add(new InconsistentReadAnalysis(new AnalysisCompleteListener() {
				@Override
				public void analysisCompleted(final InconsistentReadSolver solver, final AtMostOnceProblem problem) {
					if(!AnalysisConfiguration.VERY_QUIET) {
						dumpData(solver, problem);
					}
					System.out.println("Reports: " + solver.numReports());
					System.out.println("God's in his heaven\nall's right with the world");
				}
			}, ame));
			String option = "time:true,enabled:true,pm:yaml-file,resolver:" + resolver + ",pm-options:" + propagationFile + ",resolver-options:" + resourceFile;
			if(args.length > 5) {
				if(!args[5].isEmpty()) {
					option = option + "," + args[5];
				}
			}
			final Options o = Options.v();
			o.setPhaseOption("wjtp.ic-read", option);
			if(PhaseOptions.v().getPhaseOptions("wjtp.ic-read").containsKey("track-sites") &&
					Boolean.parseBoolean(PhaseOptions.v().getPhaseOptions("wjtp.ic-read").get("track-sites"))) { 
				o.set_keep_line_number(true);
			}
		}
		
		ComposableReflectionHandlers.v().addHandler(new TypeStateReflectionHandler());
		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() { 
			@Override
			public boolean handleForNameCall(final SootMethod container, final Stmt s, final CallGraphBuilderBridge bridge) {
				if(container.getName().equals("class$")) {
					return true;
				}
				return false;
			}
		});
		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
			// log4j is the worst
			@Override
			public boolean handleNewInstanceCall(final SootMethod container, final Stmt s, final CallGraphBuilderBridge bridge) {
				if(container.getDeclaringClass().getName().startsWith("org.apache.log4j")) {
					return true;
				}
				return false;
			}
			
			@Override
			public boolean handleInvokeCall(final SootMethod container, final Stmt s, final CallGraphBuilderBridge bridge) {
				if(container.getDeclaringClass().getName().startsWith("org.apache.log4j")) {
					return true;
				}
				return false;
			}
			
			@Override
			public boolean handleForNameCall(final SootMethod container, final Stmt s, final CallGraphBuilderBridge bridge) {
				if(container.getDeclaringClass().getName().startsWith("org.apache.log4j")) {
					return true;
				}
				return false;
			}
		});
		if(unrollTargets.size() > 0) {
			ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
				ConstantStringInliner inliner = new ConstantStringInliner(unrollTargets);
				
				@Override
				public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
					final Iterator<Unit> it = m.getActiveBody().getUnits().snapshotIterator();
					while(it.hasNext()) {
						final Unit u = it.next();
						if(!(u instanceof Stmt)) {
							continue;
						}
						final Stmt stmt = (Stmt) u;
						if(!stmt.containsInvokeExpr()) {
							continue;
						}
						inliner.rewriteCallSite(m, stmt);
					}
				}
			});
		}

		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
			@Override
			public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
				for(final Unit u : m.getActiveBody().getUnits()) {
					final Stmt s = (Stmt) u;
					if(!s.containsInvokeExpr()) {
						continue;
					}
					if(!(s.getInvokeExpr() instanceof VirtualInvokeExpr)) {
						continue;
					}
					final VirtualInvokeExpr vie = (VirtualInvokeExpr) s.getInvokeExpr();
					if(!(vie.getBase().getType() instanceof RefType)) {
						continue;
					}
					final RefType rt = (RefType) vie.getBase().getType();
					final SootClass cls = rt.getSootClass();
					if(!cls.isConcrete()) {
						continue;
					}
					final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
					if(fh.getSubclassesOf(rt.getSootClass()).size() == 0) {
						final SootMethod resolved = VirtualCalls.v().resolveNonSpecial(rt, s.getInvokeExpr().getMethodRef().getSubSignature());
						if(resolved != null) {
							bridge.addEdge(m, s, resolved, Kind.VIRTUAL);
						}
					}
				}
			}
		});
		
		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
			@Override
			public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
				if(!m.hasActiveBody()) {
					return;
				}
				m.unfreeze();
				if(m.getReturnType() == VoidType.v()) {
					return;
				}
				final Body body = m.getActiveBody();
				final LocalGenerator lg = new LocalGenerator(body);
				final Set<ReturnStmt> returnUnits = new HashSet<>();
				final PatchingChain<Unit> uChain = body.getUnits();
				for(final Unit u : uChain) {
					if(!(u instanceof ReturnStmt)) {
						continue;
					}
					final ReturnStmt rs = (ReturnStmt) u;
					returnUnits.add(rs);
				}
				if(returnUnits.size() == 1) {
					return;
				}
				final Local returnLocal = lg.generateLocal(m.getReturnType());
				final Jimple j = Jimple.v();
				
				final ReturnStmt finalReturn = j.newReturnStmt(returnLocal);
				uChain.addLast(finalReturn);
				
				for(final ReturnStmt rs : returnUnits) {
					final AssignStmt redirect = j.newAssignStmt(returnLocal, rs.getOp());
					redirect.addAllTagsOf(rs);
					uChain.swapWith(rs, redirect);
					uChain.insertAfter(j.newGotoStmt(finalReturn), redirect);
				}
			}
		});
		if(!parse.has("c")) {
			handleStaticInitModel(mainMethod);
		}
		totalTime.stop();
		PackManager.v().runPacks();
		timeoutExec.shutdownNow();
		dumpPerformanceInfo(parse, memRecorder, maxMemory[0]);
	}

	public static void dumpPerformanceInfo(final OptionSet parse, final Thread memRecorder, final long maxMemory) throws IOException, InterruptedException {
		if(parse.has("t")) {
			final String outFile = (String) parse.valueOf("t");
			final Map<String, Long> times = new HashMap<>();
			times.put("call-graph", Timers.v().callgraphTimer.getTime());
			times.put("analysis", Legato.totalTime.elapsed(TimeUnit.MILLISECONDS));
			times.put("alias", AliasResolver.getTotalAliasTime());
			final Yaml y = new Yaml();
			try(FileWriter fw = new FileWriter(new File(outFile))) {
				y.dump(times, fw);
			}
		} else if(!AnalysisConfiguration.VERY_QUIET) {
			System.out.println("Call-graph time: " + Timers.v().callgraphTimer.getTime());
			System.out.println("Analysis time: " + Legato.totalTime.elapsed(TimeUnit.MILLISECONDS));
			System.out.println("Total alias time: " + AliasResolver.getTotalAliasTime());
		}
		if(parse.has("m")) {
			assert memRecorder != null;
			memRecorder.interrupt();
			memRecorder.join();
			if(parse.hasArgument("m")) {
				try(PrintStream ps = new PrintStream(new File((String) parse.valueOf("m")))) {
					ps.print(maxMemory);
				}
			} else {
				System.out.println("Max memory consumption: " + maxMemory);
			}
		}
		if(AnalysisConfiguration.RECORD_MAX_PRIMES) {
			System.out.println("Max primes: " + InconsistentReadSolver.primeCounts.get());
		}
		if(AnalysisConfiguration.RECORD_MAX_K) {
			System.out.println("Max k-sensitivity: " + InconsistentReadSolver.maxKCount.get());
		}
	}

	public static String constructClassPath(final String libDirs, final String appDir) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append(appDir);
		if(!libDirs.isEmpty()) {
			for(final String d : libDirs.split(File.pathSeparator)) {
				if(d.endsWith("*")) {
					final String dirName = d.substring(0, d.length() - 1);
					Files.walkFileTree(new File(dirName).toPath(), new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
								throws IOException {
							if(!attrs.isRegularFile()) {
								return FileVisitResult.CONTINUE;
							}
							if(file.getFileName().toString().endsWith(".jar")) {
								sb.append(":").append(file.toAbsolutePath().toString());
							}
							return FileVisitResult.CONTINUE;
						}
					});
				} else {
					sb.append(d);
				}
			}
		}
		return sb.toString();
	}

	private static void setupJspModel(final String routingFile) throws IOException, FileNotFoundException {
		Scene.v().addBasicClass("org.apache.jasper.runtime.HttpJspBase");
		Scene.v().addBasicClass("javax.servlet.http.HttpServlet");
		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
			private boolean isSubclass(final SootClass sub, final String superClass) {
				assert superClass.equals("org.apache.jasper.runtime.HttpJspBase") || 
					superClass.equals("javax.servlet.http.HttpServlet") : superClass;
				assert Scene.v().containsClass(superClass) : superClass;
				sub.checkLevel(SootClass.HIERARCHY);
				
				SootClass it = sub;
				while(it.hasSuperclass()) {
					it = it.getSuperclass();
					if(it.getName().equals(superClass)) {
						return true;
					}
				}
				assert it.getName().equals("java.lang.Object") || it.isPhantom() : it;
				return false;
			}
			
			Set<SootClass> visited = new HashSet<>();
			@Override
			public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
				final SootClass cls = m.getDeclaringClass();
				if(!visited.add(cls)) {
					return;
				}
				if(isSubclass(cls, "org.apache.jasper.runtime.HttpJspBase")) {
					if(cls.getName().equals("org.apache.jasper.runtime.HttpJspBase")) {
						return;
					}
					if(!AnalysisConfiguration.VERY_QUIET) {
						System.out.println("Transforming jasper bodies: " + cls);
					}
					JasperTransformer.synthesizeBodies(cls);
				} else if(isSubclass(cls, "javax.servlet.http.HttpServlet")) {
					if(cls.getName().equals("javax.servlet.http.HttpServlet")) {
						return;
					}
					if(!AnalysisConfiguration.VERY_QUIET) {
						System.out.println("instrumenting: " + cls);
					}
					HttpServletTransformer.synthesizeBody(cls);
				}
			}
		});
		final Yaml y = new Yaml();
		final Map<String, Object> routing;
		try(final FileInputStream fis = new FileInputStream(new File(routingFile))) {
			@SuppressWarnings("unchecked")
			final Map<String, Object> tmp = (Map<String, Object>) y.load(fis);
			routing = tmp;
		}
		final JspUrlRouter router = new JspUrlRouter(routing);
		{
			// Yes, these two handlers communicate through an aliased hash map. Womp
			final Map<Local, Set<String>> deferredResolution = new HashMap<>();
			// handle jsp include()/forward calls
			ComposableReflectionHandlers.v().addHandler(new JspDispatchInliner());
			ComposableReflectionHandlers.v().addHandler(new ServletDispatchResolver(router, deferredResolution));
			ComposableReflectionHandlers.v().addHandler(new AggressiveDispatchInliner(router, deferredResolution));
		}
	}

	public static void handleStaticInitModel(final SootMethod mainMethod) {
		final SootMethod entryMethod;
		if(mainMethod == null) {
			assert Scene.v().getEntryPoints().size() == 1;
			entryMethod = Scene.v().getEntryPoints().get(0);
		} else {
			entryMethod = mainMethod;
		}
		final SootClass mainClass = mainMethod.getDeclaringClass();
		final SootMethod sm = new SootMethod("legato_dummy_clinit", Collections.<Type>emptyList(), VoidType.v(), Modifier.SYNTHETIC | Modifier.STATIC);
		final JimpleBody jb = Jimple.v().newBody(sm);
		jb.getUnits().add(new JReturnVoidStmt());
		sm.setActiveBody(jb);
		jb.validate();
		Scene.v().getMainClass().addMethod(sm);
		final SootMethodRef dummyClinitRef = Scene.v().makeMethodRef(mainClass, "legato_dummy_clinit", Collections.<Type>emptyList(), VoidType.v(), true);
		final InvokeStmt ie = new JInvokeStmt(new JStaticInvokeExpr(dummyClinitRef, Collections.<Value>emptyList()));
		entryMethod.retrieveActiveBody();
		final PatchingChain<Unit> units = entryMethod.getActiveBody().getUnits();
		assert units.getFirst() instanceof JNopStmt;
		units.insertAfter(ie, units.getFirst());
		
		ComposableReflectionHandlers.v().addHandler(new AbstractReflectionHandler() {
			@Override
			public void handleNewMethod(final SootMethod m, final CallGraphBuilderBridge bridge) {
				if(m == entryMethod) {
					for(final SootClass cls : Scene.v().dynamicClasses()) {
						if(cls.declaresMethod("void <clinit>()")) {
							final SootMethod method = cls.getMethod("void <clinit>()");
							bridge.addEdge(m, ie, method, Kind.CLINIT);
						}
					}
				}
			}
		});
	}
}
