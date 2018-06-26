package edu.washington.cse.instrumentation.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Scene;
import soot.SootMethod;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;

import com.google.common.cache.CacheBuilder;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.SummaryMode;
import edu.washington.cse.instrumentation.analysis.aliasing.AliasResolver;
import edu.washington.cse.instrumentation.analysis.cfg.CompressedInterproceduralCFG;
import edu.washington.cse.instrumentation.analysis.propagation.PropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.SimplePropagationManager;
import edu.washington.cse.instrumentation.analysis.propagation.YamlPropagationManager;
import edu.washington.cse.instrumentation.analysis.report.NullReporter;
import edu.washington.cse.instrumentation.analysis.report.Reporter;
import edu.washington.cse.instrumentation.analysis.report.SysOutReporter;
import edu.washington.cse.instrumentation.analysis.report.YamlCollectionReporter;
import edu.washington.cse.instrumentation.analysis.resource.CachedYamlResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.HybridResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.ResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.SimpleResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.SimpleStringResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.StaticResourceResolver;
import edu.washington.cse.instrumentation.analysis.resource.YamlResourceResolver;

public class AnalysisConfiguration {
	public final static class Builder implements Cloneable {
		private ResourceResolver rr;
		private PropagationManager pm;
		
		boolean syncHavoc, trackAll, haltOnFirstError, trackSites, trackStats;
		private Reporter reporter;
		private SummaryMode summaryMode;
		private final JimpleBasedInterproceduralCFG icfg;
		private String warnLog, siteLog;
		private String ignoreFile;
		private AnalysisModelExtension ext;
		
		private Builder(final JimpleBasedInterproceduralCFG icfg) {
			trackAll = false;
			haltOnFirstError = false;
			syncHavoc = true;
			summaryMode = AtMostOnceProblem.SummaryMode.IGNORE;
			reporter = new SysOutReporter();
			this.icfg = icfg;
			reporter.setIcfg(icfg);
			trackSites = false;
			ignoreFile = null;
			trackStats = false;
		}
		
		public Builder withPropagationManager(final PropagationManager pm) {
			final Builder toReturn = innerClone();
			toReturn.pm = pm;
			return toReturn;
		}
		
		public Builder withTrackSites(final boolean ts) {
			final Builder toReturn = innerClone();
			toReturn.trackSites = ts;
			return toReturn;
		}
		
		public Builder withStats(final boolean st) {
			final Builder toReturn = innerClone();
			toReturn.trackStats = st;
			return toReturn;
		}
		
		public Builder withReporter(final Reporter r) {
			final Builder toReturn = innerClone();
			r.setIcfg(icfg);
			toReturn.reporter = r;
			return toReturn;
		}
		
		public Builder withIgnoreFile(final String ignore) {
			final Builder toReturn = innerClone();
			toReturn.ignoreFile = ignore;
			return toReturn;
		}
		
		public Builder withResourceResolver(final ResourceResolver rr) {
			final Builder toReturn = innerClone();
			toReturn.rr = rr;
			return toReturn;
		}
		
		public Builder withSyncHavoc(final boolean syncHavoc) {
			final Builder toReturn = innerClone();
			toReturn.syncHavoc = syncHavoc;
			return toReturn;
		}
		
		public Builder withTrackAll(final boolean trackAll) {
			final Builder toReturn = innerClone();
			toReturn.trackAll = trackAll;
			return toReturn;
		}
		
		public Builder withSummaryMode(final SummaryMode sm) {
			final Builder toReturn = innerClone();
			toReturn.summaryMode = sm;
			return toReturn;
		}
		
		public Builder withHaltOnFirstError(final boolean hofe) {
			final Builder toReturn = innerClone();
			toReturn.haltOnFirstError = hofe;
			return toReturn;
		}
		
		public Builder withWarnLog(final String wLog) {
			final Builder toReturn = innerClone();
			toReturn.warnLog = wLog;
			return toReturn;
		}
		
		public Builder withSiteLog(final String sl) {
			final Builder toReturn = innerClone();
			toReturn.siteLog = sl;
			return toReturn;
		}
		
		public AnalysisConfiguration build() {
			if(pm == null) {
				throw new IllegalArgumentException("Failed to configure propagation manager");
			}
			if(rr == null) {
				throw new IllegalArgumentException("Failed to configure propagation manager");
			}
			
			final Collection<SootMethod> ignored = new HashSet<>(readIgnored());
			for(final String s : ReflectionEdgePredicate.REFLECTION_SIGS) {
				if(Scene.v().containsMethod(s)) {
					ignored.add(Scene.v().getMethod(s));
				}
			}
			ignored.addAll(ext.ignoredMethods());
			return new AnalysisConfiguration(
				icfg, rr, pm, syncHavoc, trackAll, haltOnFirstError, trackSites, summaryMode, reporter, warnLog, siteLog, ignored, trackStats,  ext
			);
		}
		
		private Collection<SootMethod> readIgnored() {
			if(ignoreFile == null) {
				return Collections.emptySet();
			}
			try(final BufferedReader fr = new BufferedReader(new FileReader(new File(ignoreFile)))) {
				final Set<SootMethod> accum = new HashSet<>();
				String l;
				while((l = fr.readLine()) != null) {
					if(l.trim().isEmpty()) {
						continue;
					}
					accum.add(Scene.v().getMethod(l));
				}
				return Collections.unmodifiableSet(accum);
			} catch (final IOException e) { return Collections.emptySet(); }
		}

		private Builder innerClone() {
			try {
				return (Builder) this.clone();
			} catch (final CloneNotSupportedException e) { throw new RuntimeException(); }
		}

		public Builder withExtension(final AnalysisModelExtension ext) {
			final Builder b = innerClone();
			b.ext = ext;
			return b;
		}
	}

	public final Reporter reporter;
	public final SummaryMode summaryMode;
	public final boolean haltOnFirstError;
	public final boolean trackAll;
	public final boolean syncHavoc;
	public final boolean trackSites;
	public final PropagationManager propagationManager;
	public final ResourceResolver resourceResolver;
	public final JimpleBasedInterproceduralCFG icfg;
	public final AliasResolver aliasResolver;
	public final String warnLog;
	public final String siteLog;
	public final Collection<SootMethod> ignoredMethods;
	private final CacheBuilder<?, ?> cacheBuilder;
	public final AnalysisModelExtension extension;
	
	public static final boolean RECORD_MAX_PRIMES = Boolean.parseBoolean(System.getProperty("legato.record-primes", "false"));
	public static final boolean RECORD_MAX_K = Boolean.parseBoolean(System.getProperty("legato.record-k", "false"));
	
	public static final boolean ENABLE_NARROW = Boolean.parseBoolean(System.getProperty("legato.enable-narrow", "true"));
	public static final boolean CONSERVATIVE_HEAP = Boolean.parseBoolean(System.getProperty("legato.conservative-heap", "false"));
	public static final boolean FLOW_SENSITIVE_TRANSITIVITY = Boolean.parseBoolean(System.getProperty("legato.flow-sens", "true"));
	public static final boolean VERY_QUIET = Boolean.parseBoolean(System.getProperty("legato.very-quiet", "false"));
	
	protected AnalysisConfiguration(final JimpleBasedInterproceduralCFG icfg, final ResourceResolver rr, final PropagationManager pm, final boolean syncHavoc, final boolean trackAll,
			final boolean haltOnFirstError, final boolean trackSites, final SummaryMode summaryMode, final Reporter reporter, final String warnLog, final String siteLog,
			final Collection<SootMethod> toIgnore, final boolean trackStats, final AnalysisModelExtension ext) { 
		this.resourceResolver = rr;
		this.propagationManager = pm;
		this.syncHavoc = syncHavoc;
		this.trackAll = trackAll;
		this.haltOnFirstError = haltOnFirstError;
		this.trackSites = trackSites;
		this.summaryMode = summaryMode;
		this.reporter = reporter;
		this.warnLog = warnLog;
		this.siteLog = siteLog;
		this.ignoredMethods = toIgnore;
		
		this.icfg = icfg;
		this.cacheBuilder = trackStats ? CacheBuilder.newBuilder().recordStats() : CacheBuilder.newBuilder();
		this.aliasResolver = new AliasResolver(icfg, pm, ignoredMethods, ext);
		
		this.extension = ext;
		extension.setConfig(this);
	}
	
	protected AnalysisConfiguration(final JimpleBasedInterproceduralCFG icfg,
			final ResourceResolver rr,
			final PropagationManager pm, final boolean syncHavoc,
			final boolean trackAll, final boolean haltOnFirstError, final boolean trackSites,
			final SummaryMode summaryMode,
			final Reporter reporter, final String warnLog,
			final String siteLog, final AliasResolver aliasResolver,
			final Collection<SootMethod> toIgnore,
			final CacheBuilder<?, ?> builder, final AnalysisModelExtension ext) {
		this.resourceResolver = rr;
		this.propagationManager = pm;
		this.syncHavoc = syncHavoc;
		this.trackAll = trackAll;
		this.haltOnFirstError = haltOnFirstError;
		this.trackSites = trackSites;
		this.summaryMode = summaryMode;
		this.reporter = reporter;
		this.warnLog = warnLog;
		this.siteLog = siteLog;
		
		this.ignoredMethods = toIgnore;
		
		this.icfg = icfg;
		this.aliasResolver = aliasResolver;
		this.cacheBuilder = builder;
		this.extension = ext;
	}

	public static Builder newBuilder(final JimpleBasedInterproceduralCFG icfg) {
		return new Builder(icfg);
	}
	
	public static AnalysisConfiguration parseConfiguration(final Map<String, String> options, final AnalysisModelExtension ext) {
		final JimpleBasedInterproceduralCFG icfg = new CompressedInterproceduralCFG();
		Builder b = new Builder(icfg);
		
		final String resolverType = options.get("resolver");
		final String option = options.get("resolver-options");
		final ResourceResolver rr = parseResolver(icfg, resolverType, option);
		
		final PropagationManager pm;
		final String propagationType = options.get("pm");
		final String propagationOption= options.get("pm-options");
		pm = parsePropagation(propagationType, propagationOption);
		pm.initialize();
		
		b = b.withPropagationManager(pm).withResourceResolver(rr);
		
		if(options.containsKey("sync-havoc")) {
			b = b.withSyncHavoc(Boolean.parseBoolean(options.get("sync-havoc")));
		}
		
		if(options.containsKey("hofe")) {
			b = b.withHaltOnFirstError(Boolean.parseBoolean(options.get("hofe")));
		}
		
		if(options.containsKey("track-all")) {
			b = b.withTrackAll(Boolean.parseBoolean(options.get("track-all")));
		}
		
		if(options.containsKey("summary-mode")) {
			b = b.withSummaryMode(SummaryMode.valueOf(options.get("summary-mode").toUpperCase()));
		}
		
		if(options.containsKey("warn-log")) {
			b = b.withWarnLog(options.get("warn-log"));
		}
		
		if(options.containsKey("track-sites")) {
			b = b.withTrackSites(Boolean.parseBoolean(options.get("track-sites")));
		}
		
		if(options.containsKey("site-log")) {
			b = b.withSiteLog(options.get("site-log"));
		}
		
		if(options.containsKey("ignore-file")) {
			b = b.withIgnoreFile(options.get("ignore-file"));
		}
		
		if(options.containsKey("stats")) {
			b = b.withStats(Boolean.parseBoolean(options.get("stats")));
		}
		
		if(options.containsKey("output")) {
			final String outputType = options.get("output");
			if(outputType.equals("quiet")) {
				b = b.withReporter(new NullReporter());
			} else if(outputType.equals("console")) {
				b = b.withReporter(new SysOutReporter());
			} else if(outputType.equals("yaml")) {
				b = b.withReporter(new YamlCollectionReporter(options.get("output-opt")));
			} else {
				throw new IllegalArgumentException("illegal output type: " + outputType);
			}
		}
		
		return b.withExtension(ext).build();
	}

	public static PropagationManager parsePropagation(final String propagationType, final String propagationOption) {
		final PropagationManager pm;
		if(propagationType.equals("simple")) {
			pm = new SimplePropagationManager();
		} else if(propagationType.equals("yaml-file")) {
			pm = new YamlPropagationManager(propagationOption);
		} else {
			throw new RuntimeException("Invalid propagation manager");
		}
		pm.initialize();
		return pm;
	}

	public static ResourceResolver parseResolver(final JimpleBasedInterproceduralCFG icfg, final String resolverType, final String option) {
		final ResourceResolver rr;
		if(resolverType.equals("simple-get")) {
			rr = new SimpleResourceResolver();
		} else if(resolverType.equals("yaml-file")) {
			rr = new YamlResourceResolver(option);
		} else if(resolverType.equals("cached-yaml")) {
			rr = new CachedYamlResourceResolver(option, icfg);
		} else if(resolverType.equals("simple-string")) {
			rr = new SimpleStringResourceResolver(option);
		} else if(resolverType.equals("hybrid")) {
			rr = new HybridResourceResolver(option, icfg);
		} else if(resolverType.equals("static")) {
			rr = new StaticResourceResolver(option, icfg);
		} else {
			throw new RuntimeException("Invalid resolver");
		}
		return rr;
	}
	
	public AnalysisConfiguration copyWithResolver(final ResourceResolver resourceResolver) {
		return new AnalysisConfiguration(icfg, resourceResolver, propagationManager, syncHavoc,
				trackAll, haltOnFirstError, trackSites, summaryMode, reporter, warnLog, siteLog, aliasResolver, ignoredMethods, cacheBuilder, extension);
	}

	@SuppressWarnings("unchecked")
	public <K, V> CacheBuilder<K, V> cacheBuilder() {
		return (CacheBuilder<K, V>) cacheBuilder;
	}
}
