package edu.washington.instrumentation.analysis.test;

import org.testng.annotations.Test;

@Test(groups="legatoTests")
public class TestContainerAbstraction extends AbstractLegatoTest {
	public TestContainerAbstraction() {
		super("ContainerAbstractionTest", "resolver:simple-get,pm:yaml-file,pm-options:" +
				System.getProperty("legato.resources") + "/collections.yml" + ":" +
				System.getProperty("legato.test-resources") + "/propagation.yml");
	}
	
	public void testMultiAdd() {
		assertIsomorphicTreeReturn("testMultiAdd", "{1}");
	}
	
	public void testToArray() {
		assertIsomorphicTreeReturn("testToArray", "{1}");
	}
	
	public void testAddAll() {
		assertBottomReturn("testAddAll");
	}
	
	public void testReplace() {
		assertBottomReturn("testReplace");
		assertIsomorphicTreeReturn("testReplaceCons", "{1}");
	}
	
	public void testContainerAliases() {
		assertIsomorphicTreeReturn("testPutAliasSearch", "{1}");
		assertIsomorphicTreeReturn("addAllAliasSearch", "{1}");
		assertBottomReturn("containerWeakUpdate");
		assertIsomorphicTreeReturn("arrayTransferTest", "{1}");
		assertIsomorphicTreeReturn("testIteratorUpdate", "{1}");
		assertIsomorphicTreeReturn("testTransferAliases", "{1}");
	}
}
