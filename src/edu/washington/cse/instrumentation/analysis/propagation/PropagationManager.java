package edu.washington.cse.instrumentation.analysis.propagation;

import soot.SootMethod;
import soot.Type;
import soot.Unit;

public interface PropagationManager {
	public static enum PropagationTarget {
		RECEIVER,
		RETURN,
		FLUENT, IDENTITY, GRAPH,
		
		HAVOC,
		
		CONTAINER_PUT, CONTAINER_GET,
		
		CONTAINER_REPLACE, CONTAINER_ADDALL, CONTAINER_TRANSFER, CONTAINER_MOVE,
		
		// AKA never call this function 
		DIE;

		public boolean isContainerAbstraction() {
			return this.name().startsWith("CONTAINER_");
		}

		public boolean isContainerWrite() {
			return this == CONTAINER_ADDALL || this == CONTAINER_PUT;
		}
	}
	
	boolean isPropagationMethod(SootMethod m);
	PropagationSpec getPropagationSpec(Unit callSite);
	boolean isIdentityMethod(SootMethod sm);
	
	void initialize();
	boolean isContainerType(Type t);
}
