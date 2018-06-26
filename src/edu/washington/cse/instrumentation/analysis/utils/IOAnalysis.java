package edu.washington.cse.instrumentation.analysis.utils;

import java.io.IOException;
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
import org.yaml.snakeyaml.Yaml;

import soot.ArrayType;
import soot.FastHierarchy;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Type;

public class IOAnalysis {
	interface ProcessMethod {
		public Object handleMethod(SootMethod m);
		public void genRules(List<Object> output, SootClass cls);
	}
	
	public static void main(final String[] args) throws IOException {
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.io", new SceneTransformer() {
			private FastHierarchy fh;

			@Override
			protected void internalTransform(final String phaseName,
					final Map<String, String> options) {
				final SootClass readerClass = Scene.v().getSootClass("java.io.Reader");
				final SootClass writerClass = Scene.v().getSootClass("java.io.Writer");
				
				final SootClass inputClass = Scene.v().getSootClass("java.io.InputStream");
				final SootClass outputClass = Scene.v().getSootClass("java.io.OutputStream");
				
				final List<Object> output = new ArrayList<>();
				fh = Scene.v().getOrMakeFastHierarchy();
				
				processOutputRoot(outputClass, output);
				processOutputRoot(writerClass, output);
				processInputRoot(readerClass, output);
				processInputRoot(inputClass, output);
				final DumperOptions dOptions = new DumperOptions();
				dOptions.setWidth(Integer.MAX_VALUE);
				final Yaml y = new Yaml(dOptions);
				System.out.println(y.dump(output));
				System.exit(0);
			}
			
			private void worklist(final SootClass root, final List<Object> output, final ProcessMethod handler) {
				final LinkedList<SootClass> worklist = new LinkedList<>();
				worklist.add(root);
				while(!worklist.isEmpty()) {
					final SootClass cls = worklist.removeFirst();
					if(cls.getName().startsWith("com.sun") || cls.getName().startsWith("sun") || cls.getName().startsWith("org.omg.CORBA")) {
						continue;
					}
					worklist.addAll(fh.getSubclassesOf(cls));
					
					processClassCommon(output, cls);
					handler.genRules(output, cls);
					
					output.add("^<" + cls.getName() + ":<init>>");
					final Set<String> sMethods = getSuperClassMethods(cls.getSuperclass());
					for(final SootMethod m : cls.getMethods()) {
						if(m.isPrivate()) {
							continue;
						}
						if(m.isStaticInitializer()) {
							continue;
						}
						if(m.isConstructor()) {
							continue;
						} else if(sMethods.contains(m.getSubSignature())) {
							continue;
						} else {
							final Object toAdd = handler.handleMethod(m);
							if(toAdd != null) {
								output.add(toAdd);
							}
						}
					}
				}
			}
			
			private void processOutputRoot(final SootClass writerClass, final List<Object> output) {
				worklist(writerClass, output, new ProcessMethod() {
					@Override
					public Object handleMethod(final SootMethod m) {
						for(final String iPref : ignored) {
							if(m.getName().startsWith(iPref)) {
								return null;
							}
						}
						final Map<String, Object> toRet = new HashMap<>();
						toRet.put("sig", m.getSignature());
						toRet.put("target", "IDENTITY");
						return toRet;
					}
					
					private final String[] ignored = new String[]{
							"print",
							"write",
							"append",
							"flush",
							"close",
							"access$"
					};

					@Override
					public void genRules(final List<Object> output, final SootClass cls) {
						outer: for(final String iPref : ignored) {
							for(final SootMethod m : cls.getMethods()) {
								if(!m.isPublic()) {
									continue;
								}
								if(m.getName().startsWith(iPref)) {
									output.add("=<" + cls.getName() + ":" + iPref + "*>");
									continue outer;
								}
							}
						}
					}
				});
			}

			private void processClassCommon(final List<Object> output,
					final SootClass cls) {
				final Set<SootClass> superclasses = getSuperclasses(cls);
				if(superclasses.size() > 0) {
					final HashMap<String, Object> entry = new HashMap<>();
					entry.put("extend", cls.getName());
					final List<String> parents = new ArrayList<>();
					for(final SootClass p : superclasses) {
						parents.add(p.toString());
					}
					entry.put("parents", parents);
					output.add(entry);
				}
			}
			
			
			private void processInputRoot(final SootClass readerClass, final List<Object> output) {
				worklist(readerClass, output, new ProcessMethod() {
					
					@Override
					public Object handleMethod(final SootMethod m) {
						for(final String pr : toIgnore) {
							if(m.getName().startsWith(pr)) {
								return null;
							}
						}
						final Map<String, Object> t = new HashMap<>();
						t.put("sig", m.getSignature());
						if(isReadOutArg(m)) {
							t.put("target", "IDENTITY");
						} else {
							t.put("target", "???");
						}
						return t;
					}
					
					final String[] toIgnore = new String[]{
						"read",
						"skip",
						"close",
						"available",
						"reset",
						"markSupported",
						"mark",
						"access$"
					};
					
					@Override
					public void genRules(final List<Object> output, final SootClass cls) {
						outer: for(final String iPref : toIgnore) {
							for(final SootMethod m : cls.getMethods()) {
								if(!m.isPublic()) {
									continue;
								}
								if(m.getName().startsWith(iPref)) {
									output.add("=<" + cls.getName() + ":" + iPref + "*>");
									continue outer;
								}
							}
						}
					}
				});
			}
			
			private boolean isReadOutArg(final SootMethod m) {
				if(!m.getName().startsWith("read") || m.getParameterCount() == 0) {
					return false;
				}
				for(int i = 0; i < m.getParameterCount(); i++) {
					final Type ty = m.getParameterType(i);
					if(ty instanceof ArrayType) {
						return true;
					}
				}
				return false;
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
			
			private Set<String> getSuperClassMethods(final SootClass superClass) {
				SootClass it = superClass;
				final Set<String> toReturn = new HashSet<>();
				while(!it.getName().equals("java.lang.Object")) {
					for(final SootMethod m : it.getMethods()) {
						if(m.isConstructor()) {
							continue;
						}
						if(!m.isPublic()) {
							continue;
						}
						toReturn.add(m.getSubSignature());
					}
					it = it.getSuperclass();
				}
				return toReturn;
			}
		}));

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
