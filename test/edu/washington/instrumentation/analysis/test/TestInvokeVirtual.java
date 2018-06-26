package edu.washington.instrumentation.analysis.test;

import org.testng.annotations.Test;

@Test(groups = "legatoTests")
public class TestInvokeVirtual extends AbstractLegatoTest {

	public TestInvokeVirtual() {
		super("InheritanceTest", "resolver:simple-get,pm:simple");
	}
	
	@Test
	public void testInvokeVirtual() {
		assertIsomorphicTreeReturn("testInvokeVirtual", "({t2,1}{4}|{t2,2}{3})");
	}
	
	@Test
	public void testInvokeVirtualLoop() {
		assertBottomReturn("testDynamicDispatchLoop");
		assertBottomReturn("testTransitiveDynamicDispatchLoop");
	}
	
	@Test
	public void testInvokeVirtualNarrowing() {
		assertIsomorphicTreeReturn("testDynamicNarrowing", "{1}");
		assertIsomorphicTreeReturn("testTransitiveDynamicNarrowing", "{1}");
		assertIsomorphicTreeReturn("testTransitiveNarrowing", "{2}({t1,2}{3}|{t1,1}{4})");
	}
}
