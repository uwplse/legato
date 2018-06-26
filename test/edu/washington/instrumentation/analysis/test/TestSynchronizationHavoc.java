package edu.washington.instrumentation.analysis.test;

import java.util.Set;

import org.testng.annotations.Test;

import edu.washington.cse.instrumentation.analysis.EverythingIsInconsistentException;

@Test(groups="legatoTests")
public class TestSynchronizationHavoc extends AbstractLegatoTest {
	public TestSynchronizationHavoc() {
		super("SynchronizationTest", "resolver:simple-get,pm:simple");
		this.enableSyncHavoc = true;
	}

	public void testSimpleSynchronization() {
		assertBottomReturn("testSimpleSynchronization");
	}
	
	public void testSameReadSync() {
		assertIsomorphicTreeReturn("testSameReadSync", "{s2}{1}");
	}
	
	public void testSynchNarrowing() {
		assertIsomorphicTreeReturn("testSynchNarrowing", "{s2}{1}");
		assertIsomorphicTreeReturn("testInterProcNarrowing", "{s2}{1}");
		assertIsomorphicTreeReturn("testInterProcNarrowingDD", "{s2}{1}");
	}
	
	public void testInterproceduralSynch() {
		assertBottomReturn("testInterproceduralSynch");
	}
	
	public void testSyncPriming() {
		assertBottomReturn("testSyncPriming");
	}
	
	public void testNestedSynchronization() {
		assertBottomReturn("testNestedSync");
		assertBottomReturn("testInterproceduralNestedSync");
		assertIsomorphicTreeReturn("testInterproceduralNopSync", "{s1}{2}");
		assertBottomReturn("testNestedSynchronizedMethod");
	}
	
	public void testSynchronizedMethod() {
		assertIsomorphicTreeReturn("testSynchronizedMethod", "{2}{s3}{1}");
		assertBottomReturn("testSynchronizedMethodPriming");
	}
	
	public void testVolatileReads() {
		assertIsomorphicTreeReturn("testSimpleVolatileRead", "{s3}{1}");
		assertBottomReturn("testUniqueVolatileReads");
		assertBottomReturn("testNoMutexSync");
		assertBottomReturn("testVolatilePriming");
		assertIsomorphicTreeReturn("testVolatilePriming2", "{s3}'{1}");
	}
	
	public void testWaitHavoc() {
		assertBottomReturn("testSimpleWaitHavoc");
		assertIsomorphicTreeReturn("testWaitHavocPrev", "{s3}{1}");
	}
	
	public void testNestedWait() {
		assertAnalysisThrows("testNestedSyncWait", EverythingIsInconsistentException.class);
		assertAnalysisThrows("testRecursiveSync", EverythingIsInconsistentException.class);
	}
	
	@Override
	protected Set<String> testMethods() {
		final Set<String> toReturn = super.testMethods();
		toReturn.add("recursiveSyncExecute");
		return toReturn;
	}
}
