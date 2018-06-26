package edu.washington.cse.instrumentation.analysis.functions;

import java.util.Set;

import soot.FastHierarchy;
import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import boomerang.accessgraph.AccessGraph;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Table;

import edu.washington.cse.instrumentation.analysis.AnalysisConfiguration;
import edu.washington.cse.instrumentation.analysis.preanalysis.FieldPreAnalysis;
import edu.washington.cse.instrumentation.analysis.preanalysis.SyncPreAnalysis;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;

public abstract class AbstractPathFunctions {
	
	protected final JimpleBasedInterproceduralCFG icfg;
	protected final Table<Unit, AccessGraph, Set<AccessGraph>> synchReadLookup;
	protected final AccessGraph zeroValue;
	protected final SyncPreAnalysis spa;
	protected final CallParamDeciderProvider paramDeciderCache;
	
	protected final ResourceResolver resourceResolver;
	protected final PropagationManager propagationManager;
	protected final FieldPreAnalysis fpa;
	
	protected AbstractPathFunctions(
		final AnalysisConfiguration conf, final AccessGraph zeroValue,
		final SyncPreAnalysis spa,
		final FieldPreAnalysis fpa, final Table<Unit, AccessGraph, Set<AccessGraph>> synchReadLookup,
		final CallParamDeciderProvider cpdp) {
		
		this.icfg = conf.icfg;
		this.propagationManager = conf.propagationManager;
		this.resourceResolver = conf.resourceResolver;
		this.paramDeciderCache = cpdp;
		this.synchReadLookup = synchReadLookup;
		this.zeroValue = zeroValue;
		this.spa = spa;
		this.fpa = fpa;
	}
	
	protected boolean isSynchedUnit(final Unit curr) {
		return spa.getUnitToSynchMarker().contains(curr);
	}
	
	protected boolean isSyncedFieldAt(final Unit curr, final SootField f) {
		if(!spa.getUnitToSynchMarker().contains(curr)) {
			return false;
		}
		final HashMultimap<SootField, RefType> syncWriteFields = this.spa.getSyncWriteFields();
		if(!syncWriteFields.containsKey(f)) {
			return false;
		}
		final int tag = spa.getUnitToSynchMarker().get(curr);
		final RefType synchronizingType = spa.getSynchronizingType(tag);
		final Set<RefType> types = syncWriteFields.get(f);
		final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
		for(final RefType rt : types) {
			if(fh.canStoreType(rt, synchronizingType) || fh.canStoreType(synchronizingType, rt)) {
				return true;

			}
		}
		return false;
	}
	
	
	public static boolean isPhantomMethodCall(final Unit callSite, final SootMethod sm, final JimpleBasedInterproceduralCFG icfg) {
		return icfg.getCalleesOfCallAt(callSite).size() == 0 && sm.isPhantom();
	}
	
	protected boolean isPhantomMethodCall(final Unit callSite, final SootMethod sm) {
		return isPhantomMethodCall(callSite, sm, icfg);
	}
}
