import java.util.ArrayList;


public class SummaryTest {
	public static int get() {
		return 3;
	}
	
	public static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}

	public static int testBasicSummarization() {
		final int a = get();
		System.out.println(a);
		System.out.println(a);
		return a;
	}
	
	public static int testIdentitySummarization() {
		final int a = get();
		final StringBuilder sb = new StringBuilder();
		sb.append(a);
		System.out.println(a);
		final ArrayList<Integer> l = new ArrayList<>();
		l.add(a);
		l.remove(a);
		return a;
	}
	
	public static void main(final String[] args) { 
		System.out.println(testIdentitySummarization());
	}
}
