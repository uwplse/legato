package edu.washington.cse.instrumentation.analysis.functions;

import soot.Local;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.CastExpr;
import soot.jimple.Constant;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.NewExpr;
import soot.jimple.NullConstant;
import soot.jimple.NumericConstant;
import soot.jimple.StaticFieldRef;

public class ClassifyDefinition {
	public enum LHSType {
		ARRAY_WRITE,
		FIELD_WRITE,
		STATIC_WRITE,
		LOCAL,
	}
	public enum RHSType {
		FIELD_READ,
		STATIC_READ,
		LOCAL,
		NEW,
//		NEW_ARRAY,
//		NEW_MULTIARRAY,
		NULL,
		CONST,
		REF_CONST,
		EXPR,
		CAST,
		ARRAY_READ
	}
	public static class DefinitionType {
		public final LHSType lhsType;
		public final RHSType rhsType;
		private DefinitionType(final LHSType t1, final RHSType t2) {
			this.lhsType = t1;
			this.rhsType = t2;
		}
		public boolean isKillRHS() {
			return rhsType == RHSType.NEW || rhsType == RHSType.CONST || rhsType == RHSType.REF_CONST || rhsType == RHSType.NULL; 
		}
		
		public boolean isLocalRHS() {
			return rhsType == RHSType.CAST || rhsType == RHSType.LOCAL;
		}
		
		public FieldRef extractFieldRHS(final Value v) {
			assert rhsType == RHSType.FIELD_READ || rhsType == RHSType.STATIC_READ : rhsType;
			return (FieldRef)v;
		}
		
		public Local extractLocalRHS(final Value v) {
			assert isLocalRHS() : rhsType;
			if(rhsType == RHSType.CAST) {
				assert v instanceof CastExpr;
				return (Local) ((CastExpr)v).getOp();
			} else if(rhsType == RHSType.LOCAL) {
				return (Local) v;
			} else {
				assert false : v + " " + rhsType;
				return null;
			}
		}
	}
	
	public static DefinitionType classifyDef(final DefinitionStmt def) {
		final Value lhs = def.getLeftOp();
		final Value rhs = def.getRightOp();
		final LHSType l;
		final RHSType r;
		if(lhs instanceof InstanceFieldRef) {
			l = LHSType.FIELD_WRITE;
		} else if(lhs instanceof Local) {
			l = LHSType.LOCAL;
		} else if(lhs instanceof StaticFieldRef) {
			l = LHSType.STATIC_WRITE;
		} else if(lhs instanceof ArrayRef) {
			l = LHSType.ARRAY_WRITE;
		} else {
			throw new RuntimeException("Unexpected LHS form: " + lhs);
		}
		if(rhs instanceof NullConstant) {
			r = RHSType.NULL;
		} else if(rhs instanceof InstanceFieldRef) {
			r = RHSType.FIELD_READ;
		} else if(rhs instanceof StaticFieldRef) {
			r = RHSType.STATIC_READ;
		} else if(rhs instanceof NewExpr) {
			r = RHSType.NEW;
		} else if(rhs instanceof Local) {
			r = RHSType.LOCAL;
		} else if(rhs instanceof NumericConstant) {
			r = RHSType.CONST;
		} else if(rhs instanceof Constant) {
			r = RHSType.REF_CONST;
		} else if(rhs instanceof CastExpr) {
			final CastExpr ce = (CastExpr)rhs;
			if(ce.getOp() instanceof Local) {
				r = RHSType.CAST;
			} else if(ce.getOp() instanceof Constant){
				r = RHSType.NULL;
			} else {
				r = RHSType.EXPR;
			}
		} else if(rhs instanceof ArrayRef) {
			r = RHSType.ARRAY_READ;
//		} else if(rhs instanceof NewArrayExpr) {
//			r = RHSType.NEW_ARRAY;
//		} else if(rhs instanceof NewMultiArrayExpr) {
//			r = RHSType.NEW_MULTIARRAY;
		} else {
			r = RHSType.EXPR;
		}
		return new DefinitionType(l, r);
	}
}
