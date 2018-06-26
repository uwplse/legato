package edu.washington.instrumentation.analysis.test;

import java.util.Set;

import org.testng.annotations.Test;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.SummaryMode;

@Test(groups={"legatoTests"})
public class TestIdentitySummarization extends AbstractLegatoTest {
	public TestIdentitySummarization() {
		super("SummaryTest", "resolver:simple-get,pm:yaml-file,pm-options:" + System.getProperty("legato.test-resources") + "/system-propagation.yml");
	}
	
	public void testIdentitySummaries() {
		this.mode = SummaryMode.BOTTOM;
		assertIsomorphicTreeReturn("testIdentitySummarization", "{4}");
	}
	
	@Override
	protected Set<String> testMethods() {
		final Set<String> toReturn = super.testMethods();
		toReturn.add("testBasicSummarization");
		return toReturn;
	}
}
