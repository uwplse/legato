package edu.washington.cse.instrumentation.analysis.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.FastHierarchy;
import soot.G;
import soot.Local;
import soot.PackManager;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.jimple.InterfaceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.scalar.Pair;
import edu.washington.cse.instrumentation.analysis.LegatoConfigurer;
import edu.washington.cse.instrumentation.analysis.functions.CallParamDeciderProvider;
import edu.washington.cse.instrumentation.analysis.preanalysis.ConstantStringInliner;

public class InlineTargetFinder {
	private static final class InlineTargetTransformer extends Transform {
		public static final int AGGRESSIVENESS_LEVEL = 1;
		private final Set<Pair<String, Integer>> out;
		
		private InlineTargetTransformer(final HashSet<Pair<String, Integer>> out, final Collection<Pair<String, Integer>> indirectionSites) {
			super("wjtp.inline", new SceneTransformer() {
				@Override
				protected void internalTransform(final String phaseName, final Map<String, String> options) {
					final JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
					final FastHierarchy fh = Scene.v().getOrMakeFastHierarchy();
					final Set<String> blackList = new HashSet<>();
					for(final Pair<String, Integer> sens : indirectionSites) {
						blackList.add(sens.getO1());
					}
					for(final Pair<String, Integer> sens : indirectionSites) {
						final String sig = sens.getO1();
						final int pos = sens.getO2();
						if(!Scene.v().containsMethod(sig)) {
							continue;
						}
						final String clsName = Scene.v().signatureToClass(sig);
						final SootClass declarer = Scene.v().getSootClass(clsName);
						Collection<SootClass> toSearch;
						if(declarer.isInterface()) {
							toSearch = fh.getAllImplementersOfInterface(declarer);
						} else {
							toSearch = fh.getSubclassesOf(Scene.v().getSootClass(clsName));	
						}
						final String subSig = Scene.v().signatureToSubsignature(sig);
						this.findInlineTargets(icfg, sens, out, blackList);
						for(final SootClass cls : toSearch) {
							if(cls.declaresMethod(subSig)) {
								this.findInlineTargets(icfg, new Pair<>(cls.getMethod(subSig).getSignature(), pos), out,  blackList);
							}
						}
					}
				}

				private void findInlineTargets(final JimpleBasedInterproceduralCFG icfg, final Pair<String, Integer> sens,
						final HashSet<Pair<String, Integer>> out, final Set<String> blackList) {
					final int startPos = sens.getO2();
					final Collection<Unit> callers = icfg.getCallersOf(Scene.v().getMethod(sens.getO1()));
					final LinkedList<SearchTriple> worklist = new LinkedList<>();
					for(final Unit u : callers) {
						worklist.add(new SearchTriple(0, startPos, (Stmt) u));
					}
					while(!worklist.isEmpty()) {
						final SearchTriple curr = worklist.removeFirst();
						final Stmt callUnit = curr.callUnit;
						final int argPos = curr.argPosition;
						assert callUnit.containsInvokeExpr();
						final Value argVal = callUnit.getInvokeExpr().getArg(argPos);
						final SootMethod containingMethod = icfg.getMethodOf(callUnit);
						if(containingMethod.getName().equals("class$")) {
							continue;
						}
						if(ConstantStringInliner.getStaticString(containingMethod, callUnit, argPos) != null) {
							if(curr.unrollLevel > 0 && !(callUnit.getInvokeExpr() instanceof InterfaceInvokeExpr) &&
									!blackList.contains(callUnit.getInvokeExpr().getMethod().getSignature())) {
								out.add(new Pair<>(callUnit.getInvokeExpr().getMethod().getSignature(), curr.argPosition));
							}
						} else if(argVal instanceof Local) {
							final Local l = (Local) argVal;
							if(containingMethod.getActiveBody().getParameterLocals().contains(l) && !CallParamDeciderProvider.methodOverwriteSet(containingMethod).contains(l)) {
								if(curr.unrollLevel < AGGRESSIVENESS_LEVEL) {
									final int ind = containingMethod.getActiveBody().getParameterLocals().indexOf(l);
									assert ind > -1;
									for(final Unit u : icfg.getCallersOf(containingMethod)) {
										worklist.add(new SearchTriple(curr.unrollLevel + 1, ind, (Stmt) u));
									}
								}
							}
						}
					}
				}
			});
			this.out = out;
			setDeclaredOptions("enabled");
		}
		
		private InlineTargetTransformer(final Collection<Pair<String, Integer>> indirectSites) {
			this(new HashSet<Pair<String, Integer>>(), indirectSites);
		}
	}

	private static class SearchTriple {
		private final Stmt callUnit;
		private final int argPosition;
		private final int unrollLevel;

		public SearchTriple(final int unrollLevel, final int argPosition, final Stmt callUnit) {
			this.unrollLevel = unrollLevel;
			this.argPosition = argPosition;
			this.callUnit = callUnit;
		}

		@Override
		public String toString() {
			return "SearchTriple [callUnit=" + callUnit + ", argPosition=" + argPosition + ", unrollLevel=" + unrollLevel + "]";
		}
	}

	public static List<Pair<String, Integer>> findUnrollTargets(final LegatoConfigurer lc, final Collection<Pair<String, Integer>> indirectFlow) {
		G.v().out = new PrintStream(new ByteArrayOutputStream(){ 
	    @Override
	    public void write(final int b) {
	    }

	    @Override
	    public void write(final byte[] b, final int off, final int len) {
	    }

	    @Override
	    public void writeTo(final OutputStream out) throws IOException {
	    }
		});
		
		lc.doConfigure(false);
		Scene.v().loadNecessaryClasses();
		lc.configureEntryPoints();
		
		final InlineTargetTransformer tr = new InlineTargetTransformer(indirectFlow);
		PackManager.v().getPack("wjtp").add(tr);
		PackManager.v().getPack("cg").apply();
		PackManager.v().getPack("wjtp").apply();
		G.reset();
		return new ArrayList<>(tr.out);
	}
}
