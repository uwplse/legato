package edu.washington.instrumentation.analysis.test;

import org.testng.annotations.Test;

@Test(groups="legatoTests")
public class TestHeapAbstraction extends AbstractLegatoTest {
	public TestHeapAbstraction() {
		super("HeapTest", "resolver:simple-get,pm:simple");
	}

	@Test
	public void testInconsistentField() {
		assertBottomReturn("inconsistentFieldAdd");
	}
	
	@Test
	public void testInconsistentAliasRead() {
		assertNotBottomReturn("consistentRead");
	}
	
	@Test
	public void testConsistentField() {
		assertNotBottomReturn("consistentFieldAdd");
	}
	
	@Test
	public void testArrayWrite() {
		assertBottomReturn("basicArrayTest");
	}
	
	@Test
	public void testArrayRead() {
		assertNotBottomReturn("arrayReadTest");
	}
	
	@Test
	public void testArrayUpcast() {
		assertTopReturn("testArrayUpcast");
	}
	
	@Test
	public void testArrayUnwrap() {
		assertNotBottomReturn("testArrayUnwrap");
	}
	
	@Test
	public void testMultiArray() {
		assertNotBottomReturn("testDoubleUnwrap");
	}
	
	@Test
	public void testNarrowing() {
		assertIsomorphicTreeReturn("testNarrowing", "{1}");
		assertIsomorphicTreeReturn("testNarrowing2", "{2}{1}");
		assertIsomorphicTreeReturn("testNarrowing3", "{3}{2}{1}");
		assertNotBottomReturn("testTransitiveNarrowing");
		assertBottomReturn("testNarrowingFailure");
		assertIsomorphicTreeReturn("testChainNarrowing", "{1}{2}");
		assertBottomReturn("testChainNarrowFailure");
	}
	
	@Test
	public void testStaticFields() {
		assertBottomReturn("inconsistentStaticFieldAdd");
		assertNotBottomReturn("consistentStaticFieldAdd");
		assertNotBottomReturn("inconsistentStaticRead");
	}
	
	@Test
	public void testArrayInterprocedurality() {
		assertIsomorphicTreeReturn("testArrayInterprocedural", "{3}");
	}
	
	public void testVirtualDispatchUpdate() {
		assertIsomorphicTreeReturn("inconsistentVirtualDispatch", "({t1,2}{3}|{t1,1}{2})");
	}
	
	@Test
	public void testArrayReturned() {
		assertIsomorphicTreeReturn("testArrayReturn", "{3}{1}");
	}
	
	public void testCalleeUpdate() {
		assertBottomReturn("noStrongUpdateInCallee");
		assertIsomorphicTreeReturn("strongUpdateInCallee", "{1}");
		assertIsomorphicTreeReturn("strongUpdateInCalleeWithWrite", "{1}");
	}
	
	public void testAliasSearch() {
		assertIsomorphicTreeReturn("testAliasSearchInCaller", "{1}{2}");
	}
}
