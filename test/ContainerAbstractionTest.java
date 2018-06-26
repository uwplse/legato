import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;


public class ContainerAbstractionTest {
	private static boolean nondetBool() {
		return System.currentTimeMillis() == 0;
	}
	
	public static int get() {
		return 3;
	}
	
	public static class Container {
		public int f;
	}
	
	public static int testMultiAdd() {
		final List<Integer> i = new ArrayList<>();
		final int a = get();
		while(nondetBool()) {
			i.add(a);
		}
		return i.get(0);
	}
	
	public static int testToArray() {
		final ArrayList<Integer> i = new ArrayList<>();
		i.add(get());
		final Integer[] arr = i.toArray(new Integer[0]);
		return arr[0];
	}
	
	public static int testAddAll() {
		final HashMap<Integer, String> foo = new HashMap<>();
		foo.put(get(), "asdf");
		
		final HashMap<Integer, String> bar = new HashMap<>(foo);
		return bar.keySet().iterator().next() + get();
	}
	
	public static int testReplace() {
		final HashMap<Integer, String> foo = new HashMap<>();
		final int a = get();
		foo.put(a, "asdf");
		final String ret = foo.put(a, "foo");
		return Integer.parseInt(ret) + get();
	}
	
	public static int testReplaceCons() {
		final HashMap<Integer, String> foo = new HashMap<>();
		final int a = get();
		foo.put(a, "asdf");
		final String ret = foo.put(a, "foo");
		return Integer.parseInt(ret) + a;
	}
	
	public static int testPutAliasSearch() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		a.add(g);
		a.get(0).f = get();
		return g.f;
	}
	
	public static int addAllAliasSearch() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		final HashMap<String, Container> m = new HashMap<>();
		m.put("", g);
		a.addAll(m.values());
		a.get(0).f = get();
		return m.get("").f + a.get(0).f;
	}
	
	public static int containerWeakUpdate() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		a.add(g);
		a.get(0).f = get();
		a.get(0).f = get();
		return a.get(0).f;
	}
	
	public static int arrayTransferTest() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		a.add(g);
		a.toArray(new Container[0])[0].f = get();
		return g.f;
	}
	
	public static int testIteratorUpdate() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		a.listIterator().add(g);
		g.f = get();
		return a.get(0).f;
	}
	
	public static int testTransferAliases() {
		final Container g = new Container();
		final ArrayList<Container> a = new ArrayList<>();
		ListIterator<Container> it;
		if(nondetBool()) {
			it = a.listIterator();
		} else {
			it = null;
		}
		g.f = get();
		a.listIterator().add(g);
		return it.next().f;
	}
	
	public static void main(final String[] args) { }
}
