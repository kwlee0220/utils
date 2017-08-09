package utils.func;

import java.util.List;
import java.util.function.BiFunction;

import com.google.common.collect.Lists;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class FuncList<T> {
	@SuppressWarnings("rawtypes")
	public static final FuncList NIL = new Nil();
	
	public abstract T head();
	public abstract FuncList<T> tail();
	public abstract boolean isEmpty();
	public abstract int length();
	public abstract <U> U foldLeft(U identity, BiFunction<U,T,U> fold);
	public abstract <U> U foldLeft(U identity, Function<U, Function<T, U>> fold);
	
	private FuncList() { }
	
	@SuppressWarnings("unchecked")
	public static <T> FuncList<T> list() {
		return NIL;
	}
	
	@SafeVarargs
	public static <T> FuncList<T> list(T... elms) {
		FuncList<T> cons = list();
		for ( int i = elms.length-1; i >= 0; --i ) {
			cons = new Cons<>(elms[i], cons);
		}
		
		return cons;
	}
	
	public static <T> FuncList<T> list(List<T> elms) {
		FuncList<T> cons = list();
		for ( int i = elms.size()-1; i >= 0; --i ) {
			cons = new Cons<>(elms.get(i), cons);
		}
		
		return cons;
	}
	
	public Failable<T> getAt(int index) {
		if ( isEmpty() ) {
			return Failable.failure(new IllegalArgumentException());
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		Cons<T> cons = (Cons)this;
		if ( index == 0 ) {
			return Failable.success(cons.m_head);
		}
		
		return cons.m_tail.getAt(index-1);
	}
	
	public List<T> toList() {
		List<T> list = Lists.newArrayList();
		fill(list);
		
		return list;
	}
	
	public void fill(List<T> list) {
		FuncList<T> current = this;
		while ( current instanceof Cons ) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Cons<T> cons = (Cons)current;
			list.add(cons.m_head);
			current = cons.m_tail;
		}
	}
	
	private static class Nil<T> extends FuncList<T> {
		private Nil() { }

		@Override
		public T head() {
			throw new IllegalStateException("empty list");
		}

		@Override
		public FuncList<T> tail() {
			throw new IllegalStateException("empty list");
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public int length() {
			return 0;
		}

		@Override
		public <U> U foldLeft(U identity, Function<U, Function<T, U>> fold) {
			return identity;
		}

		@Override
		public <U> U foldLeft(U identity, BiFunction<U,T,U> fold) {
			return identity;
		}
		
		@Override
		public String toString() {
			return "[]";
		}
	}
	
	private static class Cons<T> extends FuncList<T> {
		private final T m_head;
		private final FuncList<T> m_tail;
		private final int m_length;
		
		private Cons(T head, FuncList<T> tail) {
			m_head = head;
			m_tail = tail;
			m_length = tail.length() + 1;
		}

		@Override
		public T head() {
			return m_head;
		}

		@Override
		public FuncList<T> tail() {
			return m_tail;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public int length() {
			return m_length;
		}

		@Override
		public <U> U foldLeft(U identity, Function<U, Function<T, U>> fold) {
			return foldLeft(identity, this, fold).evaluate();
		}
		private <U> TailCall<U> foldLeft(U acc, FuncList<T> list,
										Function<U, Function<T, U>> fold) {
			return list.isEmpty()
					? TailCall.returns(acc)
					: TailCall.suspend(() -> foldLeft(fold.apply(acc).apply(list.head()),
														list.tail(), fold));
		}

		@Override
		public <U> U foldLeft(U identity, BiFunction<U,T,U> fold) {
			return foldLeft(identity, this, fold).evaluate();
		}
		private <U> TailCall<U> foldLeft(U acc, FuncList<T> list, BiFunction<U,T,U> fold) {
			return list.isEmpty()
					? TailCall.returns(acc)
					: TailCall.suspend(() -> foldLeft(fold.apply(acc, list.head()),
														list.tail(), fold));
		}
		
		@Override
		public String toString() {
			StringBuilder builder = foldLeft(new StringBuilder(),
											(b,d)-> b.append(""+d).append(','));
			builder.setLength(builder.length()-1);
			return "[" + builder.toString() + "]";
		}
	}
}
