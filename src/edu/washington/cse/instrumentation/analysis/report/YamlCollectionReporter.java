package edu.washington.cse.instrumentation.analysis.report;

import heros.EdgeFunction;
import heros.edgefunc.EdgeIdentity;
import heros.solver.PathEdge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.util.HashMultiMap;
import soot.util.MultiMap;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem;
import edu.washington.cse.instrumentation.analysis.EdgeFunctionPair;
import edu.washington.cse.instrumentation.analysis.rectree.EffectEdgeIdentity;
import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.PrependFunction;
import edu.washington.cse.instrumentation.analysis.rectree.RecTreeDomain;
import edu.washington.cse.instrumentation.analysis.rectree.TreeEncapsulatingFunction;
import edu.washington.cse.instrumentation.analysis.rectree.TreeFunction;
import edu.washington.cse.instrumentation.analysis.solver.LegatoValueNode;

public class YamlCollectionReporter implements Reporter {
	
	private JimpleBasedInterproceduralCFG icfg;
	private final Map<PathEdge<Unit, AccessGraph>, FlowReport> savedFlows = new HashMap<>();
	private final Map<LegatoValueNode, ValueReport> savedValues = new HashMap<>();
	
	private final Map<LegatoValueNode, RecTreeDomain> lostStaticFlows = new HashMap<>();
	private final Map<LegatoValueNode, RecTreeDomain> lostHeapFlows = new HashMap<>();
	
	private static class FlowReport {
		public final EdgeFunctionPair functions;
		public final boolean predConsistent;
		
		public FlowReport(final EdgeFunctionPair p, final boolean isPathSensitivity) {
			this.functions = p;
			this.predConsistent = isPathSensitivity;
		}
	}
	
	private static class ValueReport {
		public final Table<List<Unit>, AccessGraph, RecTreeDomain> incoming;
		public boolean predecessorConsistent;
		private final List<List<Unit>> dupContexts;
		
		public ValueReport(final Table<List<Unit>, AccessGraph, RecTreeDomain> joinedValues,
				final boolean predecessorConsistent, final List<List<Unit>> dupContexts) {
			this.incoming = joinedValues;
			this.predecessorConsistent = predecessorConsistent;
			this.dupContexts = dupContexts;
		}
	}
	
	private final String outputFile;
	private Unit initialUnit;
	public YamlCollectionReporter(final String outputFile) { 
		this.outputFile = outputFile;
	}

	@Override
	public void handleInconsistentValue(final List<Unit> context, final Unit target, final AccessGraph targetVal,
			final Table<List<Unit>, AccessGraph, RecTreeDomain> joinedValues, final boolean predecessorConsistent, final List<List<Unit>> dupContexts) {
		savedValues.put(new LegatoValueNode(context, target, targetVal), new ValueReport(joinedValues, predecessorConsistent, dupContexts));
	}

	@Override
	public void handleInconsistentFlow(final AccessGraph sourceVal, final Unit target, final AccessGraph targetVal,
			final EdgeFunction<RecTreeDomain> firstTree, final EdgeFunction<RecTreeDomain> secondTree, final boolean predecessorConsistent) {
		final PathEdge<Unit, AccessGraph> key = new PathEdge<Unit, AccessGraph>(sourceVal, target, targetVal);
		savedFlows.put(key, new FlowReport(new EdgeFunctionPair(firstTree, secondTree), predecessorConsistent));
	}
	
	public int getUnitTag(final Unit in) {
		final SootMethod m = icfg.getMethodOf(in);
		final String cmp = in.toString();
		int cnt = 0;
		for(final Unit u : m.getActiveBody().getUnits()) {
			if(u.toString().equals(cmp) && in != u) {
				cnt++;
			} else if(in == u) {
				return cnt;
			}
		}
		throw new RuntimeException();
	}

	public void dumpReports(final PrintWriter pw) {
		final List<Map<String, Object>> toDump = new ArrayList<>();
		for(final Entry<PathEdge<Unit, AccessGraph>, FlowReport> kv : savedFlows.entrySet()) {
			final Map<String, Object> entry = new HashMap<>();
			entry.put("report-type", "flow");
			final PathEdge<Unit, AccessGraph> path = kv.getKey();
			{
				final PathEdge<Unit, AccessGraph> key = path;
				entry.put("key", Arrays.asList(key.factAtSource().toString(), key.getTarget().toString() + "|" + getUnitTag(key.getTarget()), key.factAtTarget().toString()));
			}
			entry.put("containing-method", icfg.getMethodOf(path.getTarget()).toString());
			final FlowReport report = kv.getValue();
			final EdgeFunction<RecTreeDomain> fstFunction = report.functions.getO1();
			final EdgeFunction<RecTreeDomain> sndFunction = report.functions.getO2();
			entry.put("failing", findFailingProps(fstFunction, sndFunction));
			entry.put("fst-string", fstFunction.toString());
			entry.put("snd-string", sndFunction.toString());
			entry.put("fst", serializeFunction(fstFunction));
			entry.put("snd", serializeFunction(sndFunction));
			
			entry.put("target", path.factAtTarget().toString());
			if(!path.factAtTarget().isStatic()) {
				entry.put("target-base-type", path.factAtTarget().getBaseType().toString());
			}
			entry.put("target-unit", path.getTarget().toString());
			entry.put("target-num", AtMostOnceProblem.getUnitNumber(path.getTarget()));
			
			entry.put("sensitivity", report.predConsistent);
			
			toDump.add(entry);
		}
		for(final Entry<LegatoValueNode, ValueReport> c : savedValues.entrySet()) {
			final Map<String, Object> entry = new HashMap<>();
			entry.put("report-type", "value");
			final LegatoValueNode nodeKey = c.getKey();
			final Unit targetUnit = nodeKey.getTarget();
			final List<Unit> context = nodeKey.context();
			final AccessGraph targetGraph = nodeKey.factAtTarget();
			commonValueReport(entry, targetUnit, context, targetGraph);
			final Table<List<Unit>, AccessGraph, RecTreeDomain> incomingValues = c.getValue().incoming;
			{
				final ArrayList<String> inputVals = new ArrayList<>();
				for(final AccessGraph ag : incomingValues.columnKeySet()) {
					inputVals.add(ag.toString());
				}
				entry.put("inputs", inputVals);
			}
			{
				final Map<String, Object> joinVals = new HashMap<>();
				for(final Cell<List<Unit>, AccessGraph, RecTreeDomain> t : incomingValues.cellSet()) {
					final Map<String, Object> treeSerialized = new HashMap<>();
					t.getValue().serialize(treeSerialized);
					
					final String p;
					if(incomingValues.rowKeySet().size() > 0) {
						p = t.getRowKey() + "|" + t.getColumnKey();
					} else {
						p = t.getColumnKey().toString();
					}
					
					joinVals.put(p, treeSerialized);
				}
				entry.put("vals", joinVals);
			}
			if(c.getValue().dupContexts != null && c.getValue().dupContexts.size() > 0) {
				final List<List<Integer>> extraContext = new ArrayList<>();
				for(final List<Unit> dc : c.getValue().dupContexts) {
					extraContext.add(contextToTagList(dc));
				}
				entry.put("dup-contexts", extraContext);
			}
			entry.put("failing", findFailingProps(incomingValues.values()));
			entry.put("predecessor", c.getValue().predecessorConsistent);
			if(incomingValues.rowKeySet().size() > 1) {
				entry.put("context-sens", true);
			}
			toDump.add(entry);
		}
		reportLostFlows(toDump, lostHeapFlows, "lost-heap");
		reportLostFlows(toDump, lostStaticFlows, "lost-static");
		final DumperOptions dOptions = new DumperOptions();
		dOptions.setWidth(Integer.MAX_VALUE);
		final Yaml l = new Yaml(dOptions);
		pw.println(l.dump(toDump));
		pw.flush();
	}
	
	public List<String> contextToStringList(final List<Unit> context) {
		final ArrayList<String> toReturn = new ArrayList<>();
		for(final Unit u : context) {
			if(u == initialUnit) {
				toReturn.add(u.toString());
			} else {
				toReturn.add(u.toString() + "|" + getUnitTag(u));
			}
		}
		return toReturn;
	}
	
	private void commonValueReport(final Map<String, Object> entry, final Unit targetUnit, final List<Unit> context, final AccessGraph targetGraph) {
		final List<String> contextList = contextToStringList(context);
		entry.put("key", Arrays.asList(contextList, targetGraph.toString(), targetUnit.toString() + "|" + getUnitTag(targetUnit)));
		entry.put("target", targetGraph.toString());
		if(!targetGraph.isStatic()) {
			entry.put("target-base-type", targetGraph.getBaseType().toString());
		}
		
		entry.put("target-unit", targetUnit.toString());
		entry.put("target-num", Scene.v().getUnitNumberer().get(targetUnit));
		
		entry.put("containing-method", icfg.getMethodOf(targetUnit).toString());
		entry.put("context", contextList);
		entry.put("context-num", contextToTagList(context));
	}
	
	private List<Integer> contextToTagList(final List<Unit> context) {
		final ArrayList<Integer> toReturn = new ArrayList<>();
		for(final Unit u : context) {
			if(u == initialUnit) {
				toReturn.add(-1);
			} else {
				toReturn.add((int) Scene.v().getUnitNumberer().get(u));
			}
		}
		return toReturn;
	}

	private void reportLostFlows(final List<Map<String, Object>> toDump, final Map<LegatoValueNode, RecTreeDomain> lostValues, final String reportTag) {
		for(final Map.Entry<LegatoValueNode, RecTreeDomain> report : lostValues.entrySet()) {
			final Map<String, Object> entry = new HashMap<>();
			entry.put("report-type", reportTag);
			final LegatoValueNode valueNode = report.getKey();
			commonValueReport(entry, valueNode.getTarget(), valueNode.context(), valueNode.factAtTarget());
			final Map<String, Object> val = new HashMap<>();
			report.getValue().serialize(val);
			entry.put("value", val);
			toDump.add(entry);
		}
	}

	private List<String> findFailingProps(final EdgeFunction<RecTreeDomain> fstFunction, final EdgeFunction<RecTreeDomain> sndFunction) {
		final boolean hasTreeType1 = fstFunction instanceof TreeFunction || fstFunction instanceof PrependFunction;
		final boolean hasTreeType2 = sndFunction instanceof TreeFunction || sndFunction instanceof PrependFunction;
		if(hasTreeType1 && hasTreeType2) {
			return findFailingProps(Arrays.asList(
					((TreeEncapsulatingFunction)fstFunction).getTree(), 
					((TreeEncapsulatingFunction)sndFunction).getTree()));
		} else {
			return null;
		}
	}

	private Map<String, Object> serializeFunction(final EdgeFunction<RecTreeDomain> d) {
		final Map<String, Object> toReturn = new HashMap<>();
		if(d instanceof PrependFunction) {
			final PrependFunction prependFunction = (PrependFunction) d;
			toReturn.put("type", "pp");
			toReturn.put("props", Collections.singletonList("*"));
			toReturn.put("effect", prependFunction.getEffect().toString());
			final Map<String, Object> roots = new HashMap<>();
			prependFunction.paramTree.serialize(roots);
			toReturn.put("root", roots);
		} else if(d instanceof TreeFunction && ((TreeFunction)d).isBottom()) {
			toReturn.put("type", "bot");
		} else if(d instanceof TreeFunction) {
			final TreeFunction treeFunction = (TreeFunction) d;
			toReturn.put("type", "t");
			final List<String> props = new ArrayList<>();
			if(treeFunction.tree.pointwiseRoots != null) {
				props.addAll(treeFunction.tree.pointwiseRoots.keySet());
			}
			if(treeFunction.tree.restRoot != null) {
				props.add("*");
			}
			toReturn.put("props", props);
			toReturn.put("effect", treeFunction.getEffect().toString());
			final Map<String, Object> roots = new HashMap<>();
			treeFunction.tree.serialize(roots);
			toReturn.put("root", roots);
		} else if(d instanceof EffectEdgeIdentity) {
			final EffectEdgeIdentity effectEdgeIdentity = (EffectEdgeIdentity) d;
			toReturn.put("type", "id");
			toReturn.put("effect", effectEdgeIdentity.getEffect().toString());
		} else if(d instanceof EdgeIdentity) {
			toReturn.put("type", "id");
			toReturn.put("effect", "NONE");
		} else {
			throw new RuntimeException("Failed to handle type: "+ d.getClass());
		}
		return toReturn;
	}
	
	private List<String> findFailingProps(final Collection<RecTreeDomain> rd) {
		final MultiMap<String, Node> props = new HashMultiMap<>();
		final List<Node> restNodes = new ArrayList<>();
		boolean hasPoint = false, hasRest = false;
		{
			for(final RecTreeDomain trd : rd) {
				if(trd == RecTreeDomain.BOTTOM) {
					continue;
				}
				if(trd.restRoot != null) {
					restNodes.add(trd.restRoot);
					hasRest = true;
				}
				if(trd.pointwiseRoots != null) {
					hasPoint = true;
					for(final Map.Entry<String, Node> kv : trd.pointwiseRoots.entrySet()) {
						props.put(kv.getKey(), kv.getValue());
					}
				}
			}
		}
		if(hasRest && hasPoint) {
			final HashSet<String> s = new HashSet<>();
			s.addAll(props.keySet());
			s.add("*");
			return new ArrayList<>(s);
		} else if(hasPoint) {
			final ArrayList<String> toRet = new ArrayList<>();
			outer: for(final String p : props.keySet()) {
				Node node = null;
				for(final Node rtd : props.get(p)) {
					if(node == null) {
						node = rtd;
					} else {
						node = node.joinWith(rtd);
					}
					if(node == null) {
						toRet.add(p);
						continue outer;
					}
				}
			}
			return toRet;
		} else if(hasRest) {
			return Collections.singletonList("*");
		} else {
			return null;
		}
	}

	@Override
	public void setIcfg(final JimpleBasedInterproceduralCFG icfg) {
		this.icfg = icfg;
	}

	@Override
	public void finish() {
		if(outputFile == null) {
			this.dumpReports(new PrintWriter(System.err));
		} else {
			try(PrintWriter pw = new PrintWriter(new File(outputFile))) {
				this.dumpReports(pw);	
			} catch (final FileNotFoundException e) { }
		}
	}

	@Override
	public void setInitialContext(final List<Unit> u) {
		this.initialUnit = u.get(0);
	}

	@Override
	public void handleHeapTimeout(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) {
		lostHeapFlows.put(new LegatoValueNode(context, unit, fact), val);
	}

	@Override
	public void handleLostStaticFlow(final List<Unit> context, final Unit unit, final AccessGraph fact, final RecTreeDomain val) {
		lostStaticFlows.put(new LegatoValueNode(context, unit, fact), val);
	}
}
