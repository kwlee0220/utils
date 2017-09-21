package utils.stream;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import io.vavr.CheckedConsumer;
import io.vavr.Function2;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import utils.func.FLists;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Stream<T> {
	public Option<T> next();
	
	@SuppressWarnings("unchecked")
	public static <T> Stream<T> empty() {
		return Streams.EMPTY;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Stream<T> of(T... values) {
		return of(Arrays.asList(values));
	}
	
	public static <T> Stream<T> of(Iterable<T> values) {
		final Iterator<T> iter = values.iterator();
		return () -> iter.hasNext() ? Option.some(iter.next()) : Option.none();
	}
	
	public static <S,T> Stream<T> unfold(S init, Function<S,Option<Tuple2<T,S>>> generator) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(generator != null, "generator is null");
		
		return new Streams.UnfoldStream<T, S>(init, generator);
	}
	
	public static <T> Stream<T> generate(T init, Function<T,T> inc) {
		Preconditions.checkArgument(init != null, "init is null");
		Preconditions.checkArgument(inc != null, "inc is null");
		
		return new Streams.GeneratedStream<>(init, inc);
	}
	
	public static Stream<Integer> range(int start, int end) {
		return new Streams.RangedStream(start, end, false);
	}
	public static Stream<Integer> rangeClosed(int start, int end) {
		return new Streams.RangedStream(start, end, true);
	}
	
	public default Stream<T> take(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.TakenStream<>(this, count);
	}
	public default Stream<T> drop(long count) {
		Preconditions.checkArgument(count >= 0, "count < 0");
		return new Streams.DroppedStream<>(this, count);
	}

	public default Stream<T> takeWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.TakeWhileStream<>(this, pred);
	}
	public default Stream<T> dropWhile(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return new Streams.DropWhileStream<>(this, pred);
	}
	
	public default Stream<T> filter(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		Predicate<T> negated = pred.negate();
		return () -> {
			Option<T> next;
			while ( (next = next()).filter(negated).isDefined() );
			return next;
		};
	}
	
	public default <V> Stream<V> map(Function<T,V> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return () -> next().map(mapper);
	}
	
	public default <V> Stream<V> flatMap(Function<T,Stream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(mapper).foldLeft(empty(), (a,s) -> concat(a,s));
	}
	
	public default boolean exists(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return foldLeft(false, true, (a,t) -> { 
			try {
				return pred.test(t);
			}
			catch ( Exception e ) {
				return false;
			}
		});
	}
	public default boolean forAll(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		return foldLeft(true, false, (a,t) -> { 
			try {
				return pred.test(t);
			}
			catch ( Throwable e ) {
				return false;
			}
		});
	}
	
	public default <S> S foldLeft(S accum, Function2<S,T,S> folder) {
		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(folder != null, "folder is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			accum = folder.apply(accum, next.get());
		}
		
		return accum;
	}
	
	public default <S> S foldLeft(S accum, S stopper, BiFunction<S,T,S> folder) {
		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(folder != null, "folder is null");
		
		if ( accum.equals(stopper) ) {
			return accum;
		}
		
		Option<T> next = next();
		while ( (next = next()).isDefined() ) {
			accum = folder.apply(accum, next.get());
			if ( accum.equals(stopper) ) {
				return accum;
			}
		}
		
		return accum;
	}
	
	public default <S> S foldRight(S accum, BiFunction<T,S,S> folder) {
		return FLists.foldRight(toList(), accum, folder);
	}
	
	public default T reduce(BiFunction<T,T,T> reducer) {
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		Option<T> next = next();
		if ( next.isDefined() ) {
			return foldLeft(next.get(), (a,t) -> reducer.apply(a, t));
		}
		else {
			throw new IllegalStateException("Stream is empty");
		}
	}
	
	public default Option<T> find(Predicate<T> pred) {
		Preconditions.checkArgument(pred != null, "pred is null");
		
		Option<T> next;
		Predicate<T> negated = pred.negate();
		while ( (next = next()).filter(negated).isDefined() );
		
		return next;
	}
	
	public static <T> Stream<T> concat(Stream<T> head, Stream<T> tail) {
		Preconditions.checkArgument(head != null, "head is null");
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return new Streams.AppendedStream<>(head, tail);
	}
	
	public default <U> Stream<Tuple2<T,U>> zip(Stream<U> other) {
		return () -> {
			Option<T> next1 = this.next();
			Option<U> next2 = other.next();
			
			return ( next1.isDefined() && next2.isDefined() )
					? Option.some(new Tuple2<>(next1.get(), next2.get()))
					: Option.none();
		};
	}
	
	public default List<T> toList() {
		return foldLeft(Lists.newArrayList(), (l,t) -> { l.add(t); return l; });
	}
	
	public default void forEach(Consumer<T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			effect.accept(next.get());
		}
	}
	
	public default void forEachIE(CheckedConsumer<T> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		Option<T> next;
		while ( (next = next()).isDefined() ) {
			try {
				effect.accept(next.get());
			}
			catch ( Throwable ignored ) { }
		}
	}
	
	public default <K> Grouped<K,T> groupBy(Function<T,K> keyer) {
		return foldLeft(new Grouped<>(), (g,t) -> g.add(keyer.apply(t), t));
	}
	
	public default Stream<T> sorted(Comparator<? super T> cmp) {
		List<T> list = toList();
		list.sort(cmp);
		return of(list);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default Stream<T> sorted() {
		return sorted((t1,t2) -> ((Comparable)t1).compareTo(t2));
	}
	
	public default String join(String delim, String begin, String end) {
		return foldLeft(new StringBuilder(begin), (b,t) -> b.append(t.toString()).append(delim))
					.append(end)
					.toString();
	}
	
	public default String join(String delim) {
		return join(delim, "", "");
	}

	public default boolean startsWith(Stream<T> subList) {
		Preconditions.checkArgument(subList != null, "subList is null");
		
		Option<T> subNext = subList.next();
		Option<T> next = next();
		while ( subNext.isDefined() && next.isDefined() ) {
			if ( !subNext.get().equals(next.get()) ) {
				return false;
			}
			
			subNext = subList.next();
			next = next();
		}
		
		return (next.isDefined() && subNext.isEmpty());
	}
}
