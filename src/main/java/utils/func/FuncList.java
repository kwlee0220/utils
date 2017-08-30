package utils.func;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.collect.Lists;

import io.vavr.Tuple2;


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
	public abstract FuncList<T> drop(int n);
	public abstract FuncList<T> dropWhile(Function<T, Boolean> cond);
	public abstract FuncList<T> reverse();
	public abstract <U> U foldLeft(U identity, BiFunction<U,T,U> fold);
	public abstract <U> Tuple2<U,FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U,T,U> fold);
	
	private FuncList() { }
	
	@SuppressWarnings("unchecked")
	public static <T> FuncList<T> list() {
		return NIL;
	}
	
	public FuncList<T> cons(T head) {
		return new Cons<>(head, this);
	}
	
	public <U> U foldLeft(U identity, Function<U, Function<T, U>> fold) {
		return foldLeft(identity, (a,t) -> fold.apply(a).apply(t));
	}

	public <U> U foldRight(U identity, BiFunction<T, U, U> fold) {
		return reverse().foldLeft(identity, (a,t)->fold.apply(t, a));
	}

	public <U> U foldRight(U identity, Function<T, Function<U, U>> fold) {
		return foldRight(identity, (t,u) -> fold.apply(t).apply(u));
	}

	public Optional<T> reduce(BiFunction<T, T, T> fold) {
		return isEmpty()
				? Optional.empty()
				: Optional.of(foldLeft(head(), fold));
	}
	
	public static <T,S> FuncList<T> unfold(S start, Function<S,Result<Tuple2<T,S>>> gen) {
		return unfold_(list(), start, gen).evaluate().reverse();
	}
	private static <T,S> TailCall<FuncList<T>> unfold_(FuncList<T> accum, S cursor,
														Function<S,Result<Tuple2<T,S>>> gen) {
		return gen.apply(cursor)
					.map(t -> TailCall.suspend(() -> unfold_(accum.cons(t._1), t._2, gen)))
					.getOrElse(TailCall.returns(accum));
	}

	public FuncList<T> filter(Function<T, Boolean> pred) {
		return foldRight(list(), (t,a) -> pred.apply(t) ? new Cons<>(t, a) : a);
	}

	public <U> FuncList<U> map(Function<T, U> mapper) {
		return foldRight(list(), (t,a) -> new Cons<>(mapper.apply(t), a));
	}

	public <U> FuncList<U> flatMap(Function<T, FuncList<U>> mapper) {
		return foldRight(list(), (t,a) -> concat(mapper.apply(t), a));
	}
	
	public boolean exists(Function<T,Boolean> cond) {
		return foldLeft(false, true, (b,t) -> cond.apply(t))._1;
	}
	
	public boolean forAll(Function<T,Boolean> cond) {
		return foldLeft(true, false, (b,t) -> cond.apply(t))._1;
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
	
	public static <T> FuncList<T> concat(FuncList<T> list1, FuncList<T> list2) {
		return list1.foldRight(list2, (t,accum)->new Cons<>(t, accum));
	}
	
	public static <T> FuncList<T> flatten(FuncList<FuncList<T>> list) {
		return list.foldLeft(list(), (a,l) -> concat(a, l));
	}

	public static <U,V,T> FuncList<T> zipWith(FuncList<U> list1, FuncList<V> list2,
												Function<U, Function<V,T>> zip) {
		return zipWith(list1, list2, (u,v) -> zip.apply(u).apply(v));
	}
	
	public static <U,V,T> FuncList<T> zipWith(FuncList<U> list1, FuncList<V> list2,
												BiFunction<U,V,T> zip) {
		return zipWith_(list(), list1, list2, zip).evaluate().reverse();
	}
	private static <U,V,T> TailCall<FuncList<T>> zipWith_(FuncList<T> accum,
															FuncList<U> list1,
															FuncList<V> list2,
															BiFunction<U,V,T> zip) {
		return list1.isEmpty() || list2.isEmpty()
			? TailCall.returns(accum)
			: TailCall.suspend(() -> zipWith_(accum.cons(zip.apply(list1.head(), list2.head())),
												list1.tail(), list2.tail(), zip));
	}
	
	public static <U,V> Tuple2<FuncList<U>,FuncList<V>> unzip(FuncList<Tuple2<U,V>> list) {
		return list.foldRight(new Tuple2<>(list(), list()),
							(t,a) -> new Tuple2<>(a._1.cons(t._1), a._2.cons(t._2)));
	}
	public <U,V> Tuple2<FuncList<U>,FuncList<V>> unzip(Function<T,Tuple2<U,V>> tear) {
		return foldRight(new Tuple2<>(list(), list()),
						(t,a) -> {
							Tuple2<U,V> p = tear.apply(t);
							return new Tuple2<>(a._1.cons(p._1), a._2.cons(p._2));
						});
	}
	
	public static <C extends Comparable<C>> Optional<C> max(FuncList<C> list) {
		return list.reduce((m,t) -> (m.compareTo(t) >= 0) ? m : t);
	}
	
	public static <C extends Comparable<C>> Optional<C> min(FuncList<C> list) {
		return list.reduce((m,t) -> (m.compareTo(t) <= 0) ? m : t);
	}
	
	public Result<T> get(int index) {
		return index < 0 || index >= length()
				? Result.failure(new IndexOutOfBoundsException(""+index))
				: get_(this, index).evaluate();
	}
	private static <T> TailCall<Result<T>> get_(FuncList<T> list, int index) {
		return index == 0
				? TailCall.returns(Result.of(list.head()))
				: TailCall.suspend(() -> get_(list.tail(), index-1));
	}
	
	public Result<Tuple2<FuncList<T>, FuncList<T>>> splitAt(int index) {
		return index < 0 || index >= length()
				? Result.failure(new IndexOutOfBoundsException(""+index))
				: splitAt_(list(), reverse(), length() - index).evaluate();
	}
	private static <T> TailCall<Result<Tuple2<FuncList<T>, FuncList<T>>>>
							splitAt_(FuncList<T> accum, FuncList<T> list, int index) {
		return index == 0 || list.isEmpty()
				? TailCall.returns(Result.of(new Tuple2<>(list.reverse(), accum)))
				: TailCall.suspend(() -> splitAt_(accum.cons(list.head()), list.tail(),
													index-1));
	}
	
	public boolean startsWith(FuncList<T> sub) {
		return startsWith_(this, sub).evaluate();
	}
	private static <T> TailCall<Boolean> startsWith_(FuncList<T> list, FuncList<T> sub) {
		return sub.isEmpty()
				? TailCall.returns(true)
				: list.isEmpty()
					? TailCall.returns(false)
					: list.head().equals(sub.head())
						? TailCall.suspend(()-> startsWith_(list.tail(), sub.tail()))
						: TailCall.returns(false);
	}
	
	public boolean hasSubList(FuncList<T> sub) {
		return hasSubList_(this, sub).evaluate();
	}
	public static <T> TailCall<Boolean> hasSubList_(FuncList<T> list, FuncList<T> sub) {
		return list.isEmpty()
				? TailCall.returns(sub.isEmpty())
				: list.startsWith(sub)
					? TailCall.returns(true)
					: TailCall.suspend(()-> hasSubList_(list.tail(), sub));
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
		public FuncList<T> drop(int n) {
			return this;
		}

		@Override
		public FuncList<T> dropWhile(Function<T,Boolean> cond) {
			return this;
		}

		@Override
		public FuncList<T> reverse() {
			return this;
		}

		@Override
		public <U> U foldLeft(U identity, BiFunction<U,T,U> fold) {
			return identity;
		}

		@Override
		public <U> Tuple2<U, FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U, T, U> fold) {
			return new Tuple2<>(identity, this);
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
		public FuncList<T> drop(int n) {
			return drop_(this, n).evaluate();
		}
		private static <T> TailCall<FuncList<T>> drop_(FuncList<T> list, int n) {
			return (n <= 0 || list.isEmpty())
					? TailCall.returns(list)
					: TailCall.suspend(()-> drop_(list.tail(), n-1));
		}

		@Override
		public FuncList<T> dropWhile(Function<T, Boolean> cond) {
			return dropWhile_(this, cond).evaluate();
		}
		private static <T> TailCall<FuncList<T>> dropWhile_(FuncList<T> list,
															Function<T, Boolean> cond) {
			return (list.isEmpty() || !cond.apply(list.head()))
					? TailCall.returns(list)
					: TailCall.suspend(()-> dropWhile_(list.tail(), cond));
		}

		@Override
		public FuncList<T> reverse() {
			return reverse(list(), this).evaluate();
		}
		private static <T> TailCall<FuncList<T>> reverse(FuncList<T> acc, FuncList<T> list) {
			return list.isEmpty()
					? TailCall.returns(acc)
					: TailCall.suspend(() -> reverse(new Cons<>(list.head(), acc), list.tail()));
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
		public <U> Tuple2<U,FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U,T,U> fold) {
			return foldLeft_(identity, stopper, this, fold);
		}
		private static <T,U> Tuple2<U,FuncList<T>> foldLeft_(U acc, U stopper, FuncList<T> list,
															BiFunction<U,T,U> fold) {
			return list.isEmpty() || acc.equals(stopper)
					? new Tuple2<>(acc, list)
					: foldLeft_(fold.apply(acc, list.head()), stopper, list.tail(), fold);
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
