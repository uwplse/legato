
public class InheritanceTest {
	public static int get() {
		return 3;
	}
	
	public static abstract class Inheritance {
		public abstract int execute();
	}
	public static class Implementation1 extends Inheritance {
		@Override
		public int execute() {
			return get();
		}
	}
	public static class Implementation2 extends Inheritance {
		@Override
		public int execute() {
			return get();
		}
	}
	public static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	public static int testInvokeVirtual() {
		Inheritance i;
		if(nondetBool()) {
			i = new Implementation1();
		} else {
			i = new Implementation2();
		}
		return i.execute();
	}
	
	public static class Field1 extends Inheritance {
		public int f;
		@Override
		public int execute() {
			return f;
		}
	}
	
	public static class Field2 extends Inheritance {
		public int g;
		@Override
		public int execute() {
			return this.g;
		}
	}
	
	public static int testDynamicDispatchLoop() {
		final int a = get();
		final int b = get();
		int c = 0;
		int d = 0;
		while(nondetBool()) {
			Inheritance o;
			if(nondetBool()) {
				o = new Field1();
				((Field1)o).f = a;
			} else {
				o = new Field2();
				((Field2)o).g = b;
			}
			d = c;
			c = o.execute();
		}
		return d + c;
	}
	
	public static int testDynamicNarrowing() {
		final int a = get();
		Inheritance o;
		if(nondetBool()) {
			o = new Field1();
			((Field1)o).f = a;
		} else {
			o = new Field2();
			((Field2)o).g = a;
		}
		final int e = o.execute();
		return a + e;
	}
	
	public static int testTransitiveDynamicDispatchLoop() {
		final int a = get();
		final int b = get();
		int c = 0;
		int d = 0;
		while(nondetBool()) {
			Inheritance o;
			if(nondetBool()) {
				o = new Field1();
				((Field1)o).f = a;
			} else {
				o = new Field2();
				((Field2)o).g = b;
			}
			d = c;
			c = transitiveExecute(o);
		}
		return d + c;
	}
	
	private static int transitiveExecute(final Inheritance i) {
		return i.execute();
	}
	
	public static int testTransitiveDynamicNarrowing() {
		final int a = get();
		Inheritance o = null;
		if(nondetBool()) {
			o = new Field1();
			((Field1)o).f = a;
		} else {
			o = new Field2();
			((Field2)o).g = a;
		}
		final int e = transitiveExecute(o);
		return a + e;
	}
	
	public static int transitiveNarrow(final Inheritance o) {
		final Field1 o2 = new Field1();
		o2.f = o.execute();
		final int b = o2.f;
		final int a = o2.execute();
		return a + b;
	}
	
	public static int testTransitiveNarrowing() {
		final int a = get();
		final int b = get();
		Inheritance o;
		if(nondetBool()) {
			o = new Field1();
			((Field1)o).f = a;
		} else {
			o = new Field2();
			((Field2)o).g = b;
		}
		return transitiveNarrow(o);
	}
	
	public static void main(final String[] args) { }
}
