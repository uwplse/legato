
public class PropagationTest {
	public static class TestClass {
		int b;
		int f;
		public TestClass fluent(final int a) {
			b += a;
			return this;
		}
		public int returnValue() {
			return 0;
		}
		public TestClass fluent(final TestClass other) {
			b += other.b;
			return this;
		}
		
		public void receiver(final TestClass other, final int a) {
			b += other.b + a;
		}
		
		public void receiver(final int a) {
			b += a;
		}
		
		public void receiver(final TestClass other) {
			b += other.b;
		}
		
		public static int returnValue(final TestClass arg) {
			return 0;
		}
		
		public TestClass notFluent(final int a) {
			return this;
		}
		
		public static String format(final String msg, final Object... params) {
			return "";
		}
		
		public void addAll(final Object... params) { }
		
		public static void process(final int a, final TestClass out) { }
	}
	
	public static int get() {
		return 3;
	}
	
	public static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	public static int testBasicFluentPropagation() {
		final int a = get();
		final TestClass cls = new TestClass();
		while(nondetBool()) {
			cls.fluent(a);
		}
		return cls.returnValue();
	}
	
	public static int testPropagationNoField() {
		final int a = get();
		final TestClass cls = new TestClass();
		cls.f = a;
		final TestClass other = new TestClass();
		other.fluent(cls);
		other.receiver(cls);
		final int d = TestClass.returnValue(cls);
		final int e = cls.returnValue();
		return other.returnValue() + d + e + cls.returnValue();
	}
	
	public static int testFluentReturnFlow() {
		return (new TestClass()).fluent(get()).fluent(get()).fluent(get()).returnValue();
	}
	
	public static int testFluentReturnFlowConsistent() {
		final int a = get();
		return (new TestClass()).fluent(a).fluent(a).fluent(a).returnValue();
	} 
	
	public static int testNestedPropagationInconsistent() {
		final TestClass cls = new TestClass();
		nestedPropagation(cls);
		cls.receiver(get());
		return cls.returnValue();
	}
	
	public static int testNestedPropagationConsistent() {
		final TestClass cls = new TestClass();
		nestedPropagation(cls);
		return cls.returnValue();
	}
	
	public static int testOverwriteFluent() {
		TestClass cls = new TestClass();
		cls.f = get();
		cls = cls.fluent(get());
		return cls.f + cls.returnValue();
	}
	
	private static void nestedPropagation(final TestClass cls) {
		cls.receiver(get());
	}
	
	public static int testReturnGraphIncons() {
		final int a = get();
		final String b = String.valueOf(a);
		final String[] c = b.split(",");
		final int d = Integer.parseInt(c[0]);
		return d + get();
	}
	
	public static int testReturnGraphCons() {
		final int a = get();
		final String b = String.valueOf(a);
		final String[] c = b.split(",");
		final int d = Integer.parseInt(c[0]);
		return d + a;
	}
	
	public static int testSubfieldPropagationIncons() {
		final String s = TestClass.format("foo", get());
		return Integer.parseInt(s) + get();
	}
	
	public static int testSubfieldPropagationCons() {
		final int g = get();
		final String s = TestClass.format("foo", g);
		return Integer.parseInt(s) + g;
	}
	
	public static int testOutParamPropagationIncons() {
		final TestClass cls = new TestClass();
		TestClass.process(get(), cls);
		return cls.returnValue() + get();
	}
	
	public static int testOutParamPropagationCons() {
		final TestClass cls = new TestClass();
		final int a = get();
		TestClass.process(a, cls);
		return cls.returnValue() + a;
	}
	
	public static int testPropagationOverwrite() {
		String[] arg = new String[]{String.valueOf(get())};
		System.out.println(arg);
		arg = String.valueOf(get()).split(",");
		return Integer.parseInt(arg[0]);
	}
	
	public static int testNopPropagation() {
		final TestClass cls = new TestClass().fluent(get());
		TestClass.process(0, cls);
		return cls.returnValue();
	}
	
	public static int testSubfieldPropagationFlow() {
		final TestClass cls = new TestClass();
		TestClass o;
		if(nondetBool()) {
			o = null;
		} else {
			o = cls;
		}
		doSubfieldPropagation(o, get());
		return cls.returnValue() + get();
	}

	private static void doSubfieldPropagation(final TestClass o, final Integer i) {
		o.addAll(i);
	}
	
	public static void main(final String[] args) { testSubfieldPropagationFlow(); }
}
