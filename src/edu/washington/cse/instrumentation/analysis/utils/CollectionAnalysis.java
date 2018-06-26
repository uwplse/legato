package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.Yaml;

import soot.FastHierarchy;
import soot.PackManager;
import soot.RefLikeType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Type;
import soot.util.Chain;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import soot.util.NumberedString;
import soot.util.StringNumberer;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class CollectionAnalysis {
	public static void main(final String[] args) throws IOException {
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.collections", new SceneTransformer() {
			@Override
			protected void internalTransform(final String phaseName,
					final Map<String, String> options) {
				final SootClass mapClass = Scene.v().getSootClass("java.util.Map");
				final SootClass collectionClass = Scene.v().getSootClass("java.util.Collection");
				final SootClass iteratorClass = Scene.v().getSootClass("java.util.Iterator");
				final MultiMap<SootClass, String> interfaceToSigs = new HashMultiMap<>();
				
				final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
				
				processInterface(fh, mapClass, interfaceToSigs);
				processInterface(fh, collectionClass, interfaceToSigs);
				processInterface(fh, iteratorClass, interfaceToSigs);
				collectSigs(collectionClass, interfaceToSigs);
				collectSigs(mapClass, interfaceToSigs);
				collectSigs(iteratorClass, interfaceToSigs);
				
				for(final SootClass cls : interfaceToSigs.keySet()) {
					final String className = cls.getName();
					for(final String sig : interfaceToSigs.get(cls)) {
						System.out.println("- sig: '<" + className + ": " + sig + ">'");
						System.out.println("  target: ???");
					}
				}
				System.exit(0);
			}
			
			private void processInterface(final FastHierarchy fh, final SootClass intf, final MultiMap<SootClass, String> outputMap) {
				for(final SootClass cls : fh.getAllSubinterfaces(intf)) {
					collectSigs(cls, outputMap);
				}
			}

			private void collectSigs(final SootClass intf,
					final MultiMap<SootClass, String> outputMap) {
				final Set<String> superinterfaceMethods = getInterfaceMethods(intf.getInterfaces());
				for(final SootMethod m : intf.getMethods()) {
					if(!superinterfaceMethods.contains(m.getSubSignature())) {
						outputMap.put(intf, m.getSubSignature());
					}
				}
			}

			private Set<String> getInterfaceMethods(final Chain<SootClass> interfaces) {
				final List<SootClass> worklist = new ArrayList<>(interfaces);
				final Set<String> toReturn = new HashSet<>();
				final Set<SootClass> visited = new HashSet<>();
				while(!worklist.isEmpty()) {
					final SootClass w = worklist.remove(worklist.size() - 1);
					if(!visited.add(w)) {
						continue;
					}
					worklist.addAll(w.getInterfaces());
					for(final SootMethod m : w.getMethods()) {
						toReturn.add(m.getSubSignature());
					}
				}
				return toReturn;
			}
		}));
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.collectionimpl", new SceneTransformer() {
			private Set<SootClass> getImplementedInterfaces(final SootClass impl) {
				final Set<SootClass> toReturn = new HashSet<>();
				final ArrayList<SootClass> worklist = new ArrayList<>(impl.getInterfaces());
				while(worklist.size() > 0) {
					final SootClass cls = worklist.remove(worklist.size() - 1);
					if(!toReturn.add(cls)) {
						continue;
					}
					worklist.addAll(cls.getInterfaces());
				}
				return toReturn;
			}
			
			final NumberedString toStringSig;
			final NumberedString equalsSig;
			final NumberedString hashcodeSig;
			final NumberedString cloneSig;
			private Table<SootClass, String, String> sigMap;
			private FastHierarchy fh;
			
			{
				final StringNumberer subSigNumberer = Scene.v().getSubSigNumberer();
				toStringSig = subSigNumberer.findOrAdd("java.lang.String toString()");
				equalsSig = subSigNumberer.findOrAdd("boolean equals(java.lang.Object)");
				hashcodeSig = subSigNumberer.findOrAdd("int hashCode()");
				cloneSig = subSigNumberer.findOrAdd("java.lang.Object clone()");
			}
			
			@SuppressWarnings("unchecked")
			@Override
			protected void internalTransform(final String phaseName, final Map<String, String> options) {
				ignores = options.containsKey("ignore") ? options.get("ignore").split(";") : new String[0];
				List<Map<String, String>> l = null;
				final DumperOptions dOptions = new DumperOptions();
				dOptions.setDefaultFlowStyle(FlowStyle.BLOCK);
				dOptions.setWidth(Integer.MAX_VALUE);
				final Yaml y = new Yaml(dOptions);
				try(InputStream is = new FileInputStream(new File(options.get("intf-spec")))) {
					l = (List<Map<String, String>>) y.load(is);
				} catch (final IOException e) {
					e.printStackTrace();
				}
				sigMap = HashBasedTable.create();
				for(final Map<String, String> m: l) {
					final String methodSig = m.get("sig");
					final SootMethod method = Scene.v().getMethod(methodSig);
					sigMap.put(method.getDeclaringClass(), method.getSubSignature(), m.get("target"));
				}
				fh = Scene.v().getOrMakeFastHierarchy();
				
				final Set<SootClass> implementationClasses = new HashSet<>();
				final Set<SootClass> subInterfaces = new HashSet<>();
				for(final SootClass intf : sigMap.rowKeySet()) {
					final Set<SootClass> impl = fh.getAllImplementersOfInterface(intf);
					for(final SootClass i : impl) {
						final String name = i.getName();
						if(ignored(name)) {
							continue;
						}
						implementationClasses.add(i);
					}
					final Set<SootClass> subIntf = fh.getAllSubinterfaces(intf);
					for(final SootClass si : subIntf) {
						final String name = si.getName();
						if(ignored(name)) {
							continue;
						}
						subInterfaces.add(si);
					}
				}
				final List<Object> hierarchy = new ArrayList<>();

				for(final SootClass subI : subInterfaces) {
					processSubInterface(subI, hierarchy);
				}
				
				for(final SootClass impl : implementationClasses) {
					processImplementationRoot(impl, hierarchy);
				}
				{
					final List<Object> output = new ArrayList<>();
					output.addAll(l);
					output.addAll(hierarchy);
					try(PrintWriter o = new PrintWriter(new File(options.get("output")))) {
						y.dump(output, o);
					} catch (final FileNotFoundException e) { 
						System.out.println("womp");
						System.err.println(y.dump(output));
					}
				}				
				System.exit(0);
			}
			
			private boolean ignored(final String name) {
				for(final String pref : ignores) {
					if(name.startsWith(pref)) {
						return true;
					}
				}
				return false;
			}
			
			private void processSubInterface(final SootClass i, final List<Object> hierarchy) {
				final Set<SootClass> implementedInterfaces = getImplementedInterfaces(i);
				implementedInterfaces.retainAll(sigMap.rowKeySet());
				final HashMap<String, Object> entry = new HashMap<>();
				
				entry.put("extend", i.getName());
				final List<String> parents = new ArrayList<>();
				for(final SootClass implemented : implementedInterfaces) {
					parents.add(implemented.toString());
				}
				entry.put("parents", parents);
				hierarchy.add(entry);
			}

			private final Set<SootClass> processedClasses = new HashSet<>();
			private String[] ignores;
			
			private void processImplementationRoot(final SootClass i, final List<Object> hierarchy) {
				if(processedClasses.contains(i)) {
					return;
				}
				final LinkedList<SootClass> worklist = new LinkedList<>();
				worklist.add(i);
				while(!worklist.isEmpty()) {
					final SootClass cls = worklist.removeFirst();
					if(!processedClasses.add(cls)) {
						continue;
					}
					processImplementation(cls, hierarchy);
				}
			}
			
			private Set<SootClass> getSuperclasses(final SootClass cls) {
				final HashSet<SootClass> toReturn = new HashSet<>();
				SootClass it = cls.getSuperclass();
				while(!it.getName().equals("java.lang.Object")) {
					toReturn.add(it);
					it = it.getSuperclass();
				}
				return toReturn;
			}
			
			private void processImplementation(final SootClass i, final List<Object> hierarchy) {
				final Set<SootClass> implementedInterfaces = getImplementedInterfaces(i);
				implementedInterfaces.retainAll(sigMap.rowKeySet());
				final HashMap<String, Object> entry = new HashMap<>();
				
				entry.put("extend", i.getName());
				final List<String> parents = new ArrayList<>();
				for(final SootClass implemented : implementedInterfaces) {
					parents.add(implemented.toString());
				}
				for(final SootClass inherited : getSuperclasses(i)) {
					parents.add(inherited.toString());
				}
				entry.put("parents", parents);
				hierarchy.add(entry);
				
				for(final SootMethod m : i.getMethods()) {
					if(!m.isPublic()) {
						continue;
					}
					if(m.isConstructor() && m.getParameterCount() == 0) {
						continue;
					} else if(m.isConstructor() && fh.canStoreType(i.getType(), Scene.v().getRefType("java.util.Map$Entry"))) {
						final Map<String, Object> constructorEntry = new HashMap<>();
						if(m.getParameterCount() == 2 && m.getParameterType(0) instanceof RefLikeType && m.getParameterType(1) instanceof RefLikeType) {
							constructorEntry.put("target", "CONTAINER_PUT");
						} else if(m.getParameterCount() == 1 && fh.canStoreType(m.getParameterType(0), Scene.v().getRefType("java.util.Map$Entry"))) {
							constructorEntry.put("target", "CONTAINER_ADDALL");
						} else {
							constructorEntry.put("target", "???");
						}
						constructorEntry.put("sig", m.getSignature());
						hierarchy.add(constructorEntry);
						continue;
					} else if(m.isConstructor()) {
						final Map<String, Object> constructorEntry = new HashMap<>();
						constructorEntry.put("sig", m.getSignature());
						final String target;
						// special cases
						if(m.getParameterCount() == 1 && fh.canStoreType(m.getParameterType(0), Scene.v().getRefType("java.util.Comparator"))) {
							// receiver
							target = "RECEIVER";
						} else if(m.getParameterCount() == 1 &&
							(fh.canStoreType(m.getParameterType(0), Scene.v().getRefType("java.util.Collection")) ||
							 fh.canStoreType(m.getParameterType(0), Scene.v().getRefType("java.util.Map")) ||
							 fh.canStoreType(m.getParameterType(0), Scene.v().getRefType("java.util.Iterator")))) {
							target = "CONTAINER_ADDALL";
						} else if(isNoRefType(m.getParameterTypes())) {
							target = "IDENTITY";
						} else {
							target = "???";
						}
						constructorEntry.put("target", target);
						hierarchy.add(constructorEntry);
						continue;
					} else if(hasCompatibleImplementation(m, implementedInterfaces)) {
						continue;
					}
					final NumberedString sig = m.getNumberedSubSignature();
					final Map<String, Object> methodEntry = new HashMap<>();
					methodEntry.put("sig", m.getSignature());
					if(sig == toStringSig || sig == hashcodeSig || sig == equalsSig) {
						methodEntry.put("target", "RETURN");
					} else if(sig == cloneSig || 
							(m.getName().equals("iterator") && m.getParameterCount() == 0 && fh.canStoreType(m.getReturnType(), Scene.v().getRefType("java.util.Iterator")))) {
						methodEntry.put("target", "CONTAINER_TRANSFER");
					} else {
						methodEntry.put("target", "???");
					}
					hierarchy.add(methodEntry);
				}
			}

			private boolean isNoRefType(final List<Type> parameterTypes) {
				for(final Type t : parameterTypes) {
					if(t instanceof RefLikeType) {
						return false;
					}
				}
				return true;
			}

			private boolean hasCompatibleImplementation(final SootMethod m, final Set<SootClass> implementedInterfaces) {
				for(final SootClass intf : implementedInterfaces) {
					method_search: for(final SootMethod iMethod : intf.getMethods()) {
						if(!m.getName().equals(iMethod.getName())) {
							continue;
						}
						if(m.getParameterCount() != iMethod.getParameterCount()) {
							continue;
						}
						// override check, don't try this at home kids
						for(int i = 0; i < m.getParameterCount(); i++) {
							if(!fh.canStoreType(m.getParameterType(i), iMethod.getParameterType(i))) {
								continue method_search;
							}
						}
						if(!fh.canStoreType(m.getReturnType(), iMethod.getReturnType())) {
							continue;
						}
						return true;
					}
				}
				return false;
			}
		}) {
			{
				setDeclaredOptions("intf-spec enabled ignore output");
			}
		});
		
		final List<String> toProcess = new ArrayList<>(); 
		try(final ZipFile zf = new ZipFile("/usr/lib/jvm/java-7-openjdk-amd64/jre/lib/rt.jar")) {
			final Enumeration<? extends ZipEntry> entries = zf.entries();
			while(entries.hasMoreElements()) {
				final ZipEntry ze = entries.nextElement();
				final String nm = ze.getName();
				if(!nm.endsWith(".class")) {
					continue;
				}
				final String internalName = nm.substring(0, nm.length() - 6).replace('/', '.');
				toProcess.add(internalName);
			}
		}
		final ArrayList<String> newArgs = new ArrayList<>(Arrays.asList(args));
		newArgs.addAll(toProcess);
		soot.Main.main(newArgs.toArray(new String[newArgs.size()]));
	}
}
