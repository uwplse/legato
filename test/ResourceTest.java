public class ResourceTest {
	public static int get(final String k) {
		return 3;
	}
	
	public static int testBasicJoin() {
		final int a = get("foo");
		final int b = get("bar");
		return a + b;
	}
	
	public static int testUnionJoin() {
		final int a = get("foo");
		final int b = get(System.currentTimeMillis() == 0 ? "foo" : "bar");
		return a + b;
	}
	
	public static int testSimpleUnion() {
		return get(System.currentTimeMillis() == 0 ? "foo" : "bar");
	}
	
	public static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	public static int testPrependRest() {
		int a = 0, b = 0, c = 0;
		while(nondetBool()) {
			b = c;
			c = a;
			a = get(getNonDetString());
		}
		return b;
	}
	
	public static int testConservativeStrings() {
		int b;
		b = get(getNonDetString());
		return b + get("bar");
	}
	
	public static int testPrependPoint() {
		int b = 0;
		int a = 0;
		while(nondetBool()) {
			b = a;
			a = get(nondetBool() ? "foo": "bar");
		}
		return b;
	}
	
	private static String getNonDetString() {
		return System.currentTimeMillis() + ""; 
	}
	
	public static void main(final String[] args) {
	}
}
