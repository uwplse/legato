package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import soot.Body;
import soot.G;
import soot.Local;
import soot.Modifier;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.VoidType;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.options.Options;
import soot.toolkits.scalar.Pair;

public class GenerateStrutsActions {
	private static int counter = 0;
	
	@SuppressWarnings("unchecked")
	public static void main(final String[] args) throws IOException {
		final Yaml y = new Yaml();
		final Object read;
		try {
			read = y.load(new FileInputStream(new File(args[1])));
		} catch (final FileNotFoundException e) {
			throw e;
		}
		final Map<String, Object> specs = (Map<String, Object>) read;
		final Options o = Options.v();
		o.set_soot_classpath(args[0]);
		o.set_allow_phantom_refs(true);
		o.set_whole_program(true);
		o.set_output_dir(args[3]);
		o.set_output_format(Options.output_format_class);
		o.set_asm_backend(true);
		G.v().out = new PrintStream(new OutputStream() {
			@Override
			public void write(final int b) throws IOException {
			}
		});
		final Set<String> applicationClasses = new HashSet<>();
		for(final Object rawSpec : specs.values()) {
			final Map<String, Object> actionSpec = (Map<String, Object>) rawSpec;
			applicationClasses.add((String) actionSpec.get("type"));
		}
		o.classes().addAll(applicationClasses);
		Scene.v().loadNecessaryClasses();
		final List<Map<String, String>> output = new ArrayList<>();
		for(final Map.Entry<String, Object> specKV : specs.entrySet()) {
			final String path = specKV.getKey();
			final Map<String, Object> actionSpec = (Map<String, Object>)specKV.getValue();
			final SootClass actionType = Scene.v().getSootClass((String) actionSpec.get("type"));
			if(actionSpec.get("parameter_name") == null && !actionType.getSuperclass().getName().equals("org.apache.struts.action.Action")) {
				final SootClass collapsed = collapseClass(actionType);
				final Map<String, String> entry = new HashMap<>();
				entry.put("path", path);
				entry.put("method", "execute");
				entry.put("param_method", null);
				entry.put("parent_type", actionType.getName());
				entry.put("type", collapsed.getName());
				output.add(entry);
			} else if(actionSpec.get("parameter_name") == null) {
				final Map<String, String> entry = new HashMap<>();
				entry.put("path", path);
				entry.put("method", "execute");
				entry.put("param_method", null);
				entry.put("parent_type", actionType.getName());
				entry.put("type", actionType.getName());
				output.add(entry);
			} else {
				assert hasCompleteHierarchy(actionType);
				final List<SootMethod> actionMethods = findDispatchMethods(actionType);
				for(final SootMethod m : actionMethods) {
					if(m.getName().equals("unspecified")) {
						int mod = m.getModifiers();
						mod &= ~Modifier.PROTECTED;
						mod |= Modifier.PUBLIC;
						m.setModifiers(mod);
					}
				}
				if(overridesExecute(actionType) && !isDirectAction(actionType)) {
					// collapse type hierarchy, generate one action per handler, instrument dispatchMethod
					for(final SootMethod m : actionMethods) {
						final SootClass collapsed = collapseClass(actionType);
						instrumentForCall(m, collapsed);
						final Map<String, String> entry = new HashMap<>();
						entry.put("path", path);
						entry.put("method", "execute");
						entry.put("param_method", m.getName());
						entry.put("parent_type", actionType.getName());
						entry.put("type", collapsed.getName());
						output.add(entry);
					}
				} else if(isDirectAction(actionType)) {
					for(final SootMethod m : actionMethods) {
						final Map<String, String> entry = new HashMap<>();
						entry.put("path", path);
						entry.put("method", m.getName());
						entry.put("param_method", m.getName());
						entry.put("parent_type", actionType.getName());
						entry.put("type", actionType.getName());
						output.add(entry);
					}
				} else {
					// collapse type hierarchy, but generate a single class
					final SootClass collapsed = collapseClass(actionType);
					for(final SootMethod m : actionMethods) {
						final Map<String, String> entry = new HashMap<>();
						entry.put("path", path);
						entry.put("method", m.getName());
						entry.put("param_method", m.getName());
						entry.put("parent_type", actionType.getName());
						entry.put("type", collapsed.getName());
						output.add(entry);
					}
				}
			}
		}
		o.set_whole_program(false);
		PackManager.v().runBodyPacks();
		PackManager.v().writeOutput();
		try(FileWriter fw = new FileWriter(new File(args[2]))) {
			y.dump(output, fw);
		}
	}
	
	private static void instrumentForCall(final SootMethod m, final SootClass collapsed) {
		for(final SootMethod methods : collapsed.getMethods()) {
			for(final Unit u : methods.getActiveBody().getUnits()) {
				final Stmt s = (Stmt) u;
				if(!s.containsInvokeExpr()) {
					continue;
				}
				if(s.getInvokeExpr().getMethodRef().getSubSignature().getString().equals("org.apache.struts.action.ActionForward dispatchMethod(" +
					"org.apache.struts.action.ActionMapping," +
					"org.apache.struts.action.ActionForm," +
					"javax.servlet.http.HttpServletRequest," +
					"javax.servlet.http.HttpServletResponse,java.lang.String)")) {
//					s.getInvokeExpr().setMethodRef(m.makeRef());
					assert s.getInvokeExpr() instanceof VirtualInvokeExpr;
					final VirtualInvokeExpr vie = (VirtualInvokeExpr) s.getInvokeExpr();
					final List<Value> args = vie.getArgs();
					s.getInvokeExprBox().setValue(Jimple.v().newVirtualInvokeExpr((Local) vie.getBase(), collapsed.getMethod(m.getSubSignature()).makeRef(), 
							args.subList(0, args.size() - 1)));
				}
			}
		}
	}

	private static SootClass collapseClass(final SootClass cls) {
		final SootClass toReturn = new SootClass(cls.getName() + "$LegatoCollapse$" + (counter++), cls.getModifiers());
		toReturn.setSuperclass(getStrutsParent(cls));
		SootClass it = cls;
		final Set<Pair<SootClass, String>> needSuperInline = new HashSet<>();
		final Set<String> nonStrutsParents = nonStrutsHierarchy(cls);
		final Jimple jimple = Jimple.v();
		while(!isStrutsClass(it)) {
			Scene.v().loadClass(it.getName(), SootClass.BODIES);
			for(final SootMethod m : it.getMethods()) {
				if(m.isConstructor()) {
					assert m.getParameterTypes().size() == 0;
					continue;
				}
				if(m.isAbstract()) {
					continue;
				}
				if(m.isStatic()) {
					continue;
				}
				String name = m.getName();
				if(toReturn.declaresMethod(m.getSubSignature())) {
					final Pair<SootClass, String> methodKey = new Pair<SootClass, String>(it, m.getSubSignature());
					if(!needSuperInline.contains(methodKey)) {
						continue;
					}
					name = name + "$$" + it.getName();
				}
				final SootMethod copy = new SootMethod(name, m.getParameterTypes(), m.getReturnType(), m.getModifiers(), m.getExceptions());
				final Body newBody = jimple.newBody(copy);
				toReturn.addMethod(copy);
				newBody.importBodyContentsFrom(m.retrieveActiveBody());
				copy.setActiveBody(newBody);
				for(final Unit u : newBody.getUnits()) {
					final Stmt s = (Stmt) u;
					if(s instanceof DefinitionStmt && ((DefinitionStmt) s).getRightOp() instanceof ThisRef) {
						final DefinitionStmt definitionStmt = (DefinitionStmt) s;
						definitionStmt.getRightOpBox().setValue(jimple.newThisRef(toReturn.getType()));
						final Local thisLocal = (Local) definitionStmt.getLeftOp();
						thisLocal.setType(toReturn.getType());
					}
					if(s.containsFieldRef() && s.getFieldRef() instanceof InstanceFieldRef && nonStrutsParents.contains(s.getFieldRef().getFieldRef().declaringClass().getName())) {
						assert ((InstanceFieldRef)s.getFieldRef()).getBase().equals(newBody.getThisLocal());
						final InstanceFieldRef ifr = (InstanceFieldRef) s.getFieldRef();
						ifr.setFieldRef(Scene.v().makeFieldRef(toReturn, s.getFieldRef().getFieldRef().name(), s.getFieldRef().getFieldRef().type(), false));
					}
					if(!s.containsInvokeExpr()) {
						continue;
					}
					final InvokeExpr ie = s.getInvokeExpr();
					final SootMethodRef smr = ie.getMethodRef();
					if(nonStrutsParents.contains(smr.declaringClass().getName()) && (ie instanceof InstanceInvokeExpr)) {
						assert ((InstanceInvokeExpr)ie).getBase().equals(newBody.getThisLocal());
						final String newMethodName;
						if(ie instanceof SpecialInvokeExpr && isSuperClassCall(ie, m, it)) {
							needSuperInline.add(new Pair<SootClass, String>(smr.declaringClass(), smr.getSubSignature().getString()));
							newMethodName = smr.name() + "$$" + smr.declaringClass().toString();
						} else {
							newMethodName = smr.name();
						}
						final SootMethodRef newMethodRef = Scene.v().makeMethodRef(toReturn, newMethodName, smr.parameterTypes(), smr.returnType(), smr.isStatic());
						ie.setMethodRef(newMethodRef);
					}
				}
			}
			for(final SootField f : it.getFields()) {
				assert f.isStatic();
			}
			it = it.getSuperclass();
		}
		{
			final SootMethod constructor = new SootMethod("<init>", Collections.<Type>emptyList(), VoidType.v(), Modifier.PUBLIC);
			toReturn.addMethod(constructor);
			final Body b = jimple.newBody(constructor);
			constructor.setActiveBody(b);
			final PatchingChain<Unit> units = b.getUnits();
			final RefType collapsedType = toReturn.getType();
			final Local thisLocal = jimple.newLocal("r0", collapsedType);
			b.getLocals().add(thisLocal);
			units.add(jimple.newIdentityStmt(thisLocal, jimple.newThisRef(collapsedType)));
			units.add(
				jimple.newInvokeStmt(
					jimple.newSpecialInvokeExpr(thisLocal,
						Scene.v().makeMethodRef(getStrutsParent(cls), "<init>", Collections.<Type>emptyList(), VoidType.v(), false))));
			units.add(jimple.newReturnVoidStmt());
		}
		Scene.v().addClass(toReturn);
		Scene.v().getApplicationClasses().add(toReturn);
		return toReturn;
	}
	
	private static boolean isSuperClassCall(final InvokeExpr ie, final SootMethod m, final SootClass it) {
		final SootMethodRef mr = ie.getMethodRef();
		if(mr.declaringClass().equals(it)) {
			assert ie.getMethod().isPrivate();
			return false;
		} else {
			return true;
		}
	}

	private static Set<String> nonStrutsHierarchy(final SootClass cls) {
		final Set<String> toReturn = new HashSet<>();
		SootClass it = cls;
		while(!isStrutsClass(it)) {
			toReturn.add(it.getName());
			it = it.getSuperclass();
		}
		return toReturn;
	}
	
	private static SootClass getStrutsParent(final SootClass cls) {
		SootClass it = cls;
		while(!isStrutsClass(it)) {
			it = it.getSuperclass();
		}
		return it;
	}

	private static boolean hasCompleteHierarchy(final SootClass actionType) {
		SootClass it = actionType;
		while(!isStrutsClass(it)) {
			if(it.isPhantomClass()) {
				return false;
			}
			it = it.getSuperclass();
		}
		return true;
	}

	private static boolean isDirectAction(final SootClass actionType) {
		return isStrutsClass(actionType.getSuperclass());
	}

	private static boolean isStrutsClass(final SootClass superClass) {
		return superClass.getName().startsWith("org.apache.struts");
	}
	
	private static boolean overridesExecute(final SootClass actionType) {
		SootClass it = actionType;
		while(!isStrutsClass(it)) {
			if(it.declaresMethod("org.apache.struts.action.ActionForward execute(" +
					"org.apache.struts.action.ActionMapping," +
					"org.apache.struts.action.ActionForm," +
					"javax.servlet.http.HttpServletRequest," +
					"javax.servlet.http.HttpServletResponse)")) {
				return true;
			}
			it = it.getSuperclass();
		}
		return false;
	}

	private static List<SootMethod> findDispatchMethods(final SootClass actionType) {
		final List<Type> s = getActionMethodTypes();
		final ArrayList<SootMethod> toReturn = new ArrayList<>();
		for(final SootMethod m : actionType.getMethods()) {
			if(m.getParameterTypes().equals(s) && m.getReturnType().equals(getActionMethodReturnType())) {
				toReturn.add(m);
			}
		}
		return toReturn;
	}

	private static RefType getActionMethodReturnType() {
		return Scene.v().getRefType("org.apache.struts.action.ActionForward");
	}

	public static List<Type> getActionMethodTypes() {
		return Arrays.<Type>asList(
			Scene.v().getRefType("org.apache.struts.action.ActionMapping"),
			Scene.v().getRefType("org.apache.struts.action.ActionForm"),
			Scene.v().getRefType("javax.servlet.http.HttpServletRequest"),
			Scene.v().getRefType("javax.servlet.http.HttpServletResponse"));
	}
}
