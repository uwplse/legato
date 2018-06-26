package edu.washington.cse.instrumentation.analysis.propagation;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import soot.Local;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import boomerang.AliasFinder;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;


public class YamlPropagationManager implements PropagationManager {
	private final Map<String, PropagationTarget> propagationSpec = new HashMap<>();
	private final Map<SootMethod, PropagationTarget> propagationMethodSpec = new HashMap<>();
	
	private final Table<String, String, PropagationTarget> returnTypeSpec = HashBasedTable.create();
	private final Map<String, Set<String>> packageSpecs = new HashMap<>();
	private final Table<String, String, PropagationTarget> methodNameSpec = HashBasedTable.create();
	
	private final Table<String, String, PropagationTarget> methodNamePrefixSpec = HashBasedTable.create();
	
	private final MultiMap<String, String> identityPackages = new HashMultiMap<>();
	private final Map<String, GraphResolver> graphResolvers = new HashMap<>();
	private final Map<String, TIntList> subfieldArgs = new HashMap<>();
	
	private final Set<String> containerRoots = new HashSet<>();
	private final Set<String> isContainerDerived = new HashSet<>();
	
	public YamlPropagationManager(final String propagationSpecFile) {
		for(final String p : propagationSpecFile.split(":")) {
			parseYamlFile(p);
		}
	}

	@SuppressWarnings("unchecked")
	private void parseYamlFile(final String propagationSpecFile) {
		final Yaml y = new Yaml();
		final List<Object> parsed;
		try(InputStream is = new FileInputStream(new File(propagationSpecFile))) {
			parsed = (List<Object>) y.load(is);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		final Table<String, String, PropagationTarget> parsedSigs = HashBasedTable.create();
		for(final Object line : parsed) {
			if(line instanceof String) {
				parseRule((String) line);
				continue;
			}
			final Map<String, Object> p = (Map<String,Object>)line;
			if(p.containsKey("rule")) {
				parseRule((String) p.get("rule"));
				continue;
			}
			if(p.containsKey("extend")) {
				handleExtend(p, parsedSigs);
				continue;
			}
			if(!p.containsKey("sig")) {
				throw new IllegalArgumentException("Bad propagation spec: " + p);
			}
			if(!p.containsKey("target")) {
				throw new IllegalArgumentException("Bad propagation spec: " + p);
			}
			final String target = (String)p.get("target");
			if(target.equals("???")) {
				continue;
			}
			
			final String sig = (String) p.get("sig");
			final PropagationTarget t = PropagationTarget.valueOf(target);
			addPropagationSpec(sig, t);
			{
				parsedSigs.put(Scene.v().signatureToClass(sig), Scene.v().signatureToSubsignature(sig), t);
			}
			
			if(t == PropagationTarget.GRAPH) {
				if(!p.containsKey("resolver")) {
					throw new IllegalArgumentException("Missing graph resolver: " + p);
				}
				parseResolver(sig, p);
			}
			if(p.containsKey("subfields")) {
				final List<Integer> fConf = (List<Integer>)p.get("subfields");
				final TIntList f = new TIntArrayList(fConf.size(), -1);
				f.addAll(fConf);
				subfieldArgs.put(sig, f);
			}
			if(p.containsKey("subfield")) {
				throw new RuntimeException("You meant subfields: " + p);
			}
		}
	}

	private void addPropagationSpec(final String sig, final PropagationTarget t) {
		if(propagationSpec.containsKey(sig)) {
			if(propagationSpec.get(sig) == t && propagationSpec.get(sig) != PropagationTarget.GRAPH) {
				return;
			}
			throw new IllegalArgumentException("Duplicate propagation spec: (" + sig + " -> " + t + ") vs " + propagationSpec.get(sig));
		}
		if(t.isContainerAbstraction() && !isContainerDerived.contains(Scene.v().signatureToClass(sig))) {
			this.containerRoots.add(Scene.v().signatureToClass(sig));
		}
		propagationSpec.put(sig, t);
	}
	
	private void handleExtend(final Map<String, Object> p, final Table<String, String, PropagationTarget> parsedSigs) {
		@SuppressWarnings("unchecked")
		final List<String> parents = (List<String>) p.get("parents");
		final String extendingClass = (String)p.get("extend");
		for(final String cls: parents) {
			if(containerRoots.contains(cls) || isContainerDerived.contains(cls)) {
				isContainerDerived.add(extendingClass);
			}
		}
		for(final String cls : parents) {
			for(final Map.Entry<String, PropagationTarget> rowMap : parsedSigs.row(cls).entrySet()) {
				if(rowMap.getKey().contains("<init>")) {
					continue;
				}
				final String thisSpec = "<" + extendingClass + ": " + rowMap.getKey() + ">";
				addPropagationSpec(thisSpec, rowMap.getValue());
				if(rowMap.getValue() == PropagationTarget.GRAPH) {
					final GraphResolver parentResolver = graphResolvers.get("<" + cls + ": " + rowMap.getKey() + ">");
					assert parentResolver != null;
					graphResolvers.put(thisSpec, parentResolver);
				}
				final String methodName = rowMap.getKey().replaceAll("[^ ]+ ([^(]+)\\(.*\\)", "$1");
				assert !methodName.contains("(") : methodName + " " + rowMap.getKey();
				// TODO: fix this
//				if(methodNameSpec.contains(extendingClass, methodName)) {
//					if(methodNameSpec.get(extendingClass, methodName) != rowMap.getValue()) {
//						throw new RuntimeException(methodNameSpec.get(extendingClass, methodName) + " vs " + rowMap.getValue() + " " + extendingClass + " " + rowMap.getKey());
//					}
//				} else {
//					methodNameSpec.put(extendingClass, methodName, rowMap.getValue());
//				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void parseResolver(final String sig, final Map<String, Object> p) {
		if(p.get("resolver").equals("ret-graph")) {
			final SootField[] wsf = parseFieldList(p);
			graphResolvers.put(sig, new ReturnGraphResolver(wsf));
		} else if(p.get("resolver").equals("out-arg")) {
			final int a = (int) p.get("argnum");
			final OutArgResolver outArgResolver;
			if(p.containsKey("fields")) {
				outArgResolver = new OutArgResolver(a, parseFieldList(p));
			} else {
				outArgResolver = new OutArgResolver(a);
			}
			graphResolvers.put(sig, outArgResolver);
		} else {
			throw new IllegalArgumentException("Bad graphs resolver: " + p);
		}
	}

	private SootField[] parseFieldList(final Map<String, Object> p) {
		@SuppressWarnings("unchecked")
		final List<String> fieldSpecs = (List<String>) p.get("fields");
		final SootField[] wsf = new SootField[fieldSpecs.size()];
		for(int i = 0; i < fieldSpecs.size(); i++) {
			if(fieldSpecs.get(i).equals("ARRAY_FIELD")) {
				wsf[i] = AliasFinder.ARRAY_FIELD;
			} else {
				wsf[i] = Scene.v().getField(fieldSpecs.get(i));
			}
		}
		return wsf;
	}

	private void parseRule(String rule) {
		final String ruleLine = rule;
		if(rule.startsWith("@")) {
			final String tokens[] = rule.substring(1).split(":");
			assert tokens.length == 2 : ruleLine;
			assert tokens[1].endsWith("*") : ruleLine;
			if(!packageSpecs.containsKey(tokens[0])) {
				packageSpecs.put(tokens[0], new HashSet<String>());
			}
			packageSpecs.get(tokens[0]).add(tokens[1].substring(0, tokens[1].length() - 1));
		} else if(rule.startsWith("==")) {
			final String tokens[] = rule.substring(2).split(":");
			assert tokens.length == 2 : ruleLine;
			assert tokens[1].endsWith("*") : ruleLine;
			identityPackages.put(tokens[0], tokens[1].substring(0, tokens[1].length() - 1));
		} else {
			PropagationTarget tgt;
			if(rule.startsWith("^")) {
				tgt = PropagationTarget.RECEIVER;
				rule = rule.substring(1);
			} else if(rule.startsWith("~")) {
				tgt = PropagationTarget.FLUENT;
				rule = rule.substring(1);
			} else if(rule.startsWith("=")) {
				tgt = PropagationTarget.IDENTITY;
				rule = rule.substring(1);
			} else if(rule.startsWith("*")) {
				tgt = PropagationTarget.HAVOC;
				rule = rule.substring(1);
			} else {
				tgt = PropagationTarget.RETURN;
			}
			assert rule.startsWith("<") && rule.endsWith(">") : ruleLine;
			rule = rule.substring(1, rule.length() - 1);
			if(rule.contains(":")) {
				final String[] tokens = rule.split(":");
				assert tokens.length == 2 : ruleLine;
				if(tokens[1].endsWith("*")) {
					methodNamePrefixSpec.put(tokens[0].trim(), tokens[1].substring(0, tokens[1].length() - 1), tgt);
				} else {
					assert !methodNameSpec.contains(tokens[0].trim(), tokens[1].trim()) : ruleLine;
					methodNameSpec.put(tokens[0].trim(), tokens[1].trim(), tgt);
				}
			} else {
				assert rule.contains(",");
				final String[] tokens = rule.split(",");
				assert tokens.length == 2 : ruleLine;
				assert !returnTypeSpec.contains(tokens[0].trim(), tokens[1].trim()) : ruleLine;
				returnTypeSpec.put(tokens[0].trim(), tokens[1].trim(), tgt);
			}
		}
	}

	@Override
	public boolean isPropagationMethod(final SootMethod m) {
		final PropagationTarget rmt = resolveMethodTarget(m);
		return rmt != null;
	}
	
	private PropagationTarget resolveMethodTarget(final SootMethod m) {
		final String pName = m.getDeclaringClass().getPackageName();
		final String returnType = m.getReturnType().getEscapedName();
		final String methodName = m.getName();
		final String declaringClass = m.getDeclaringClass().getName();
		
		final boolean matchesMethodName = methodNameSpec.contains(declaringClass, methodName),
			matchesType = returnTypeSpec.contains(declaringClass, returnType), matchesSig = propagationMethodSpec.containsKey(m);
		boolean matchesPackage = false;
		if(packageSpecs.containsKey(pName)) {
			for(final String methodPrefix : packageSpecs.get(pName)) {
				if(methodName.startsWith(methodPrefix)) {
					matchesPackage = true;
					break;
				}
			}
		}
		boolean matchesNamePrefix = false;
		PropagationTarget prefixMatch = null;
		if(methodNamePrefixSpec.containsRow(declaringClass)) {
			int longestPrefix = -1;
			for(final Entry<String, PropagationTarget> pfKV : methodNamePrefixSpec.row(declaringClass).entrySet()) {
				if(methodName.startsWith(pfKV.getKey())) {
					if(pfKV.getKey().length() > longestPrefix) {
						prefixMatch = pfKV.getValue();
						longestPrefix = pfKV.getKey().length();
					}
				}
			}
			if(prefixMatch != null) {
				matchesNamePrefix = true;
			}
		}
		boolean matchesIdentityPackage = false;
		if(identityPackages.containsKey(pName)) {
			for(final String methodPrefix : identityPackages.get(pName)) {
				if(methodName.startsWith(methodPrefix)) {
					matchesIdentityPackage = true;
					break;
				}
			}

		}
		if(!(matchesMethodName || matchesPackage || matchesSig || matchesType || matchesIdentityPackage || matchesNamePrefix)) {
			return null;
		}
//		assert matchesPackage ^ matchesSig ^ matchesMethodName ^ matchesType ^ matchesIdentityPackage;
		if(matchesSig) {
			return propagationMethodSpec.get(m);
		} else if(matchesMethodName) {
			return methodNameSpec.get(declaringClass, methodName);
		} else if(matchesNamePrefix) {
			return prefixMatch;
		} else if(matchesType) {
			return returnTypeSpec.get(declaringClass, returnType);
		} else if(matchesPackage) {
			return PropagationTarget.RECEIVER;
		} else {
			return PropagationTarget.IDENTITY;
		}
	}

	@Override
	public PropagationSpec getPropagationSpec(final Unit callSite) {
		final Stmt s = (Stmt) callSite;
		final InvokeExpr ie = s.getInvokeExpr();
		final SootMethod method = ie.getMethod();
		final PropagationTarget t = resolveMethodTarget(method);
		if(t== PropagationTarget.IDENTITY) {
			return new PropagationSpec(Collections.<Local>emptySet(), t); 
		}
		final String sig = method.getSignature();
		
		final Set<Local> arguments;
		{
			arguments = new HashSet<>();
			for(final Value v : ie.getArgs()) {
				if(!(v instanceof Local)) {
					continue;
				}
				arguments.add((Local)v);
			}
		}
		final Set<Local> subFieldLocal;
		if(subfieldArgs.containsKey(sig)) {
			subFieldLocal = new HashSet<>();
			final int offs = ie instanceof InstanceInvokeExpr ? 1 : 0;
			final TIntIterator it = subfieldArgs.get(sig).iterator();
			while(it.hasNext()) {
				final int loc = it.next() - offs;
				if(loc == -1) {
					assert ie instanceof InstanceInvokeExpr;
					subFieldLocal.add((Local) ((InstanceInvokeExpr)ie).getBase());
				} else {
					assert loc >= 0;
					final Value v = ie.getArgs().get(loc);
					if(v instanceof Local) {
						subFieldLocal.add((Local) v);
					}
				}
			}
		} else {
			subFieldLocal = Collections.emptySet();
		}
		if(t == PropagationTarget.RECEIVER) {
			return new PropagationSpec(arguments, subFieldLocal, PropagationTarget.RECEIVER);
		} else if(t == PropagationTarget.FLUENT) {
			assert ie instanceof InstanceInvokeExpr;
			arguments.add(((Local)((InstanceInvokeExpr)ie).getBase()));
			return new PropagationSpec(arguments, subFieldLocal, PropagationTarget.FLUENT);
		} else if(t == PropagationTarget.GRAPH) {
			if(ie instanceof InstanceInvokeExpr) {
				arguments.add((Local) ((InstanceInvokeExpr) ie).getBase());
			}
			final GraphResolver gr = graphResolvers.get(sig);
			gr.postProcessArguments(arguments, subFieldLocal, ie);
			return new PropagationSpec(arguments, subFieldLocal, t, gr.resolveGraph(s));
		} else if(t == PropagationTarget.CONTAINER_GET || t == PropagationTarget.CONTAINER_TRANSFER || t == PropagationTarget.CONTAINER_MOVE) {
			assert ie instanceof InstanceInvokeExpr;
			return new PropagationSpec(Collections.<Local>singleton((Local)((InstanceInvokeExpr)ie).getBase()), t);
		} else if(t == PropagationTarget.CONTAINER_PUT || t == PropagationTarget.CONTAINER_REPLACE || t == PropagationTarget.CONTAINER_ADDALL) {
			return new PropagationSpec(arguments, t);
		} else {
			if(ie instanceof InstanceInvokeExpr) {
				final InstanceInvokeExpr instanceInvokeExpr = (InstanceInvokeExpr) ie;
				arguments.add((Local)instanceInvokeExpr.getBase());
			}
			if(!(s instanceof DefinitionStmt)) {
				return new PropagationSpec(Collections.<Local>emptySet(), PropagationTarget.IDENTITY);
			}
			return new PropagationSpec(arguments, subFieldLocal, PropagationTarget.RETURN);
		}
	}

	@Override
	public boolean isIdentityMethod(final SootMethod m) {
		final PropagationTarget rmt = resolveMethodTarget(m);
		if(m.isConstructor() && m.getDeclaringClass().getName().equals("java.lang.Object")) {
			return true;
		}
		return rmt != null && rmt == PropagationTarget.IDENTITY;
	}
	
	private final Set<Type> containerTypes = new HashSet<>();
	
	@Override
	public void initialize() {
		for(final Map.Entry<String, PropagationTarget> kv : propagationSpec.entrySet()) {
			if(Scene.v().containsMethod(kv.getKey())) {
				this.propagationMethodSpec.put(Scene.v().getMethod(kv.getKey()), kv.getValue());
			}
		}
		for(final String containerRoot : containerRoots) {
			if(Scene.v().containsClass(containerRoot)) {
				containerTypes.add(Scene.v().getRefType(containerRoot));
			}
		}
		containerRoots.clear();
		isContainerDerived.clear();
	}

	@Override
	public boolean isContainerType(final Type cls) {
		for(final Type t : containerTypes) {
			if(Scene.v().getOrMakeFastHierarchy().canStoreType(cls, t)) {
				return true;
			}
		}
		return false;
	}
}
