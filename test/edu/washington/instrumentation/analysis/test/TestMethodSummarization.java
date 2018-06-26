package edu.washington.instrumentation.analysis.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import edu.washington.cse.instrumentation.analysis.AtMostOnceProblem.SummaryMode;
import edu.washington.cse.instrumentation.analysis.EverythingIsInconsistentException;

@Test(groups={"legatoTests"})
public class TestMethodSummarization extends AbstractLegatoTest {
	public TestMethodSummarization() {
		super("SummaryTest", "resolver:simple-get,pm:simple");
	}
	
	public void testFailureSummarization() {
		this.mode = SummaryMode.FAIL;
		assertAnalysisThrows("testBasicSummarization", EverythingIsInconsistentException.class);
	}

	public void testWarnSummarization() throws IOException {
		// Don't do this at home kids
		final PrintStream stream = System.out;
		final ByteArrayOutputStream sout = new ByteArrayOutputStream();
		final PrintStream ps = new PrintStream(sout);
		System.setOut(ps);
		ps.flush();
		this.mode = SummaryMode.WARN;
		assertIsomorphicTreeReturn("testBasicSummarization", "{1}");
		System.setOut(stream);
		final String analysisOutput = sout.toString();
		Assert.assertTrue(analysisOutput.contains("WARNING: Unsummarized stub functions encountered! The analysis results may be imprecise/unsound"),"Contains warning string");
		Assert.assertTrue(analysisOutput.contains(">>> Call to <java.io.PrintStream: void println(int)>"), "Contains method name");
		Assert.assertTrue(analysisOutput.contains("    - i0"), "contains argument name");
	}
	
	public void testBottomSummarization() {
		this.mode = SummaryMode.BOTTOM;
		assertBottomReturn("testBasicSummarization");
	}
	
	@Override
	protected Set<String> testMethods() {
		final Set<String> toReturn = super.testMethods();
		toReturn.add("testIdentitySummarization");
		return toReturn;
	}
}
