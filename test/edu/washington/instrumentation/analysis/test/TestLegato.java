package edu.washington.instrumentation.analysis.test;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(groups = "legatoTests")
public class TestLegato extends AbstractLegatoTest {
	public TestLegato() {
		super("LoopTest", "resolver:simple-get,pm:simple");
	}
	
	@Test
	public void testProblem5() {
		assertIsomorphicTreeResult("problem5", "{1}");
	}
	
	@Test
	public void testInconsistentAdd() {
		assertBottomResult("inconsistentAdd");
	}
	
	@Test
	public void testConsistentAdd() {
		assertIsomorphicTreeResult("consistentAdd", "{5}{1}");
	}
	
	@Test
	public void testSimpleTracking() {
		assertIsomorphicTreeResult("doThing", "{3}{1}");
	}
	
	@Test
	public void testProblem6() {
		assertNotBottomResult("problem6");
	}
	
	@Test
	public void testProblem7() {
		assertBottomResult("problem7");
	}
	
	@Test
	public void testProblem8() {
		assertBottomResult("problem8");
	}
	
	@Test
	public void testProblem9() {
		assertIsomorphicTreeResult("problem9", "{2}{1}'");
	}
	
	@Test
	public void testProblem1() {
		assertBottomResult("problem1");
	}
	
	@Test
	public void testProblem2() {
		assertBottomResult("problem2");
	}
	
	@Test
	public void testProblem10() {
		assertIsomorphicTreeResult("problem10", "{2}{1}''");
	}
	
	public void testReturnFlow() {
		assertBottomResult("returnFlowTest");
	}
	
	public void testCast() {
		assertIsomorphicTreeResult("testCast", "{2}{1}");
	}
	
	@BeforeTest
	public void runAnalyses() {
		super.runAnalyses("testDriver");
	}

}
