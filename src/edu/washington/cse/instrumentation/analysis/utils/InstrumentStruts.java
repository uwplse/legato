package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import soot.Body;
import soot.G;
import soot.IntType;
import soot.Local;
import soot.Modifier;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.IntConstant;
import soot.jimple.Jimple;
import soot.jimple.NullConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.StmtBox;
import soot.options.Options;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.SimpleLocalDefs;
import soot.util.HashChain;

public class InstrumentStruts {
	private static final String ACTION_SERVLET_NAME = "org.apache.struts.action.ActionServlet";
	private static final String CONTEXT_NAME = "javax.servlet.ServletContext";
	private static final String ACTION_FORWARD_NAME = "org.apache.struts.action.ActionForward";
	private static final String ACTION_NAME = "org.apache.struts.action.Action";
	private static final String SERVLET_NAME = "javax.servlet.GenericServlet";
	private static final String[] INSTRUMENT_TYPES = new String[]{
		ACTION_SERVLET_NAME, CONTEXT_NAME, ACTION_FORWARD_NAME, ACTION_NAME, SERVLET_NAME
	};
	
	@SuppressWarnings("unchecked")
	public static void main(final String[] args) throws IOException {
		final Yaml y = new Yaml();
		final Object read;
		try {
			read = y.load(new FileInputStream(new File(args[1])));
		} catch (final FileNotFoundException e) {
			throw e;
		}
		final Map<String, Map<String, Object>> specs = (Map<String, Map<String, Object>>) read;
		final Options o = Options.v();
		o.set_soot_classpath(args[0]);
		o.set_allow_phantom_refs(true);
		o.set_output_dir(args[0]);
		final Set<String> applicationClasses = specs.keySet();
		final String urlMappingClass = args[3];
		final String nondetClass = args[2];
		o.classes().addAll(applicationClasses);
		o.classes().add(urlMappingClass);
		o.classes().add(nondetClass);
		for(final String s : INSTRUMENT_TYPES) {
			Scene.v().addBasicClass(s);
		}
		o.set_output_format(Options.output_format_class);
		o.set_asm_backend(true);
		o.set_src_prec(Options.src_prec_class);
		G.v().out = new PrintStream(new OutputStream() {
			@Override
			public void write(final int b) throws IOException {
			}
		});
		for(final Map<String, Object> i : specs.values()) {
			final Map<String, String> forwardingToClass = (Map<String, String>) i.get("forwards");
			for(final String cls : forwardingToClass.values()) {
				if(cls == null) { continue; }
				o.classes().add(cls);
			}
		}
		Scene.v().loadNecessaryClasses();
		{
			final List<SootClass> appClasses = new ArrayList<>();
			for(final String appClass : applicationClasses) {
				appClasses.add(Scene.v().getSootClass(appClass));
			}
			Scene.v().getApplicationClasses().clear();
			Scene.v().getApplicationClasses().addAll(appClasses);
		}
		{
			final SootClass contextClass = Scene.v().getSootClass(CONTEXT_NAME);
			contextClass.setModifiers(contextClass.getModifiers() | Modifier.INTERFACE);
		}
		
		final RefType urlForwardType = RefType.v(urlMappingClass);
		final RefType contextType = RefType.v(CONTEXT_NAME);
		final RefType actionServletType = RefType.v(ACTION_SERVLET_NAME);
		final RefType httpReqType = RefType.v("javax.servlet.http.HttpServletRequest");
		final RefType httpRespType = RefType.v("javax.servlet.http.HttpServletResponse");
		
		final SootMethodRef forwardNowRef = Scene.v().makeMethodRef(Scene.v().getSootClass(urlMappingClass), "doForwardNow", 
				Arrays.<Type>asList(contextType, httpReqType, httpRespType), VoidType.v(), false);
		
		final Jimple j = Jimple.v();
		final RefType actionForwardType = RefType.v(ACTION_FORWARD_NAME);
		for(final Map.Entry<String, Map<String, Object>> specKV : specs.entrySet()) {
			final SootClass cls = Scene.v().getSootClass(specKV.getKey());
			final Map<String, String> forwardingToClass = (Map<String, String>) specKV.getValue().get("forwards");
			final Set<String> instrumentMethods = new HashSet<String>((List<String>)specKV.getValue().get("method"));
			for(final SootMethod m : cls.getMethods()) {
				final PatchingChain<Unit> unitChain = m.retrieveActiveBody().getUnits();
				final Iterator<Unit> uIterator = unitChain.snapshotIterator();
				unit_loop: while(uIterator.hasNext()) {
					final Unit u = uIterator.next();
					final Stmt s = (Stmt) u;
					final Body activeBody = m.getActiveBody();
					final LocalGenerator localGenerator = new LocalGenerator(activeBody);
					if(s instanceof ReturnStmt && instrumentMethods.contains(m.getName()) && m.getReturnType().equals(actionForwardType) && ((ReturnStmt)s).getOp() instanceof Local) {
						final Local returnedLocal = (Local) ((ReturnStmt)s).getOp();
						final HashChain<Unit> toInsert = new HashChain<Unit>();
						final Local forwardLocal = localGenerator.generateLocal(urlForwardType);
						// sigh
						toInsert.add(j.newAssignStmt(forwardLocal, j.newCastExpr(returnedLocal, urlForwardType)));
						final Local contextLocal = localGenerator.generateLocal(contextType);
						final Local actionServletLocal = localGenerator.generateLocal(actionServletType);
						// get our servlet reference
						toInsert.add(
							j.newAssignStmt(
								actionServletLocal,
								j.newInstanceFieldRef(activeBody.getThisLocal(),
										Scene.v().makeFieldRef(cls, "servlet", actionServletType, false))));
						// now get the context
						toInsert.add(
							j.newAssignStmt(
								contextLocal,
								j.newVirtualInvokeExpr(actionServletLocal,
									Scene.v().makeMethodRef(Scene.v().getSootClass(ACTION_SERVLET_NAME), "getServletContext", Collections.<Type>emptyList(), contextType, false))));
						final List<Value> invokeArgs = new ArrayList<>();
						invokeArgs.add(contextLocal);
						
						for(final Local l : activeBody.getParameterLocals()) {
							if(l.getType().equals(httpReqType)) {
								assert invokeArgs.size() == 1;
								invokeArgs.add(l);
							} else if(l.getType().equals(httpRespType)) {
								assert invokeArgs.size() == 2;
								invokeArgs.add(l);
							}
						}
						toInsert.add(
							j.newInvokeStmt(
								j.newVirtualInvokeExpr(
									forwardLocal,
									forwardNowRef,
									invokeArgs)));
						unitChain.insertBefore(toInsert, s);
						((ReturnStmt)s).getOpBox().setValue(NullConstant.v());
					}
					if(!s.containsInvokeExpr()) {
						continue;
					}
					if(s.getInvokeExpr().getMethodRef().getSignature().equals("<org.apache.struts.action.ActionMapping: " +
							"org.apache.struts.action.ActionForward findForward(java.lang.String)>")) {
						assert s instanceof AssignStmt;
						final Value arg = s.getInvokeExpr().getArg(0);
						if(arg instanceof StringConstant) {
							final String stringConstant = ((StringConstant) arg).value;
							if(forwardingToClass.containsKey(stringConstant)) {
								insertBasicForward(m, s, forwardingToClass.get(stringConstant));
							} else {
								// lol
								s.getInvokeExprBox().setValue(NullConstant.v());
								continue;
							}
						} else if(arg instanceof Local) {
							final Local l = (Local) arg;
							final SimpleLocalDefs sld = new SimpleLocalDefs(new ExceptionalUnitGraph(activeBody, Scene.v().getDefaultThrowAnalysis()));
							final List<Unit> defUnits = sld.getDefsOfAt(l, s);
							final Set<String> reachingForwardTypes = new HashSet<>();
							for(final Unit defU : defUnits) {
								assert defU instanceof DefinitionStmt;
								// bail
								if(defU instanceof IdentityStmt) {
									insertBasicForward(m, s, nondetClass);
									continue unit_loop;
								} else if(defU instanceof AssignStmt) {
									final AssignStmt defAssign = (AssignStmt) defU;
									assert defAssign.getLeftOp().equals(l);
									// bail again
									if(!(defAssign.getRightOp() instanceof StringConstant)) {
										insertBasicForward(m, s, nondetClass);
										continue unit_loop;
									}
									final String pathConstant = ((StringConstant) defAssign.getRightOp()).value;
									if(forwardingToClass.containsKey(pathConstant)) {
										reachingForwardTypes.add(forwardingToClass.get(pathConstant));
									}
								} else {
									throw new RuntimeException("Unexpected definition form: " + defU);
								}
							}
							if(reachingForwardTypes.isEmpty()) {
								// ugh, let's be conservative
								insertBasicForward(m, s, nondetClass);
							} else if(reachingForwardTypes.size() == 1) {
								insertBasicForward(m, s, reachingForwardTypes.iterator().next());
							} else {
								// yaaaaaaay more freaking non-deterministic chains!
//								final HashChain<Unit> toInsert = new HashChain<>();
								final Local dispatchSwitchLocal = localGenerator.generateLocal(IntType.v());
								final Unit currUnit = j.newAssignStmt(dispatchSwitchLocal, 
										j.newStaticInvokeExpr(
												Scene.v().makeMethodRef(Scene.v().getSootClass("edu.washington.cse.servlet.Util"), "nondetInt", Collections.<Type>emptyList(), IntType.v(), true)));
								unitChain.insertBefore(currUnit, s);
								final StmtBox chainEnd = new StmtBox(s);
								StmtBox nextCheck = new StmtBox(null);
								int i = 0;
								final Local forwardLocal = localGenerator.generateLocal(urlForwardType);
								for(final String forwardTypeName : reachingForwardTypes) {
									Unit blockHead;
									final RefType forwardingType = RefType.v(forwardTypeName);
									final Local instantiationLocal = localGenerator.generateLocal(forwardingType);
									final Unit newUnit = j.newAssignStmt(forwardLocal, j.newNewExpr(forwardingType));
									final StmtBox tmp = new StmtBox(null);
									if(i != reachingForwardTypes.size() - 1) {
										blockHead = j.newIfStmt(
											j.newNeExpr(dispatchSwitchLocal, IntConstant.v(i)), tmp);
										unitChain.insertBefore(blockHead, s);
									} else {
										blockHead = newUnit;
									}
									unitChain.insertBefore(newUnit, s);
									
									nextCheck.setUnit(blockHead);
									nextCheck = tmp;
									
									unitChain.insertBefore(j.newInvokeStmt(
										j.newSpecialInvokeExpr(instantiationLocal, 
												Scene.v().makeMethodRef(forwardingType.getSootClass(), "<init>", Collections.<Type>emptyList(), VoidType.v(), false))), s);
									unitChain.insertBefore(j.newAssignStmt(forwardLocal, instantiationLocal), s);
									unitChain.insertBefore(j.newGotoStmt(chainEnd), s);
									i++;
								}
//								unitChain.insertBefore(toInsert, s);
								((AssignStmt)s).getRightOpBox().setValue(forwardLocal);
							}
						} else {
							throw new RuntimeException("Okay, we actually have to handle this: " + u);
						}
					}
				}
			}
		}
		o.set_whole_program(false);
		PackManager.v().runBodyPacks();
		PackManager.v().writeOutput();
	}

	public static void insertBasicForward(final SootMethod m, final Stmt s, final String forwardTypeName) {
		final Jimple j = Jimple.v();
		final RefType forwardType = RefType.v(forwardTypeName);
		final Body body = m.getActiveBody();
		final Local loc = new LocalGenerator(body).generateLocal(forwardType);
		body.getUnits().insertBefore(j.newAssignStmt(loc, j.newNewExpr(forwardType)), s);
		body.getUnits().insertBefore(
			j.newInvokeStmt(
				j.newSpecialInvokeExpr(loc,
					Scene.v().makeMethodRef(forwardType.getSootClass(), "<init>", Collections.<Type>emptyList(), VoidType.v(), false))),
			s);
		((AssignStmt)s).getRightOpBox().setValue(loc);
	}
}

