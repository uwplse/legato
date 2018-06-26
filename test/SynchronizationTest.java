
public class SynchronizationTest {
	public static int get() {
		return 3;
	}
	
	public static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	private static class Wrapper {
		int f;
		public int g;
		public volatile int a; 
	}
	
	public final static Object MUTEX = new Object();
	
	public static int testSimpleSynchronization() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 5;
		}
		w.f = get();
		int b;
		final int c = w.f;
		synchronized(MUTEX) {
			b = w.f;
		}
		return b + c;
	}
	
	public static int testSameReadSync() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int b;
		synchronized(MUTEX) {
			b = w.f;
		}
		return b + w.f;
	}
	
	public static int testSynchNarrowing() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int b, c;
		synchronized(MUTEX) {
			b = w.f;
			c = w.f;
		}
		return b + w.f + c;
	}
	
	public static int readValue(final Wrapper w) {
		return w.f;
	}
	
	public static int testInterproceduralSynch() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = get();
		}
		final int b = w.f;
		int c;
		synchronized(MUTEX) {
			c = readValue(w);
		}
		return b + w.f + c;
	}
	
	public static int testSyncPriming() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int a = 0;
		int b = 0;
		while(nondetBool()) {
			b = a;
			synchronized(MUTEX) {
				a = w.f;
			}
		}
		return a + b;
	}
	
	public static int testNestedSync() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
			w.g = 5;
		}
		w.f = get();
		w.g = get();
		int a,b;
		synchronized(MUTEX) {
			a = w.f;
			synchronized(MUTEX){
				b = w.g;
			}
		}
		return a + b;
	}
	
	public static int testInterproceduralNestedSync() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int a, b;
		synchronized(MUTEX) {
			a = w.f;
			b = nestedSyncRead(w);
		}
		return a + b;
	}
	
	public static int testInterproceduralNopSync() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		final int a, b;
		synchronized(MUTEX) {
			a = w.f;
			nestedOtherSyncRead(w);
			b = w.f;
		}
		return w.f + a + b;
	}
	
	private static int nestedOtherSyncRead(final Wrapper w) {
		synchronized(MUTEX) {
			return w.g;
		}
	}

	private static int nestedSyncRead(final Wrapper w) {
		synchronized(MUTEX) {
			return w.f;
		}
	}
	
	private synchronized static int syncRead(final Wrapper w) {  
		return w.f;
	}
	
	public static int testSynchronizedMethod() { 
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		final int a = syncRead(w);
		return a + w.f;
	}
	
	public static int testSynchronizedMethodPriming() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int a = 0, b = 0;
		while(nondetBool()) {
			b = a;
			a = syncRead(w);
		}
		return a + b;
	}
	
	public synchronized static int read(final Wrapper w) { 
		return nestedSyncRead(w) + w.f;
	}
	
	public static int testNestedSynchronizedMethod() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		return read(w);
	}
	
	public static int testSimpleVolatileRead() {
		final Wrapper w = new Wrapper();
		w.a = get();
		return w.a;
	}
	
	public static int testUniqueVolatileReads() {
		final Wrapper w = new Wrapper();
		w.a = get();
		return w.a + w.a;
	}
	
	public static int testNoMutexSync() {
		final Wrapper w = new Wrapper();
		w.a = get();
		int a, b;
		synchronized(MUTEX) {
			a = w.a;
			b = w.a;
		}
		return a + b;
	}
	
	public static int testVolatilePriming() {
		final Wrapper w = new Wrapper();
		w.a = get();
		int a = 0, b = 0;
		while(nondetBool()) {
			b = a;
			a = w.a;
		}
		return a + b;
	}

	public static int testVolatilePriming2() {
		final Wrapper w = new Wrapper();
		w.a = get();
		int a = 0, b = 0;
		while(nondetBool()) {
			b = a;
			a = w.a;
		}
		return b;
	}
	
	public static int testNestedSyncWait() {
		synchronized(MUTEX) {
			synchronized(MUTEX) {
				try {
					MUTEX.wait();
				} catch (final InterruptedException e) { }
			}
		}
		return get();
	}
	
	public static int testRecursiveSync() {
		return recursiveSyncExecute(get());
	}

	private static int recursiveSyncExecute(final int a) {
		synchronized(MUTEX) {
			if(nondetBool()) {
				recursiveSyncExecute(a);
			} else {
				try {
					MUTEX.wait();
				} catch (final InterruptedException e) { }
			}
		}
		return 0;
	}
	
	public static int testSimpleWaitHavoc() throws InterruptedException {
		final Wrapper w = new Wrapper();
		w.f = get();
		int a;
		final int b;
		synchronized(MUTEX) {
			a = w.f;
			MUTEX.wait();
			b = w.f;
		}
		return a + b;
	}
	
	public static int testInterProcNarrowing() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int b;
		synchronized(MUTEX) {
			b = w.f;
			b += id(w).f;
		}
		return b;
	}
	
	public static int testInterProcNarrowingDD() {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int b;
		Dispatch d;
		if(nondetBool()) {
			d = new Dispatch1();
		} else {
			d = new Dispatch2();
		}
		synchronized(MUTEX) {
			b = w.f;
			b += d.id(w).f;
		}
		return b;
	}

	
	private static Wrapper id(final Wrapper w) {
		return w;
	}
	
	public static abstract class Dispatch { public abstract Wrapper id(Wrapper f); }

	public static class Dispatch1 extends Dispatch {
		@Override
		public Wrapper id(final Wrapper f) { return f; }
	}
	
	public static class Dispatch2 extends Dispatch {
		@Override
		public Wrapper id(final Wrapper f) { return f; }
	}
	
	

	public static int testWaitHavocPrev() throws InterruptedException {
		final Wrapper w = new Wrapper();
		synchronized(MUTEX) {
			w.f = 4;
		}
		w.f = get();
		int a;
		final int b;
		synchronized(MUTEX) {
			a = w.f;
			b = w.f;
			MUTEX.wait();
		}
		return a + b;
	}
	
	public static void main(final String[] args) { }
}
