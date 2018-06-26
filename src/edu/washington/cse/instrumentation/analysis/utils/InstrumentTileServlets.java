package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
import soot.SootFieldRef;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.VoidType;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StaticFieldRef;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.ThisRef;
import soot.jimple.VirtualInvokeExpr;
import soot.options.Options;
import soot.util.Chain;

public class InstrumentTileServlets {
	private static final String[] SERVLET_INTFS = new String[]{
		"javax.servlet.ServletContext",
		"javax.servlet.RequestDispatcher",
		"javax.servlet.ServletRequest",
		"javax.servlet.ServletResponse"
	};
	
	@SuppressWarnings("unchecked")
	public static void main(final String args[]) throws FileNotFoundException {
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
		G.v().out = new PrintStream(new OutputStream() {
			@Override
			public void write(final int b) throws IOException {
			}
		});
		o.set_asm_backend(true);
		
		for(final String baseClass : (List<String>)specs.get("base-templates")) {
			Scene.v().addBasicClass(baseClass);
		}
		
		for(final String servletIntf : SERVLET_INTFS) {
			Scene.v().addBasicClass(servletIntf);	
		}
		
		Scene.v().loadNecessaryClasses();
		
		{
			for(final String servletIntf : SERVLET_INTFS) {
				final SootClass intfClass = Scene.v().getSootClass(servletIntf);
				intfClass.setModifiers(intfClass.getModifiers() | Modifier.INTERFACE);
			}
		}
		
		final Map<String, Map<String, String>> tileTagMap = new HashMap<>();
		for(final String baseClass : (List<String>)specs.get("base-templates")) {
			tileTagMap.put(baseClass, findTagSites(Scene.v().getSootClass(baseClass)));
		}
		final List<SootClass> toWrite = new ArrayList<>();
		final Map<String, String> output = new HashMap<>();
		for(final Map<String, Object> p : (List<Map<String, Object>>)specs.get("tiles")) {
			final String tileName = (String) p.get("name");
			final String baseTile = (String) p.get("base");
			
			assert tileTagMap.containsKey(baseTile);
			final SootClass cls = cloneClass(baseTile +"$TILE$" + tileName, Scene.v().getSootClass(baseTile));
			instrumentInsertTags(cls, (Map<String, String>) p.get("defs"), tileTagMap.get(baseTile));
			toWrite.add(cls);
			output.put(tileName, cls.getName());
		}
		Scene.v().getApplicationClasses().clear();
		Scene.v().getApplicationClasses().addAll(toWrite);
		PackManager.v().runBodyPacks();
		PackManager.v().writeOutput();
		try(PrintWriter pw = new PrintWriter(new File(args[2]))) {
			y.dump(output, pw);
		}
	}
	
	private static void instrumentInsertTags(final SootClass cls, final Map<String, String> defs, final Map<String, String> tagToMethod) {
		assert tagToMethod != null;
		assert defs != null;
		final Map<String, String> methodToUrl = new HashMap<>();
		for(final Map.Entry<String, String> kv : tagToMethod.entrySet()) {
			final String url = defs.get(kv.getKey());
			assert url != null : defs + " " + tagToMethod;
			methodToUrl.put(kv.getValue(), url);
		}
		final Jimple jimple = Jimple.v();
		for(final SootMethod m : cls.getMethods()) {
			int tempCounter = 0;
			final Body body = m.getActiveBody();
			final PatchingChain<Unit> unitChain = body.getUnits();
			final Iterator<Unit> snapshotIterator = unitChain.snapshotIterator();
			while(snapshotIterator.hasNext()) {
				final Stmt s = (Stmt) snapshotIterator.next();
				if(!s.containsInvokeExpr()) {
					continue;
				}
				final String methodSig = s.getInvokeExpr().getMethodRef().getSubSignature().getString();
				if(!methodToUrl.containsKey(methodSig)) {
					continue;
				}
				assert m.getSubSignature().equals("void _jspService(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)");
				final Local thisLocal = body.getThisLocal();
				final Chain<Local> localChain = body.getLocals();
				
				final RefType contextType = RefType.v("javax.servlet.ServletContext");
				final RefType dispatcherType = RefType.v("javax.servlet.RequestDispatcher");
				
				final Local contextLocal = jimple.newLocal("$tmp_legato_" + (tempCounter++), contextType);
				final Local dispatchLocal = jimple.newLocal("$tmp_legato_" + (tempCounter++), dispatcherType);
				localChain.add(contextLocal);
				localChain.add(dispatchLocal);
				
				final String url = methodToUrl.get(methodSig);
				
				unitChain.insertBefore(
					jimple.newAssignStmt(
						contextLocal,
						jimple.newVirtualInvokeExpr(thisLocal,
							Scene.v().makeMethodRef(cls, "getServletContext", Collections.<Type>emptyList(), contextType, false))), s);
				unitChain.insertBefore(
					jimple.newAssignStmt(
						dispatchLocal,
						jimple.newInterfaceInvokeExpr(
							contextLocal,
							Scene.v().makeMethodRef(contextType.getSootClass(), "getRequestDispatcher", Collections.<Type>singletonList(RefType.v("java.lang.String")), dispatcherType, false),
							Collections.<Value>singletonList(StringConstant.v(url))
						)
					), s);
				final List<Value> args = new ArrayList<>();
				args.add(body.getParameterLocals().get(0));
				args.add(body.getParameterLocals().get(1));
				
				final SootMethodRef includRef =
					Scene.v().makeMethodRef(dispatcherType.getSootClass(), "include", Arrays.<Type>asList(
							RefType.v("javax.servlet.ServletRequest"),
							RefType.v("javax.servlet.ServletResponse")
						), VoidType.v(), false);
				unitChain.swapWith(s, jimple.newInvokeStmt(jimple.newInterfaceInvokeExpr(dispatchLocal, includRef, args)));
			}
		}
	}

	private static SootClass cloneClass(final String newName, final SootClass toClone) {
		final SootClass toReturn = new SootClass(newName, toClone.getModifiers());
		toReturn.setSuperclass(toClone.getSuperclass());
		for(final SootClass intf : toClone.getInterfaces()) {
			toReturn.addInterface(intf);
		}
		for(final SootField sf : toClone.getFields()) {
			toReturn.addField(new SootField(sf.getName(), sf.getType(), sf.getModifiers()));
		}
		for(final SootMethod sm : toClone.getMethods()) {
			final SootMethod m = new SootMethod(sm.getName(), sm.getParameterTypes(), sm.getReturnType(), sm.getModifiers(), sm.getExceptions());
			final JimpleBody newBody = Jimple.v().newBody(m);
			newBody.importBodyContentsFrom(sm.retrieveActiveBody());
			m.setActiveBody(newBody);
			toReturn.addMethod(m);
			
			// rewrite references to the this type
			for(final Unit u : m.getActiveBody().getUnits()) {
				final Stmt s = (Stmt) u;
				if(s instanceof DefinitionStmt && ((DefinitionStmt) s).getRightOp() instanceof ThisRef) {
					final DefinitionStmt ds = (DefinitionStmt) s;
					ds.getRightOpBox().setValue(Jimple.v().newThisRef(toReturn.getType()));
				}
				if(s.containsFieldRef() && s.getFieldRef().getFieldRef().declaringClass().equals(toClone)) {
					final ValueBox fieldRefBox = s.getFieldRefBox();
					final FieldRef oldFieldValue = s.getFieldRef();
					final SootFieldRef oldFieldRef = oldFieldValue.getFieldRef();
					final SootFieldRef newSootFieldRef = Scene.v().makeFieldRef(
						toReturn, oldFieldRef.name(), oldFieldRef.type(), oldFieldRef.isStatic());
					final FieldRef newFieldValue;
					if(oldFieldValue instanceof StaticFieldRef) {
						newFieldValue = Jimple.v().newStaticFieldRef(newSootFieldRef);
					} else {
						assert oldFieldValue instanceof InstanceFieldRef;
						newFieldValue = Jimple.v().newInstanceFieldRef(((InstanceFieldRef)oldFieldValue).getBase(), newSootFieldRef);
					}
					fieldRefBox.setValue(newFieldValue);
				}
				if(s.containsInvokeExpr() && s.getInvokeExpr().getMethodRef().declaringClass().equals(toClone)) {
					final InvokeExpr ie = s.getInvokeExpr();
					final SootMethodRef oldMethodRef = ie.getMethodRef();
					final SootMethodRef newMethodRef = Scene.v().makeMethodRef(
						toReturn, oldMethodRef.name(), oldMethodRef.parameterTypes(), oldMethodRef.returnType(), oldMethodRef.isStatic());
					final InvokeExpr newInvokeExpr;
					if(ie instanceof VirtualInvokeExpr) {
						newInvokeExpr = Jimple.v().newVirtualInvokeExpr((Local) ((VirtualInvokeExpr) ie).getBase(), newMethodRef, ie.getArgs());
					} else if(ie instanceof SpecialInvokeExpr) {
						newInvokeExpr = Jimple.v().newSpecialInvokeExpr((Local) ((SpecialInvokeExpr) ie).getBase(), newMethodRef, ie.getArgs());
					} else if(ie instanceof StaticInvokeExpr) {
						newInvokeExpr = Jimple.v().newStaticInvokeExpr(newMethodRef, ie.getArgs());
					} else if(ie instanceof InterfaceInvokeExpr) {
						throw new IllegalStateException("This should be impossible: " + ie + " at " + u + " in " + sm);
					} else {
						throw new RuntimeException("Unexpected call form: " + ie + " at " + u + " in " + sm);
					}
					s.getInvokeExprBox().setValue(newInvokeExpr);
				}
			}
			for(final Local l : m.getActiveBody().getLocals()) {
				// fix local types
				if(l.getType().equals(toClone.getType())) {
					l.setType(toReturn.getType());
				}
			}
		}
		Scene.v().addClass(toReturn);
		Scene.v().getApplicationClasses().add(toReturn);
		return toReturn;
	}

	private static Map<String, String> findTagSites(final SootClass sootClass) {
		final Map<String, String> toReturn = new HashMap<>();
		for(final SootMethod m : sootClass.getMethods()) {
			m.retrieveActiveBody();
			final Set<Local> candidateLocals = new HashSet<>();
			for(final Local l : m.getActiveBody().getLocals()) {
				if(!(l.getType() instanceof RefType)) {
					continue;
				}
				final RefType refType = (RefType) l.getType();
				if(!refType.getSootClass().getName().equals("org.apache.struts.taglib.tiles.InsertTag")) {
					continue;
				}
				candidateLocals.add(l);
			}
			if(candidateLocals.size() == 0) {
				continue;
			}
			boolean foundAttribute = false;
			for(final Unit u : m.getActiveBody().getUnits()) {
				final Stmt s = (Stmt) u;
				if(!s.containsInvokeExpr()) {
					continue;
				}
				if(!(s.getInvokeExpr() instanceof InstanceInvokeExpr)) {
					continue;
				}
				final InstanceInvokeExpr iie = (InstanceInvokeExpr) s.getInvokeExpr();
				if(!candidateLocals.contains(iie.getBase())) {
					continue;
				}
				final SootMethodRef calledMethod = iie.getMethodRef();
				if(!calledMethod.getSubSignature().getString().equals("void setAttribute(java.lang.String)")) {
					continue;
				}
				if(foundAttribute) {
					System.out.println(m.getActiveBody());
					throw new RuntimeException("whoops");
				}
				final Value arg = iie.getArg(0);
				if(!(arg instanceof StringConstant)) {
					System.out.println(m.getActiveBody());
					throw new RuntimeException("whoops 2");
				}
				final String attributeValue = ((StringConstant) arg).value;
				toReturn.put(attributeValue, m.getSubSignature());
				foundAttribute = true;
			}
		}
		return toReturn;
	}
}
