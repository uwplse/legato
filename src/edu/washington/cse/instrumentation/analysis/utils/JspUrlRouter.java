package edu.washington.cse.instrumentation.analysis.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JspUrlRouter {
	private final List<Pattern> patterns = new ArrayList<>();
	private final List<String> classes = new ArrayList<>();
	private final Map<String, String> namedMapping;
	public JspUrlRouter(final Map<String, Object> routing) {
		@SuppressWarnings("unchecked")
		final Map<String, String> patternMapping = (Map<String, String>) routing.get("mapping");
		@SuppressWarnings("unchecked")
		final List<String> order = (List<String>) routing.get("pattern_order");
		for(final String p : order) {
			if(!p.contains("*")) {
				patterns.add(Pattern.compile("^" + p + "$", 0));
			} else {
				final String patt = "^" + p.replace("*", ".+") + "$";
				patterns.add(Pattern.compile(patt));
			}
			classes.add(patternMapping.get(p));
		}
		
		@SuppressWarnings("unchecked")
		final Map<String, String> namedMapping = (Map<String, String>) routing.get("router_names");
		this.namedMapping = namedMapping;
	}
	
	public String resolveDispatcher(String url) {
		final int queryStart = url.indexOf("?");
		if(queryStart != -1) {
			url = url.substring(0, queryStart);
		}
		for(int i = 0; i < patterns.size(); i++) {
			if(patterns.get(i).matcher(url).matches()) {
				return classes.get(i);
			}
		}
		return null;
	}

	public String resolveNamedDispatcher(final String arg) {
		if(namedMapping.containsKey(arg)) {
			return namedMapping.get(arg);
		}
		return null;
	}

	public Collection<String> getDispatchClasses() {
		return classes;
	}

}
