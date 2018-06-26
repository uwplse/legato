

public class HeapTest {
	private static class Whatever {
		 public static int staticF;
		 int f;
	}
	
	private static class ContainerWrapper {
		Object d;
	}

	private static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	public static int get() {
		return 3;
	}

	// FIELD TESTS
	
	public static int addField(final Whatever x, final int a, final int b) {
		x.f = a + b;
		return x.f;
	}
	
	public static int inconsistentFieldAdd() {
		return addField(new Whatever(), get(), get());
	}
	
	public static int consistentFieldAdd() {
		return addField(new Whatever(), get(), 0);
	}
	
	public static int consistentRead() {
		nondetBool();
		final Whatever c1 = new Whatever();
		Whatever c2;
		if(c1.hashCode() == 3) {
			c2 = c1;
		} else {
			c2 = null;
		}
		c1.f = get();
		return c1.f + c2.f;
	}
	
	public static int testNarrowing() {
		final Whatever w = new Whatever();
		w.f = get();
		final int a = w.f;
		final int b = sillyId(w);
		return b + a;
	}
	
	public static int testNarrowing3() {
		final Whatever w = new Whatever();
		w.f = get();
		testNarrowingInterp(w);
		return w.f;
	}
	

	private static void testNarrowingInterp(final Whatever w) {
		get();
		narrow(w);
	}

	private static void narrow(final Whatever w) {
		@SuppressWarnings("unused")
		final int j = get();
	}

	public static int testNarrowing2() {
		final Whatever w = new Whatever();
		w.f = get();
		final int b = sillyId(w);
		return b + w.f;
	}

	private static int sillyId(final Whatever w) {
		get();
		return w.f;
	}
	
	private static void sillyIdWrapper(final Whatever w) {
		sillyId(w);
	}
	
	public static int testTransitiveNarrowing() {
		final Whatever w = new Whatever();
		w.f = get();
		sillyIdWrapper(w);
		return w.f;
	}
	
	private static void setField(final Whatever w) {
		w.f = get();
	}
	
	public static int testNarrowingFailure() {
		final Whatever w = new Whatever();
		w.f = get();
		if(nondetBool()) {
			setField(w);
		}
		return w.f;
	}
	
	// STATIC FIELD TESTS

	private static int addField(final int i, final int j) {
		Whatever.staticF = i + j;
		return Whatever.staticF;
	}
	
	@SuppressWarnings("unused")
	public static int inconsistentStaticFieldAdd() {
		return addField(get(), get());
	}
	
	public static int consistentStaticFieldAdd() {
		return addField(get(), 0);
	}
	
	public static int inconsistentStaticRead() {
		Whatever.staticF = get();
		return Whatever.staticF + Whatever.staticF;
	}
	
	// ARRAY TESTS
	
	public static int basicArrayTest() {
		final int[] a = new int[4];
		a[1] = get();
		a[2] = get();
		return a[3];
	}
	
	public static int arrayReadTest() {
		final int[] a = new int[4];
		a[1] = get();
		return a[2] + a[3];
	}
	
	public static Object testArrayUpcast() {
		final ContainerWrapper w = new ContainerWrapper();
		final int[] a = new int[3];
		a[1] = get();
		w.d = a;
		
		final ContainerWrapper c = new ContainerWrapper();
		final Object e = c.d;
		return e;
	}
	
	public static int testArrayUnwrap() {
		final ContainerWrapper w = new ContainerWrapper();
		final int[] a = new int[3];
		a[1] = get();
		w.d = a;
		
		if(nondetBool()) {
			return (Integer)w.d;
		} else {
			return ((int[])w.d)[3];
		}
	}
	
	public static int readArray(final int[] b) {
		return b[0];
	}
	
	public static int testArrayInterprocedural() {
		final int a[] = new int[]{get()};
		return readArray(a);
	}
	
	public static int testArrayReturn() {
		final int[] b = getArray();
		return b[0];
	}
	
	public static int testDoubleUnwrap() {
		final int[][] r = new int[][]{
				{get()}
		};
		return r[0][0];
	}
	
	private static int[] getArray() {
		return new int[]{get()};
	}
	
	private static void overwriteParameter(Whatever w, final int d) {
		if(nondetBool()) {
			w = new Whatever();
		}
		w.f = d;
	}
	
	public static int noStrongUpdateInCallee() {
		final Whatever w = new Whatever();
		final int a = get();
		w.f = get();
		overwriteParameter(w, a);
		return a + w.f;
	}
	
	private static void noOverwriteParameter(final Whatever w, final int d) {
		w.f = d;
	}
	
	private static void overwriteParameterAfterWrite(Whatever w, final int d) {
		w.f = d;
		w = new Whatever();
		w.f = 3;
	}
	
	public static int strongUpdateInCallee() {
		final Whatever w = new Whatever();
		final int a = get();
		w.f = get();
		noOverwriteParameter(w, a);
		return a + w.f;
	}

	public static int strongUpdateInCalleeWithWrite() {
		final Whatever w = new Whatever();
		final int a = get();
		w.f = get();
		overwriteParameterAfterWrite(w, a);
		return a + w.f;
	}
	
	public static class WhateverContainer {
		Whatever wrapped = new Whatever();
		WhateverContainer() {
			wrapped.f = get();
		}
	}
	
	public static int testAliasSearchInCaller() {
		return new WhateverContainer().wrapped.f;
	}
	
	public static class Foo {
		protected int d;
		public void doThing() { }
	}
	public static class Bar extends Foo {
		@Override
		public void doThing() { 
			this.d = get();
		}
	}
	
	public static int testChainNarrowing() {
		return needFinalNarrowMethod(get());
	}
	
	public static int inconsistentVirtualDispatch() {
		Foo obj;
		if(nondetBool()) {
			obj = new Bar();
		} else {
			obj = new Foo();
		}
		obj.d = get();
		obj.doThing();
		return obj.d;
	}
	

	public static int needFinalNarrowMethod(final int b) {
		int a;
		if(b == 4) {
			// force adding a prime in this branch
			dummyFunction();
			a = id1(b);
		} else {
			a = id2(b);
		}
		return a;
	}
	
	private static int id1(final int b) {
		if(b == 4) {
			return b;
		} else {
			// force adding label on return
			get();
			return 0;
		}
	}

	private static int id2(final int b) {
		if(b == 3) {
			return b;
		} else {
			// force adding label on return
			get();
			return 0;
		}
	}
	
	public static int testChainNarrowFailure() {
		int a;
		if(nondetBool()) {
			a = id1(get());
			a = id2(a);
			a = id1(a);
			a = id2(a);
		} else {
			a = id1(get());
			a = id2(a);
			a = id1(a);
			a = id2(a);
		}
		return a;
	}

	public static void dummyFunction() { 
		get();
	}
	
	public static void main(final String[] args) { }
}
