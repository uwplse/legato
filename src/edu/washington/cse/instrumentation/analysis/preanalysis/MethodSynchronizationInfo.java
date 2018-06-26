package edu.washington.cse.instrumentation.analysis.preanalysis;

import gnu.trove.TIntCollection;

import java.util.Set;

import soot.jimple.Stmt;

public class MethodSynchronizationInfo {
	public TIntCollection syncPoints;
	public TIntCollection volatileReads;
	public TIntCollection waitStmts;
	public Set<Stmt> failStmts;
}