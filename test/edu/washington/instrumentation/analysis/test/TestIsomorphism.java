package edu.washington.instrumentation.analysis.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import edu.washington.cse.instrumentation.analysis.rectree.Node;
import edu.washington.cse.instrumentation.analysis.rectree.Parser;
import edu.washington.instrumentation.analysis.test.iso.Isomorphism;

public class TestIsomorphism {
	final Parser p = new Parser();
	
	private void assertIsomorphic(final String t1, final String t2) {
		final Node n1 = p.parse(t1);
		final Node n2 = p.parse(t2);
		Assert.assertNotNull(n1);
		Assert.assertNotNull(n2);
		Assert.assertTrue(Isomorphism.isomorphic(n1, n2), n1 + " and " + n2);
	}
	
	private void assertNotIsomorphic(final String t1, final String t2) {
		final Node n1 = p.parse(t1);
		final Node n2 = p.parse(t2);
		Assert.assertFalse(Isomorphism.isomorphic(n1, n2), n1 + " and " + n2);
	}
	
	@Test
	public void testIso() { 
		assertIsomorphic("{1}", "{2}");
		assertIsomorphic("({t1,0}{1}|{t1,1}({t1,0}{1}|{t1,2}{3}))", "({t2,0}{5}|{t2,1}({t2,0}{5}|{t2,4}{4}))");
		assertIsomorphic("{1}''", "{2}''");

		assertIsomorphic(
			"({t1,0}{1}'|{t1,1}{1})",
			"({t1,0}{2}'|{t1,1}{2})"
		);
	}
	
	@Test
	public void testRejectIso() {
		assertNotIsomorphic(
				"({t1,0}({t2,0}{2}|{t2,1}{2})|{t1,1}({t3,0}{3}|{t3,1}{3}))",
				"({t1,0}({t2,0}{2}|{t2,1}{2})|{t1,1}({t3,0}{2}|{t3,1}{2}))"
		);
		
		assertNotIsomorphic(
				"({t1,0}({t2,0}{2}|{t2,1}{2})|{t1,1}({t3,0}{3}|{t3,1}{3}))",
				"({t1,0}({t2,0}{2}|{t2,1}{2})|{t1,1}({t3,0}{2}|{t3,1}{2}))"
		);
		
		assertNotIsomorphic(
			"({t1,0}({t2,0}{2}|{t2,1}{2})|{t1,1}{t1,0}{3})",
			"({t1,0}({t2,0}{1}|{t2,1}{3})|{t1,1}{t2,0}{2})"
		);
		
		assertNotIsomorphic(
			"({t1,0}{2}|{t1,1}{t1,0}{2})",
			"({t1,0}{2}|{t1,1}{t1,2}{2})"
		);
		
		assertNotIsomorphic(
				"({t1,0}{2}|{t1,1}{t1,0}{2})",
				"({t1,0}{2}|{t1,1}{t1,2}{3})"
			);
		
		assertNotIsomorphic(
			"{1}'",
			"{2}"
		);
		
		assertNotIsomorphic(
			"({t1,0}{1}|{t1,1}{2})",
			"({t1,0}{2}'|{t1,1}{2})"
		);
		
		assertNotIsomorphic(
			"({t1,0}{1}|{t1,1}{1})",
			"({t1,0}{2}'|{t1,1}{2}')"
		);
		
		assertNotIsomorphic(
			"({t1,0}{1}''|{t1,1}{1}')",
			"({t1,0}{2}'|{t1,1}{2}')"
		);
	}
}
