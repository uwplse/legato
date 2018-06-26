package edu.washington.instrumentation.analysis.test;

import org.testng.annotations.Test;

@Test(groups="legatoTests")
public class TestPropagation extends AbstractLegatoTest {
	public TestPropagation() {
		super("PropagationTest", "resolver:simple-get,pm:yaml-file,pm-options:" + System.getProperty("legato.test-resources") + "/propagation.yml");
	}
	
	public void testFluentPropagation() {
		assertIsomorphicTreeReturn("testBasicFluentPropagation", "{1}");
		assertTopReturn("testPropagationNoField");
		assertBottomReturn("testFluentReturnFlow");
		assertIsomorphicTreeReturn("testFluentReturnFlowConsistent", "{2}");
		assertBottomReturn("testNestedPropagationInconsistent");
		assertIsomorphicTreeReturn("testNestedPropagationConsistent", "{2}{1}");
		assertBottomReturn("testOverwriteFluent");
	}
	
	public void testGraphPropagation() {
		assertBottomReturn("testReturnGraphIncons");
		assertIsomorphicTreeReturn("testReturnGraphCons", "{1}");
	}
	
	public void testSubfieldPropagation() {
		assertBottomReturn("testSubfieldPropagationIncons");
		assertIsomorphicTreeReturn("testSubfieldPropagationCons", "{1}");
		assertBottomReturn("testSubfieldPropagationFlow");
	}
	
	public void testParamPropagation() {
		assertBottomReturn("testOutParamPropagationIncons");
		assertIsomorphicTreeReturn("testOutParamPropagationCons", "{1}");
		assertIsomorphicTreeReturn("testNopPropagation", "{1}");
	}
	
	public void testPropagationOverwrite() {
		assertIsomorphicTreeReturn("testPropagationOverwrite", "{1}");
	}
}
