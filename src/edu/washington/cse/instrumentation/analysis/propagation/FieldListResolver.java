package edu.washington.cse.instrumentation.analysis.propagation;

import soot.Local;
import soot.SootField;
import soot.jimple.Stmt;
import boomerang.AliasFinder;
import boomerang.accessgraph.WrappedSootField;

public abstract class FieldListResolver implements GraphResolver {
	protected final SootField[] fields;
	public FieldListResolver(final SootField[] fields) {
		this.fields = fields;
	}

	protected WrappedSootField[] getWrappedSootFields(final Stmt callSite, final Local outputLocal) {
		final WrappedSootField[] wsf = new WrappedSootField[fields.length];
		for(int i = 0; i < wsf.length; i++) {
			if(fields[i] == AliasFinder.ARRAY_FIELD) {
				if(i == 0) {
					wsf[i] = new WrappedSootField(fields[i], outputLocal.getType(), callSite);
				} else {
					wsf[i] = new WrappedSootField(fields[i], fields[i-1].getType(), callSite);
				}
			} else {
				wsf[i] = new WrappedSootField(fields[i], fields[i].getType(), callSite);
			}
		}
		return wsf;
	}

}
