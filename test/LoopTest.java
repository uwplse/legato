

//package edu.washington.cse.instrumentation.analysis;

public class LoopTest {
	public static int get() {
		return 3;
	}
	
	private static class Whatever {
		 public static int staticF;
		 int f;
		 int g;
	}
	
	private static void blah(final int y) {
		new Whatever().f = 5 + y;
	}
	
	private static int doThing() { // { a }
		return get() + 1; // a
	}
	
	
	private static int doOtherThing(final int b, final int c) { // { b }
		int d = 0;
		if(c == 0) {
			d = get() + b;
		}
		return d;
	}
	
//	
	private static int mutualRecursion(final int d, final int e) {
		if(d == e) {
			return e;
		} else {
			return recursionTest(d, e);
		}
	}
	
	private static int recursion2(final int a) {
		if(a == 0) {
			return a;
		} else {
			return recursion2(a);
		}
	}
	
	private static int recursionTest(final int a, final int b) {
		int ret;
		if(a == 0) {
			ret = a;
		} else {
			final int r = get();
			ret = recursionTest(b, r);
		}
		return ret;
	}

	private static int foo(final int a, final int b) {
		int tmp;
		if(a == b) {
			tmp = bar(a);
		} else {
			tmp = b;
		}
		return tmp;
	}
	
	private static int bar(final int a) {
		int b;
		if(a == 0) {
			b = a;
//			return a;
		} else {
			b = get();
//			return get();
		}
		return b+b;
	}
	
	private static int problem10(final boolean x) {
		int a = 0;
		int b = 0;
		int c = 0;
		while(x) {
			c = b;
			b = a;
			a = get();
		}
		return c;
	}

	private static int problem1() {
		int a = 0, b = 0;
		while(nondetBoolean()) {
			if(nondetBoolean()) {
				b = a;
			}
			a = get();
		}
		return b;
	}
	
	private static int problem2() {
		int b = 0;
		int a = 0;
		while(nondetBoolean()) {
			a = b;
			if(nondetBoolean()) {
				b = get();
			}
		}
		return b + a;
	}
	
	private static int problem4(final boolean d) {
		int b = get(), c = 0;
		while(d) {
			if(d) {
				b = c;
			} else {
				c = b;
			}
		}
		return b;
	}
	
	private static int problem5(final int d, final boolean x) {
		int b = 0;
		while(x) {
			if(x) {
				b = d;
			}
		}
		return b;
	}
	
	private static int wrap() {
		return get();
	}
	
	private static int add(final int a, final int b) {
		return a + b;
	}
	
	private static int problem6() {
		return doOtherThing(0, get());
	}
	
	private static int problem7() {
		return doOtherThing(get(), 0);
	}
	
	private static int problem8() {
		final int a = wrap();
		final int b = wrap();
		return a + b;
	}

	private static int problem9(final boolean x) {
		int a = 0, b = 0, c = 0;
		while(x) {
			b = a;
			c = b;
			a = get();
		}
		return b + c;
	}
	
	private static int inconsistentAdd() {
		return add(get(), get());
	}
	

	private static int consistentAdd() {
		return add(get(), 3);
	}
	
	@SuppressWarnings("unused")
	public static void testDriver() {
		final int p1 = problem1();
		final int p2 = problem2();
		final int p4 = problem5(get(), true);
		final int p6 = problem6();
		final int p7 = problem7();
		final int p8 = problem8();
		final int p9 = problem9(true);
		final int p10 = doThing();
		final int p11 = inconsistentAdd();
		final int p12 = consistentAdd();
		final int p13 = problem10(true);
		final int p14 = returnFlowTest();
		final int p5 = testCast();
	}
	
	public static void heapTestDriver() {
		
	}
	
	public static int rar(final int a) {
		return a + get();
	}
	
	public static int whatever(final int a, final int b) {
		return a + b;
	}
	
	public static int blar(final int d, final int c) {
		int b;
		if(d > 0) {
			b = d;
		} else {
			b = c;
		}
		return b + d;
	}
	
	public static int recursionTest2(final int a, final int b) {
		if(a == b) {
			return a + b;
		} else {
			return recursionTest2(a,b);
		}
	}
	
	public static int doIt(final Whatever x, final int d, final int e) {
		x.f = d + e;
		return d + 1;
	}
	
	public static void recursionTest(final Whatever x, final int a, final int b) {
		if(a == b) {
			x.f = a + b;
		} else {
			recursionTest(a, b);
		}
	}
	
	public static class ArrayWrapper {
		int[] a = new int[3];
	}
	
	private static boolean nondetBoolean() {
		return System.currentTimeMillis() == 0;
	}
	
	private static int disjointLoop() {
		int a = 0, b = 0, c = 0, d = 0, e = 0, f = 0;
		while(nondetBoolean()) {
			if(nondetBoolean()) {
				b = a;
			} else if(nondetBoolean()) {
				b = f;
			}
			a = get();
			f = e;
			e = d;
			d = c;
			c = get();
		}
		return b;
	}
	
	public static void useless(final int a) { }
	
	public static int returnFlowTest() {
		int a = 0, b = 0;
		while(nondetBoolean()) {
			b = a;
			useless(b);
			a = get();
		}
		return a + b;
	}
	
	public static class Object1 {
		public void propagate(final int s) { }
	}
	
	public static class Object2 {
		public Object1 f = new Object1();
		public int g;
	}
	
	public static interface Dispatch { 
		void dispatch(Object2 o);
	}
	
	public static short testCast() {
		return (short)get();
	}
	
	public static int recursion2(final int a, final int b) {
		if(nondetBoolean()) {
			return a + b;
		} else {
			return recursion2(b, get());
		}
	}
	
	public static void whatever(final Object2 g) {
		g.g = get();
	}
	
	public static void main(final String[] args) { }
}
