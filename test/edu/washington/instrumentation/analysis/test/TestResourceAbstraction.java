package edu.washington.instrumentation.analysis.test;

import java.util.Set;

import org.testng.annotations.Test;

@Test(groups="legatoTests")
public class TestResourceAbstraction extends AbstractLegatoTest {
	public TestResourceAbstraction() {
		super("ResourceTest", "resolver:simple-string,resolver-options:0;<ResourceTest: int get(java.lang.String)>,pm:simple,track-all:true");
	}
	
	@Test
	public void testBasicPointwise() {
		assertNotBottomReturn("testBasicJoin", "foo", "bar");
	}
	
	@Test
	public void testUnionStrings() {
		assertBottomReturn("testUnionJoin");
	}
	
	@Test
	public void testSimpleUnion() {
		assertNotBottomReturn("testSimpleUnion", "foo", "bar");
	}
	
	@Test
	public void testConservativeStrings() {
		assertBottomReturn("testConservativeStrings");
	}
	
	@Test
	public void testConservativeStringLoop() {
		assertIsomorphicTreeReturn("testPrependRest", "{3}''");
	}
	
	@Test
	public void testPrependFunction() {
		assertIsomorphicTreeReturn("testPrependPoint", "{2}'", "foo", "bar");
	}
	
	@Override
	protected Set<String> testMethods() {
		
		final Set<String> toRet = super.testMethods();
		toRet.add("getNonDetString");
		return toRet;
	}
}
