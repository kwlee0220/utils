package utils.stream;

import static utils.Utilities.checkArgument;
import static utils.Utilities.checkNotNullArgument;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.CSV;
import utils.ComparableKeyValue;
import utils.Indexed;
import utils.KeyValue;
import utils.Suppliable;
import utils.Tuple;
import utils.Tuple3;
import utils.Utilities;
import utils.func.CheckedConsumer;
import utils.func.CheckedConsumerX;
import utils.func.CheckedFunction;
import utils.func.CheckedFunctionX;
import utils.func.FLists;
import utils.func.FOption;
import utils.func.Slice;
import utils.func.Try;
import utils.io.IOUtils;
import utils.stream.FStreams.AbstractFStream;
import utils.stream.FStreams.FlatMapDataSupplier;
import utils.stream.FStreams.FlatMapTry;
import utils.stream.FStreams.FoldLeftLeakFStream;
import utils.stream.FStreams.GeneratedStream;
import utils.stream.FStreams.MapOrThrowStream;
import utils.stream.FStreams.MapToBooleanStream;
import utils.stream.FStreams.MapToDoubleStream;
import utils.stream.FStreams.MapToFloatStream;
import utils.stream.FStreams.MapToIntStream;
import utils.stream.FStreams.MapToLongStream;
import utils.stream.FStreams.MappedStream;
import utils.stream.FStreams.PeekedStream;
import utils.stream.FStreams.ScannedStream;
import utils.stream.FStreams.SelectiveMapStream;
import utils.stream.FStreams.SingleSourceStream;
import utils.stream.FStreams.SplitFStream;
import utils.stream.FStreams.UnfoldStream;
import utils.stream.FStreams.UniqueFStream;
import utils.stream.FStreams.UniqueKeyFStream;
import utils.stream.IntFStream.RangedStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends Iterable<T>, AutoCloseable {
	/**
	 * 스트림에 포함된 다음 데이터를 반환한다.
	 * <p>
	 * 더 이상의 데이터가 없는 경우에는 {@link FOption#empty()}을 반환함.
	 * 이미 close된 경우에는 {@link IllegalStateException} 예외를 발생시킨다.
	 * 
	 * @return	스트림 데이터. 없는 경우는 {@link FOption#empty()}.
	 */
	public FOption<T> next();
	
	/**
	 * 스트림을 닫는다.
	 * <p>
	 * 스트림을 위해 할당된 모든 자원을 반환한다.
	 * 수행 중 오류가 발생하면 해당 정보를 {@link Try}를 통해 반환한다.
	 * 
	 * @return	수행 오류 객체.
	 */
	public default Try<Void> closeQuietly() {
		return Try.run(this::close);
	}
	
	/**
	 * 데이터가 없는 빈 스트림 객체를 반환한다.
	 * 
	 * @param <T> 스트림 원소 객체 타입
	 * @return	Empty 스트림 객체.
	 */
	@SuppressWarnings("unchecked")
	public static <T> FStream<T> empty() {
		return FStreams.EMPTY;
	}
	
	/**
	 * 주어진 데이터 객체 배열 값을 차례대로 반환하는 FStream 객체를 생성한다.
	 * <p>
	 * 생성된 FStream이 반환되는 값들의 순서는 주어진 배열 원소들의 순서와 동일하다.
	 * 
	 * @param <T> 배열 원소 데이터 타입
	 * @param values	스트림에 포함될 원소 데이터 배열.
	 * @return FStream 객체
	 */
	@SafeVarargs
	public static <T> FStream<T> of(T... values) {
		Preconditions.checkArgument(values != null, "null values");
		
		return from(Arrays.asList(values));
	}
	
	/**
	 * {@link Iterator} 객체로부터 FStream 객체를 생성한다.
	 * <p>
	 * 만일 입력 Iterator 객체가 {@link Closeable} 또는 {@link AutoCloseable}인 경우에는
	 * iteration이 완료된 후 {@link Closeable#close()} {@link AutoCloseable#close()}를 호출한다.
	 * 
	 * @param <T> Iterator에서 반환하는 원소 데이터 타입
	 * @param iter	입력 순환자 객체.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(final Iterator<? extends T> iter) {
		Preconditions.checkArgument(iter != null);
		
		return new AbstractFStream<T>() {
			@Override
			protected void closeInGuard() throws Exception {
				// 구현에 따라 iter가 {@link Closeable}일 수도 있어서
				// 그런 경우 {@link Closeable#close}를 호출하도록 한다.
				IOUtils.closeQuietly(iter);
			}

			@Override
			protected FOption<T> nextInGuard() {
				return iter.hasNext() ? FOption.of(iter.next()) : FOption.empty();
			}
		};
	}
	
	/**
	 * 주어진 {@link Iterable}객체로부터 FStream 객체를 생성한다.
	 * <p>
	 * 주어진 Iterable 객체로부터 생성 {@link Iterator}가
	 * {@link Closeable} 또는 {@link AutoCloseable}인 경우에는
	 * iteration이 완료된 후 {@link Closeable#close()} {@link AutoCloseable#close()}를 호출한다.
	 * 
	 * @param <T> Iterable에서 반환하는 데이터 타입
	 * @param values	입력 {@link Iterable} 객체.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(final Iterable<? extends T> values) {
		Preconditions.checkArgument(values != null);
		
		return new AbstractFStream<T>() {
			private Iterator<? extends T> m_iter = values.iterator();

			@Override protected void closeInGuard() throws Exception { }

			@Override
			protected FOption<T> nextInGuard() {
				if ( m_iter.hasNext() ) {
					return FOption.of(m_iter.next());
				}
				else {
					return FOption.empty();
				}
			}
		};
	}
	
	/**
	 * 주어진 {@link Stream}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Stream에서 반환하는 데이터 타입
	 * @param stream	입력 {@link Stream} 객체.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Stream<? extends T> stream) {
		Preconditions.checkArgument(stream != null, "Stream is null");
		
		return from(stream.iterator());
	}

	/**
	 * 주어진 값을 무한 반복하여 반환하는 무한 스트림 객체를 반환한다.
	 * 
	 * @param <T>		스트림이 반환하는 데이터의 타입
	 * @param value		스트림이 반복하여 반환할 데이터 값.
	 * @return	스트림 객체.
	 */
	public static <T> FStream<T> repeat(T value) {
		Preconditions.checkArgument(value != null, "repeat value");
		
		return generate(value, (v) -> v);
	}
	/**
	 * 주어진 값을 주어진 횟수만큼 반복하여 반환하는 무한 스트림 객체를 반환한다.
	 * 
	 * @param <T>		스트림이 반환하는 데이터의 타입
	 * @param value		스트림이 반복하여 반환할 데이터 값.
	 * @param count		반복 횟수.
	 * @return	스트림 객체.
	 */
	public static <T> FStream<T> repeat(T value, int count) {
		Preconditions.checkArgument(value != null, "repeat value");
		Preconditions.checkArgument(count >= 0, "count should be larger or equal to 1");
		
		return generate(value, (v) -> v).take(count);
	}
	
	/**
	 * 주어진 정수 값 ({@code start})부터 시작해서 1씩 증가하여 주어진 정수 값 {@code end} -1 까지의 
	 * 값을 반환하는 {@link IntFStream}을 반환한다.
	 * <p>
	 * 스트림에는 {@code start}는 포함하지만 {@code end}는 포함하지 않는다.
	 * 
	 * @param start		시작 값.
	 * @param end		종료 바로 다음 값.
	 * @return	스트림 객체.
	 */
	public static IntFStream range(int start, int end) {
		return new RangedStream(start, end);
	}
	
	/**
	 * 무한 스트림 객체를 생성한다.
	 * <p>
	 * 스트림에서 발행하는 첫번째 값은 인자로 전달된 {@code init}이고, 이 후부터는
	 * 바로 전에 발행된 값을 {@code inc}에 적용한 값을 반환한다.
	 * 
	 * @param <T>	생성된 스트림 데이터의 타입
	 * @param init	스트림이 발생하는 첫번재 초기값
	 * @param inc	마지막으로 발행한 값을 통해 다음으로 발행할 데이터를 생성할 {@link Function}
	 * @return FStream 객체
	 */
	public static <T> FStream<T> generate(T init, Function<? super T,? extends T> inc) {
		Preconditions.checkArgument(init != null, "initial value is null");
		Preconditions.checkArgument(inc != null, "next value generator is null");
		
		return new GeneratedStream<>(init, inc);
	}
	
	/**
	 * 데이터 생성 모듈 {@code generator}가 생성하는 데이터로 구성된 스트림 객체를 생성한다.
	 * <p>
	 * 데이터 생성은 별도의 쓰레드를 통해 수행되고, 생성된 데이터는 채널을 통해 전달된다.
	 * 
	 * @param <T>			생성된 스트림 데이터의 타입.
	 * @param generator		데이터 생성 모듈.
	 * 						데이터 생성 모듈은 데이터를 생성하여 {@link Suppliable}를 통해 제공한다.
	 * @param bufLength		데이터 생성 모듈이 생성하는 데이터를 받을 채널의 내부 버퍼 크기.
	 * 						데이터 생성 모듈이 생성한 데이터로 인해 내부 버퍼가 full된 경우
	 * 						생성 모듈 쓰레드는 버퍼가 여유 공간이 발생할 때까지 대기한다.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> generate(Generator<T> generator, int bufLength) {
		Preconditions.checkArgument(generator != null, "generator is null");
		Preconditions.checkArgument(bufLength > 0, "bufLength > 0: but=" + bufLength);
		
		return new GeneratorBasedFStream<>(generator, bufLength);
	}
	
	/**
	 * 주어진 상태 변수와 데이터 생성 함수를 활용하여 생성된 데이터로 구성된
	 * 무한 스트림 객체를 생성한다.
	 * <p>
	 * 데이터 생성 함수는 상태 변수를 인자로 받아서, 스트림 데이터와 변경된 상태 값을
	 * 반환하여야 한다. 함수의 반환 값이 {@code null}인 경우는 더 이상으로 생성할 데이터가
	 * 없다는 것을 의미한다.
	 * 
	 * @param <S>	상태 객체 타입
	 * @param <T>	데이터 생성 함수에 의해 생성되는 데이터의 타입
	 * @param initialState	상태 객체 초기 값
	 * @param gen	데이터 생성 함수
	 * @return FStream 객체
	 */
	public static <S,T> FStream<T> unfold(S initialState,
											Function<? super S, Tuple<? extends S,? extends T>> gen) {
		Preconditions.checkArgument(initialState != null, "value generator is null");
		Preconditions.checkArgument(gen != null, "next value generator is null");
		
		return new UnfoldStream<>(initialState, gen);
	}
	
	/**
	 * 주어진 길이의 버퍼를 사용하는 {@link SuppliableFStream} 객체를 생성한다.
	 * 
	 * @param <T>	생성된 스트림 데이터의 타입
	 * @param length	생성될 {@link SuppliableFStream}가 내부적으로 사용할 버퍼 길이.
	 * @return SuppliableFStream 객체
	 */
	public static <T> SuppliableFStream<T> pipe(int length) {
		Preconditions.checkArgument(length > 0, "length > 0: but=" + length);
		
		return new SuppliableFStream<>(length);
	}
	
	/**
	 * 무한 길이의 버퍼를 사용하는 {@link SuppliableFStream} 객체를 생성한다.
	 * 
	 * @param <T>	생성된 스트림 데이터의 타입
	 * @return SuppliableFStream 객체
	 */
	public static <T> SuppliableFStream<T> pipe() {
		return new SuppliableFStream<>();
	}
	
	
	
	/**
	 * 본 스트림에서 주어진 조건을 만족하는 데이터로만 구성된 스트림을 생성한다.
	 * 
	 * @param pred	조건 객체
	 * @return FStream 객체
	 */
	public default FStream<T> filter(final Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			@Override
			protected FOption<T> getNext(FStream<T> src) {
				for ( FOption<T> next = src.next(); next.isPresent(); next = src.next() ) {
					if ( pred.test(next.getUnchecked()) ) {
						return next;
					}
				}
				
				return FOption.empty();
			}
		};
	}
	
	/**
	 * 본 스트림에서 주어진 조건을 만족하지 않는 데이터로만 구성된 스트림을 생성한다.
	 * 
	 * @param pred	조건 객체
	 * @return FStream 객체
	 */
	public default FStream<T> filterNot(final Predicate<? super T> pred) {
		return filter(pred.negate());
	}
	
	/**
	 * 매핑 함수 {@code mapper}를 활용하여 본 스트림에 포함된 각 데이터를 변형한 데이터로
	 * 구성된 스트림을 생성한다.
	 * 
	 * @param <S>	매핑된 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체
	 */
	public default <S> FStream<S> map(Function<? super T,? extends S> mapper) {
		return new MappedStream<>(this, mapper);
	}

	/**
	 * 본 스트림에 포함된 각 데이터에서 변화된 데이터로 구성된 스트림을 생성한다.
	 * <p>
	 * {@code mapper} 수행 중 오류가 발생되면 해당 데이터는 무시하고, 다음 데이터를 처리한다.
	 * 
	 * @param <S>	매핑된 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체
	 */
	public default <S> FStream<S> mapOrIgnore(CheckedFunction<? super T,? extends S> mapper) {
		return new SingleSourceStream<T,S>(this) {
			@Override
			protected FOption<S> getNext(FStream<T> src) {
				FOption<T> next;
				while ( (next = src.next()).isPresent() ) {
					try {
						return FOption.of(mapper.apply(next.getUnchecked()));
					}
					catch ( Throwable ignored ) { }
				}
				return FOption.empty();
			}
		};
	}
	
	/**
	 * 매핑 함수 {@code mapper}를 활용하여 본 스트림에 포함된 각 데이터를 변형한 결과로
	 * 구성된 스트림을 생성한다.
	 * 
	 * @param <S>		매핑된 데이터의 타입.
	 * @param mapper	매핑 함수.
	 * @return		FStream 객체.
	 */
	public default <S> FStream<Try<S>> tryMap(CheckedFunction<? super T,? extends S> mapper) {
		return new SingleSourceStream<T,Try<S>>(this) {
			@Override
			public FOption<Try<S>> getNext(FStream<T> src) {
				FOption<T> onext;
				while ( (onext = src.next()).isPresent() ) {
					T next = onext.getUnchecked();
					try {
						return FOption.of(Try.success(mapper.apply(next)));
					}
					catch ( Throwable e ) {
						return FOption.of(Try.failure(e));
					}
				}
				
				return FOption.empty();
			}
		};
//		return new TryMapStream<>(this, mapper);
	}
	
	/**
	 * 본 스트림에 포함된 각 데이터에 주어진 함수({@code mapper})를 적용한 결과 데이터로
	 * 구성된 스트림을 생성한다.
	 * <p>
	 * 함수 수행 중 오류가 발생되면 바로 예외를 발생시킨다.
	 * 
	 * @param <S>		매핑된 데이터의 타입
	 * @param <X>		발생될 수 있는 오류 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체
	 */
	public default <S,X extends Throwable>
	FStream<S> mapOrThrow(CheckedFunctionX<? super T,? extends S,X> mapper) throws X {
		return new MapOrThrowStream<>(this, mapper);
	}
	
	/**
	 * 스트림에 속한 각 데이터에 대해 {@code pred}를 적용한 결과에 따라 선택적으로
	 * mapper을 적용한 결과로 구성된 출력 스트림을 생성한다.
	 * <p>
	 * {@code pred}를 적용한 결과 false인 경우에는 mapper 적용 없이 원래 데이터가
	 * 다음 단계로 전달된다.
	 *
	 * @param pred	{@code mapper} 적용 여부를 결정할 predicate
	 * @param mapper	적용할 mapper.
	 * @return	{@code pred}을 적용하여 {@code true}인 element에 대해서만 {@code mapper}을
	 * 			적용한 결과 스트림.
	 */
	public default FStream<T> mapSelectively(Predicate<? super T> pred,
												Function<? super T,? extends T> mapper) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return new SelectiveMapStream<>(this, pred, mapper);
	}

	/**
	 * 스트림에 포함된 데이터들을 주어진 매핑 함수 {@code mapper}에 병렬로 적용하여
	 * 출력된 결과로 구성된 스트림을 생성한다.
	 * <p>
	 * 스트림 데이터에 대해 매핑 함수를 병렬로 적용하기 때문에 매핑의 수행 시간이 다른 경우
	 * 출력 스트림에서의 결과 값 순서는 바뀔 수 있다.
	 * 만일 입력 데이터 순서와 출력 데이터 순서를 맞추려면
	 * {@link FStream#mapAsync(Function, AsyncExecutionOptions)} 함수를 사용하되, 입력 인자로
	 * {@link AsyncExecutionOptions#setKeepOrder(boolean)}를 활용하여 입력 데이터 순서와
	 * 출력 데이터 순서를 맞춘다.
	 * <p>
	 * 또한 입력 데이터에 대한 매칭 함수 적용은 각각 별도의 쓰레드에 의해 수행되고 동시 실행되는
	 * 쓰레드의 수는 시스템 내부 default 값을 사용한다. 만일 쓰레드 수를 변경하려면
	 * {@link FStream#mapAsync(Function, AsyncExecutionOptions)} 함수를 사용한다.
	 * 
	 * @param <S>		매핑 결과 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체
	 * @see FStream#mapAsync(Function, AsyncExecution)
	 */
	public default <S> FStream<Try<S>> mapAsync(Function<? super T, ? extends S> mapper) {
		return mapAsync(mapper, AsyncExecutionOptions.create());
	}

	/**
	 * 스트림에 포함된 데이터들을 주어진 매핑 함수 {@code mapper}에 병렬로 적용하여
	 * 출력된 결과로 구성된 스트림을 생성한다.
	 * <p>
	 * 구체적인 매핑 함수 적용 방법은 두번째 인자인 {@link AsyncExecutionOptions}에 의해 다음과
	 * 같이 결정된다.
	 * <dl>
	 * 	<dt>{@link AsyncExecutionOptions#getKeepOrder()}</dt>
	 * 	<dd>입력 데이터 사이 순서와 해당 출력 데이터 사이 순서를 맞출지 여부. {@code true}인 경우는
	 * 		각 쓰레드에 의해 수행되는 매핑 함수 수행 시간과 무관하게 출력 스트림의 결과 데이터는
	 * 		해당 입력 스트림내 데이터 순서와 동일하게 된다.
	 * 	</dd>
	 * 	<dt>{@link AsyncExecutionOptions#getWorkerCount()}</dt>
	 * 	<dd> 매핑 함수 수행을 위해 사용되는 최대 쓰레드 수.</dd>
	 * 	<dt>{@link AsyncExecutionOptions#getExecutor()}</dt>
	 * 	<dd>쓰레드 할당에 사용될 쓰레드 풀을 관리할 {@link Executor} 객체.</dd>
	 * </dl>
	 * 
	 * @param <S>		매핑 결과 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @param options	매핑 적용 옵션
	 * @return FStream 객체
	 */
	public default <S> FStream<Try<S>> mapAsync(Function<? super T,? extends S> mapper,
													AsyncExecutionOptions options) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		Preconditions.checkArgument(options != null, "AsyncExecutionOptions is null");
		
		if ( options.getKeepOrder() ) {
			return new MapOrderedAsyncStream<>(this, mapper, options);
		}
		else {
			return new MapUnorderedAsyncStream<>(this, mapper, options);
		}
	}	
	/**
	 * 본 스트림에 포함된 각 원소에 대해 주어진 매핑 함수 {@code mapper}를 적용하여 얻은 결과
	 * {@link FStream}들을 flattening하여 구성된 스트림을 생성한다. 
	 * 
	 * @param <V>	결과 스트림 원소 데이터 타입.
	 * @param mapper	매핑 함수.
	 * @return	결과 스트림.
	 */
	public default <V> FStream<V> flatMap(Function<? super T,? extends FStream<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return concat(map(mapper));
	}
	
	/**
	 * <code>map(mapper).flatMap(it -> FStream.from(it))</code>
	 */
	public default <V> FStream<V> flatMapIterable(Function<? super T, ? extends Iterable<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		return flatMap(mapper.andThen(FStream::from));
	}

	/**
	 * <code>map(mapper).flatMap(arr -> FStream.of(arr))</code>
	 */
	public default <V> FStream<V> flatMapArray(Function<? super T,V[]> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		return flatMap(mapper.andThen(FStream::of));
	}
	
	/**
	 * 본 스트림에 포함된 각 원소에 대해 주어진 매핑 함수 {@code mapper}를 적용하여
	 * 얻은 결과 객체들 중에서 null인 아닌 객체들로만 구성된 스트림을 생성한다.
	 * 
	 * @param <V>	결과 스트림 원소 데이터 타입.
	 * @param mapper	매핑 함수.
	 * @return	결과 스트림.
	 */
	public default <V> FStream<V> flatMapNullable(Function<? super T,? extends V> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		FStream<V> tmp = map(mapper);
		return tmp.filter(v -> v != null);
	}
	
	/**
	 *  {@code mapper}에 따라 본 스트림을 변환시킨다. 동작 방식은 filter와 map을 통합된
	 *  방식으로 동작한다.
	 *  {@code mapper}에 각 데이터를 적용할 때 {@link FOption#empty()}인 경우 filter-out되고,
	 *  그렇지 않은 경우는 적용 결과에 {@link FOption#get()}를 적용한 값으로 변환시킨다.
	 *
	 * @param <R>		맵 적용 결과 타입.
	 * @param mapper	스트림의 각 데이터에 적용할 맵퍼 객체
	 * @return	맵퍼가 적용된 스트림 객체
	 */
	public default <R> FStream<R> flatMapFOption(Function<? super T,? extends FOption<R>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return new SingleSourceStream<T,R>(this) {
			@Override
			public FOption<R> getNext(FStream<T> src) {
				FOption<T> next;
				while ( (next = src.next()).isPresent() ) {
					FOption<R> mapped = mapper.apply(next.getUnchecked());
					if ( mapped.isPresent() ) {
						return mapped;
					}
				}
				
				return FOption.empty();
			}
		};
	}

	/**
	 * 본 스트림에 포함된 각 원소에 대해 주어진 매핑 함수 {@code mapper}를 적용하여
	 * 얻은 결과 {@link Try} 객체들 중에서 {@link Try#isSuccessful()} 인 객체들로만
	 * 구성된 스트림을 생성한다.
	 * 
	 * @param <V>	결과 스트림 원소 데이터 타입.
	 * @param mapper	매핑 함수.
	 * @return	결과 스트림.
	 */
	public default <V> FStream<V> flatMapTry(Function<? super T,Try<V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return new FlatMapTry<>(this, mapper);
	}

	/**
	 * 본 스트림에 포함된 각 원소에 대해 주어진 매핑 함수 {@code mapper}를 병렬로 적용한  얻은 결과
	 * {@link FStream}들을 flattening하여 구성된 스트림을 생성한다.
	 * <p>
	 * 구체적인 매핑 함수 적용 방법은 두번째 인자인 {@link AsyncExecutionOptions}에 의해 다음과
	 * 같이 결정된다.
	 * <dl>
	 * 	<dt>{@link AsyncExecutionOptions#getKeepOrder()}</dt>
	 * 	<dd>입력 데이터에 매핑 함수를 병렬적으로 적용하여 생성된 {@link FStream}에서 생성된 데이터가
	 * 		섞이게 할지 말지를 결정한다. {@code true}인 경우에는 결과 FStream에 포함된 데이터가
	 * 		서로 섞이지 않고, 입력 데이터 순서을 유지하게 되고, {@code false}인 경우에는 거의 동시에
	 * 		생성된 여러 결과 FStream에서 생성된 데이터들이 서로 섞일 수 있게 된다.</dd> 
	 * 	<dt>{@link AsyncExecutionOptions#getWorkerCount()}</dt>
	 * 	<dd> 매핑 함수 수행을 위해 사용되는 최대 쓰레드 수.</dd>
	 * 	<dt>{@link AsyncExecutionOptions#getExecutor()}</dt>
	 * 	<dd>쓰레드 할당에 사용될 쓰레드 풀을 관리할 {@link Executor} 객체.</dd>
	 * </dl>
	 * 
	 * @param <V>		결과 스트림 원소 데이터 타입.
	 * @param mapper	매핑 함수.
	 * @param options	매핑 적용 옵션
	 * @return	결과 스트림.
	 */
	public default <V> FStream<V>
	flatMapAsync(Function<? super T, ? extends FStream<V>> mapper, AsyncExecutionOptions options) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		Preconditions.checkArgument(options != null, "options is null");
		
		if ( options.getKeepOrder() ) {
			return mapAsync(mapper, options)
					.flatMapTry(tried -> tried)
					.flatMap(strm -> strm);
		}
		else {
			FStream<FStream<V>> strmOfStreams = this.map(t -> new FlatMapDataSupplier<T,V>(t, mapper));
			return FStream.mergeParallel(strmOfStreams, options.getWorkerCount(), options.getExecutor());
		}
	}
	
	/**
	 * 스트림의 첫 count개의 데이터로 구성된 FStream 객체를 생성한다.
	 * <p>
	 * 만일 입력 스트림의 데이터 수가 {@code count}보다 작다면 해당 갯수의
	 * 스트림이 반환된다.
	 * 
	 * @param count	데이터 갯수.
	 * @return	최대 'count' 개의  데이터로 구성된 스트림 객체.
	 */
	public default FStream<T> take(long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0, but {}", count); 
		
		return new SingleSourceStream<T,T>(this) {
			private long m_remains = count;
			
			@Override
			public FOption<T> getNext(FStream<T> src) {
				if ( m_remains <= 0 ) {
					return FOption.empty();
				}
				else {
					--m_remains;
					return src.next();
				}
			}
		};
	}
	
	/**
	 * 스트림의 첫 count개의 데이터가 제거된 FStream 객체를 생성한다.
	 * <p>
	 * 만일 입력 스트림의 데이터 수가 {@code count}보다 작다면 빈 스트림이 반환된다.
	 * 
	 * @param count	데이터 갯수.
	 * @return	최대 'count' 개의  데이터로 구성된 스트림 객체.
	 */
	public default FStream<T> drop(final long count) {
		Preconditions.checkArgument(count >= 0, "count >= 0, but {}", count); 
		
		return new SingleSourceStream<T,T>(this) {
			private boolean m_dropped = false;
			
			@Override
			public FOption<T> getNext(FStream<T> src) {
				if ( !m_dropped ) {
					m_dropped = true;
					for ( int i =0; i < count; ++i ) {
						if ( src.next().isAbsent() ) {
							return FOption.empty();
						}
					}
				}
				
				return src.next();
			}
		};
	}
	
	/**
	 * 스트림의 마지막 count개의 데이터가 제거된 FStream 객체를 생성한다.
	 * <p>
	 * 만일 입력 스트림의 데이터 수가 {@code count}보다 작다면 빈 스트림이 반환된다.
	 * 
	 * @param count	데이터 갯수.
	 * @return	스트림 객체.
	 */
	public default FStream<T> dropLast(final int count) {
		Preconditions.checkArgument(count >= 0, "count >= 0, but {}", count); 
		
		return new SingleSourceStream<T,T>(this) {
			private List<T> m_tail = new ArrayList<T>(count+1);
			private boolean m_filled = false;
			
			@Override
			public FOption<T> getNext(FStream<T> src) {
				if ( !m_filled ) {
					m_filled = true;
					
					for ( int i =0; i < count; ++i ) {
						FOption<T> next = src.next();
						if ( next.isAbsent() ) {
							return FOption.empty();
						}
						
						m_tail.add(next.getUnchecked());
					}
				}

				FOption<T> next = src.next();
				if ( next.isAbsent() ) {
					return FOption.empty();
				}
				
				m_tail.add(next.getUnchecked());
				return FOption.of(m_tail.remove(0));
			}
		};
	}
	
	/**
	 * 스트림에서 주어진 조건식({@code pred})을 만족하지 않은 데이터가 출현하기 직전까지의
	 * 데이터를 반환하는 스트림을 생성한다. 조건을 만족하지 않게된 데이터 이후의
	 * 모든 데이터는 무시된다.
	 * 
	 * @param pred	조건식 객체
	 * @return	스트림 객체.
	 */
	public default FStream<T> takeWhile(final Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			@Override
			public FOption<T> getNext(FStream<T> src) {
				return src.next()
							.filter(pred)
							.ifAbsent(this::markEndOfStream);
			}
		};
	}
	
	/**
	 * 스트림에서 주어진 조건식({@code pred})을 만족하지 않은 데이터가 출현할 때부터의
	 * 데이터를 반환하는 스트림을 생성한다. 이때 조건을 만족하지 않게된 데이터부터
	 * 결과 스트림에 포함된다.
	 * 
	 * @param pred	조건식 객체
	 * @return	스트림 객체.
	 */
	public default FStream<T> dropWhile(final Predicate<? super T> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		
		return new SingleSourceStream<T,T>(this) {
			private boolean m_started = false;

			@Override
			public FOption<T> getNext(FStream<T> src) {
				if ( !m_started ) {
					m_started = true;
		
					FOption<T> next;
					while ( (next = src.next()).test(pred) );
					return next;
				}
				else {
					return src.next();
				}
			}
		};
	}

	public default FStream<Indexed<T>> zipWithIndex(int start) {
		return zipWith(FStream.generate(start, v -> v+1), Indexed::with);
	}
	public default FStream<Indexed<T>> zipWithIndex() {
		return zipWithIndex(0);
	}
	
	public static <T,S> FStream<Tuple<T, S>> zip(Iterable<T> first, Iterable<S> second, boolean longest) {
		return FStream.from(first).zipWith(FStream.from(second), longest);
	}
	public static <T,S> FStream<Tuple<T, S>> zip(Iterable<T> first, Iterable<S> second) {
		return FStream.zip(first, second, false);
	}

	public default <S> FStream<Tuple<T,S>> zipWith(FStream<S> other) {
		return zipWith(other, false);
	}
	public default <S,Z> FStream<Z> zipWith(FStream<S> other, BiFunction<T,S,Z> zipper) {
		return zipWith(other, zipper, false);
	}
	
	public default <S> FStream<Tuple<T,S>> zipWith(FStream<S> other, boolean longest) {
		Preconditions.checkArgument(other != null, "zip FStream is null");
		
		return zipWith(other, (t,s) -> Tuple.of(t,s), longest);
	}
	public default <S,Z> FStream<Z> zipWith(FStream<S> other, BiFunction<T,S,Z> zipper,
														boolean longest) {
		Preconditions.checkArgument(other != null);
		Preconditions.checkArgument(zipper != null);
		
		return new ZippedFStream<>(this, other, zipper, longest);
	}
	
	public default FStream<T> slice(Slice slice) {
		Preconditions.checkArgument(slice != null, "Slice was null");
		
		FStream<Indexed<T>> stream0 = this.zipWithIndex();
		if ( slice.start() != null && slice.start() > 0 ) {
			stream0 = stream0.drop(slice.start());
		}
		if ( slice.end() == null && slice.step() == null ) {
			return stream0.map(t -> t.value());
		}
		
		FStream<Tuple3<T,Integer,Integer>> stream = stream0.zipWithIndex()
															.map(t -> Tuple.of(t.value().value(),
																				t.value().index(),
																				t.index()));
		if ( slice.end() != null ) {
			stream = stream.takeWhile(t -> t._2 < slice.end());
		}
		if ( slice.step() != null ) {
			stream = stream.filter(t -> t._3 % slice.step() == 0);
		}
		
		return stream.map(t -> t._1);
	}
	
	/**
	 * 인자로 주어진 배열에 포함된 iterable들을 차례대로 순환하는 스트림을 반환한다.
	 * 
	 * @param <T>			스트림의 원소 타입
	 * @param iterables		스트림을 구성할 iterable들의 배열.
	 * @return	{@code FStream} 객체.
	 */
	@SafeVarargs
	public static <T> FStream<T> concat(Iterable<? extends T>... iterables) {
		Preconditions.checkArgument(iterables != null, "source iterables");
		return FStream.of(iterables)
						.flatMap(iter -> FStream.from(iter));
	}

	@SafeVarargs
	public static <T> FStream<T> concat(FStream<T>... streams) {
		Preconditions.checkArgument(streams != null, "source streams");
		return concat(FStream.of(streams));
	}
	
	/**
	 * 원소 스트림에 포함된 모든 데이터들을 하나의 스트림으로 묶은 스트림 객체를 생성한다.
	 * 
	 * @param <T>	스트림의 원소 타입
	 * @param fact	스트림의 스트림 객체.
	 * @return	{@code FStream} 객체.
	 */
	public static <T> FStream<T> concat(FStream<FStream<T>> fact) {
		Preconditions.checkArgument(fact != null, "source FStream factory");
		
		return new ConcatedStream<>(fact);
	}
	
	/**
	 * 현 스트림에 주어진 스트림을 연결하여 하나의 스트림 객체를 생성한다.
	 * 
	 * @param follower	본 스트림에 뒤에 붙일 스트림 객체.
	 * @return	{@code FStream} 객체.
	 */
	public default FStream<T> concatWith(FStream<T> follower) {
		Preconditions.checkArgument(follower != null, "follower is null");
		
		return concat(FStream.of(this, follower));
	}
	
	/**
	 * 현 스트림에 주어진 원소를 추가하여 하나의 스트림 객체를 생성한다.
	 * 
	 * @param tail	본 스트림에 뒤에 붙일 원소 객체.
	 * @return	{@code FStream} 객체.
	 */
	public default FStream<T> concatWith(T tail) {
		Preconditions.checkArgument(tail != null, "tail is null");
		
		return concatWith(FStream.of(tail));
	}
	
	/**
	 * {@code inputStreamFact}가 반환하는 {@link FStream<T>}들에서 생성하는 데이터들이 합쳐진
	 * 데이터를 구성된 스트림을 반환한다.
	 * <p>
	 * {@code inputStreamFact}가 반환하는 {@link FStream<T>}마다 별도의 쓰레드가 할당되어
	 * 병렬적으로 자신이 맡은 FStream에서 데이터 얻어 출력 스트림으로 전송하기 때문에,
	 * 출력 스트림에는 여러 입력 스트림에서 생성된 데이터가 섞일 수 있다.
	 * <p>
	 * 이때 수행되는 쓰레드의 수는 인자로 주어진 {@code workerCount}에 의해 결정된다.
	 * 
	 * @param <T>	스트림의 원소 타입
	 * @param inputStreamFact	입력 스트림의 스트림 객체.
	 * @param workerCount	스트림 merge 작업을 수행하는 쓰레드의 갯수.
	 * @param executor		쓰레드 풀 객체.
	 * 						{@code null}인 경우는 별도의 쓰레드 풀을 사용하지 않는 것을 의미한다.
	 * @return	{@code FStream} 객체.
	 */
	public static <T> FStream<T> mergeParallel(FStream<? extends FStream<? extends T>> inputStreamFact,
												int workerCount, @Nullable Executor executor) {
		return new MergeParallelFStream<>(inputStreamFact, workerCount, executor);
	}
	
	public default FStream<T> reduceLeak(BiFunction<? super T,? super T,? extends T> combiner) {
		Preconditions.checkArgument(combiner != null, "combiner is null");
		
		return new ScannedStream<>(this, combiner);
	}
	
	public default T reduce(BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		FOption<T> initial = next();
		if ( initial.isAbsent() ) {
			throw new IllegalStateException("FStream is empty");
		}
		
		return fold(initial.get(), (a,t) -> reducer.apply(a, t));
	}
	
	public default <S> FStream<T> foldLeak(S accum,
											BiFunction<? super S,? super T,? extends Tuple<S,T>> folder) {
		Preconditions.checkArgument(folder != null, "folder is null");
		
		return new FoldLeftLeakFStream<S,T>(this, accum, folder);
	}
	
	public default <S> S fold(S accum, BiFunction<? super S,? super T,? extends S> folder) {
		Preconditions.checkArgument(folder != null, "folder is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				accum = folder.apply(accum, next.getUnchecked());
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <S> S fold(S accum, S stopper, BiFunction<? super S,? super T,? extends S> folder) {
		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(folder != null, "folder is null");
		
		try {
			if ( accum.equals(stopper) ) {
				return accum;
			}
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				accum = folder.apply(accum, next.get());
				if ( accum.equals(stopper) ) {
					return accum;
				}
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <S> S collect(S accum, BiConsumer<? super S,? super T> collect) {
		Preconditions.checkArgument(accum != null, "accum is null");
		Preconditions.checkArgument(collect != null, "collect is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				collect.accept(accum, next.get());
			}
			
			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 주어진 키에 해당하는 데이터별로 reduce작업을 수행한다.
	 * <p>
	 * 수행된 결과는 key별로 {@link Map}에 저장되어 반환된다.
	 * 
	 * @param <K>	{@code keyer}를 통해 생성되는 키 타입 클래스.
	 * @param keyer	입력 데이터에서 키를 뽑아내는 함수.
	 * @param reducer	reduce 함수.
	 * @return	키 별로 reduce된 결과를 담은 Map 객체.
	 */
	public default <K> Map<K,T> reduceByKey(Function<? super T,? extends K> keyer,
											BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkArgument(keyer != null, "keyer is null");
		Preconditions.checkArgument(reducer != null, "reducer is null");
		
		return collect(Maps.newHashMap(), (accums,v) ->
			accums.compute(keyer.apply(v), (k,old) -> (old != null) ? reducer.apply(old, v) : v));
	}
	
	public default <K,S> Map<K,S> foldByKey(Function<? super T,? extends K> keyer,
											Function<? super K,? extends S> accumInitializer,
											BiFunction<? super S,? super T,? extends S> folder) {
		Map<K,S> accumMap = Maps.newHashMap();
		return collect(accumMap,
						(accums,v) -> accums.compute(keyer.apply(v),
													(k,accum) -> {
														if ( accum != null ) {
															accum = accumInitializer.apply(k);
														}
														return folder.apply(accum, v);
													})
		);
	}
	
	public default <K,S> Map<K,S> collectByKey(Function<? super T,? extends K> keyer,
												Function<? super K,? extends S> initState,
												BiConsumer<? super S,? super T> collector) {
		return collect(Maps.newHashMap(),
						(accums,v) -> {
							S accum = accums.computeIfAbsent(keyer.apply(v), initState);
							collector.accept(accum, v);
						});
	}
	
	
	
	
	

	/**
	 * 스트림에 포함된 데이터 중에서 마지막 {@code count}개의 원소로 구성된 리스트를 반환된다.
	 * 
	 * @param count	선택할 데이터의 갯수.
	 * @return		마지막 {@code count}개로 구성된 데이터 리스트.
	 */
	public default List<T> takeLast(int count) {
		checkArgument(count >= 0, () -> "count >= 0: but: " + count);
		
		try {
			List<T> list = new ArrayList<>(count + 1);
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				list.add(next.getUnchecked());
				if ( list.size() > count ) {
					list.remove(0);
				}
			}
			
			return list;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 모든 데이터에 차례대로 {@link Consumer#accept(Object)}를 호출한다.
	 * <p>
	 * 모든 데이터에 대한 호출이 완료되면 스트림의 {@link #closeQuietly()}를 호출한다.
	 * 
	 * @param effect	Consumer 객체.
	 */
	public default void forEach(Consumer<? super T> effect) {
		checkNotNullArgument(effect, "effect is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				effect.accept(next.get());
			}
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 이용하여 {@link CheckedConsumer#accept(Object)}를 호출한다.
	 * <p>
	 * 모든 데이터에 대한 호출이 완료되면 스트림의 {@link #closeQuietly()}를 호출한다.
	 * {@code effect} 수행 중 오류가 발생되면 해당 데이터에 대한 호출을 무시하고, 다음 데이터를 처리한다.
	 * 
	 * @param effect	Consumer 객체.
	 */
	public default void forEachOrIgnore(CheckedConsumer<? super T> effect) {
		FOption<T> next;
		while ( (next = next()).isPresent() ) {
			T input = next.getUnchecked();
			Try.run(() -> effect.accept(input));
		}
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 이용하여 {@link CheckedConsumerX#accept(Object)}를 호출한다.
	 * <p>
	 * 모든 데이터에 대한 호출이 완료되면 스트림의 {@link #closeQuietly()}를 호출한다.
	 * {@code effect} 수행 중 오류가 발생되면 남은 데이터에 대한 처리를 멈추고 해당
	 * 예외를 발생시킨다.
	 * 
	 * @param effect	Consumer 객체.
	 */
	public default <X extends Throwable>
	void forEachOrThrow(CheckedConsumerX<? super T,X> effect) throws X {
		checkNotNullArgument(effect, "effect is null");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				effect.accept(next.get());
			}
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 데이터의 갯수를 반환한다.
	 * <p>
	 * 메소드 호출 후에는 {@link FStream#close()} 가 호출된다.
	 * 
	 * @return	스트림에 포함된 데이터의 갯수
	 */
	public default long count() {
		try {
			long count = 0;
			while ( next().isPresent() ) {
				++count;
			}
			
			return count;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 첫번째 원소 데이터를 반환한다.
	 * 
	 * 만일 스트림이 빈 경우에는 {@link FOption#empty()}를 반환한다.
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다. 
	 * 
	 * @return	첫번째 원소 데이터
	 */
	public default FOption<T> findFirst() {
		try {
			return next();
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 원소들 중에선 주어진 조건을 만족하는 첫번째 데이터를 반환한다.
	 * 
	 * 만일 스트림이 빈 경우에는 {@link FOption#empty()}를 반환한다.
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다. 
	 * 
	 * @param pred	검색에 사용할 {@link Predicate}.
	 * @return	첫번째 원소 데이터
	 */
	public default FOption<T> findFirst(Predicate<? super T> pred) {
		try {
			return filter(pred).next();
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 원소들 중에선 마지막 데이터를 반환한다.
	 * 
	 * 만일 스트림이 빈 경우에는 {@link FOption#empty()}를 반환한다.
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다.
	 * 
	 * @return	마지막 원소 데이터
	 */
	public default FOption<T> findLast() {
		try {
			FOption<T> last = FOption.empty();
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				last = next;
			}
			
			return last;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 데이터가 존재하는지 여부를 반환한다.
	 * <p>
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다.
	 * 
	 * @return	한 개 이상의 데이터를 포함한 경우는 {@code true},
	 * 			그렇지 않은 경우는 {@code false}를 반환한다.
	 */
	public default boolean exists() {
		try {
			return findFirst().isPresent();
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 주어진 조건을 만족하는 데이터의 포함 여부를 반환한다.
	 * <p>
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다.
	 * 
	 * @param pred	포함 여부 조건.
	 * @return	한 개 이상의 데이터를 포함한 경우는 {@code true},
	 * 			그렇지 않은 경우는 {@code false}를 반환한다.
	 */
	public default boolean exists(Predicate<? super T> pred) {
		return findFirst(pred).isPresent();
	}
	
	/**
	 * 스트림에 포함된 모든 데이터에 대해 주어진 조건을 만족하는지 여부를 반환한다.
	 * <p>
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다.
	 * 
	 * @param pred	조건.
	 * @return	모든 데이터가 주어진 조건을 만족하는 경우는 {@code true},
	 * 			그렇지 않은 경우는 {@code false}를 반환한다.
	 */
	public default boolean forAll(Predicate<? super T> pred) {
		checkNotNullArgument(pred, "predicate");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				if ( !pred.test(next.getUnchecked()) ) {
					return false;
				}
			}
			return true;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 주어진 {@link Collection}에 추가한다.
	 * 
	 * @param <C>	스트림에 포함된 데이터가 추가될 {@link Collection}의 타입.
	 * @param coll	스트림에 포함된 데이터가 추가될 {@link Collection} 객체.
	 * @return	스트림에 포함된 데이터가 추가된 {@link Collection} 객체.
	 */
	public default <C extends Collection<? super T>> C toCollection(C coll) {
		return collect(coll, (l,t) -> l.add(t));
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 {@link List}로 추가하고 반환한다.
	 * 
	 * @return	스트림에 포함된 데이터가 추가된 {@link List} 객체.
	 */
	public default ArrayList<T> toList() {
		return toCollection(Lists.newArrayList());
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 {@link Set}으로 추가하고 반환한다.
	 * 
	 * @return	스트림에 포함된 데이터가 추가된 {@link Set} 객체.
	 */
	public default HashSet<T> toSet() {
		return toCollection(Sets.newHashSet());
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 순환하는 {@link Iterator} 객체를 반환한다.
	 * 
	 * @return	스트림에 포함된 데이터 순환을 위한 {@link Iterator} 객체.
	 */
	public default Iterator<T> iterator() {
		return new FStreamIterator<>(this);
	}
	
	/**
	 * 스트림에 포함된 모든 데이터를 순환하는 {@link Stream} 객체를 반환한다.
	 * 
	 * @return	스트림에 포함된 데이터 순환을 위한 {@link Stream} 객체.
	 */
	public default Stream<T> stream() {
		return Utilities.stream(iterator());
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public default <V> FStream<V> cast(Class<? extends V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return map(cls::cast);
	}
	
	public default <V> FStream<V> castSafely(Class<? extends V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return filter(cls::isInstance).map(cls::cast);
	}
	
	public default <V extends T> FStream<V> ofExactClass(Class<V> cls) {
		Preconditions.checkArgument(cls != null, "target class is null");
		
		return filter(v -> v.getClass().equals(cls)).map(cls::cast);
	}
	
	
	
	
	
	
	
	
	
	
	


	

	
	public default FStream<T> peek(Consumer<? super T> effect) {
		checkNotNullArgument(effect, "effect is null");
		
		return new PeekedStream<>(this, effect);
	}
	
	public default FStream<List<T>> buffer(int count, int skip) {
		checkArgument(count >= 0, "count >= 0, but: " + count);
		checkArgument(skip > 0, "skip > 0, but: " + skip);
		
		return new BufferedStream<>(this, count, skip);
	}
	
	public default FStream<List<T>> split(Predicate<? super T> delimiter) {
		Preconditions.checkArgument(delimiter != null, "delimiter is null");
		
		return new SplitFStream<>(this, delimiter);
	}
	
	public default <S> S foldRight(S accum, BiFunction<? super T,? super S,? extends S> folder) {
		return FLists.foldRight(toList(), accum, folder);
	}
	
	
	
	
	public default FStream<T> sample(double ratio) {
		checkArgument(ratio >= 0, "ratio >= 0");
		
		return new FStreams.SampledStream<>(this, ratio);
	}
	
	public default FStream<T> sample(long total, double ratio) {
		checkArgument(total >= 0, "total >= 0");
		checkArgument(ratio >= 0, "ratio >= 0");
		
		return new AdaptiveSamplingStream<>(this, total, ratio);
	}
	
	public default FStream<T> shuffle() {
		return new ShuffledFStream<>(this);
	}
	
	public default IntFStream toIntFStream() {
		return new IntFStream.FStreamAdaptor(this.cast(Integer.class));
	}
	
	/**
	 * 본 스트림에 포함된 데이터를 구성된 배열을 반환한다.
	 * 
	 * @param <S>	컴포넌트 타입
	 * @param componentType	 스트림에 포함된 데이터의 클래스.
	 * @return	배열
	 */
	@SuppressWarnings("unchecked")
	public default <S> S[] toArray(Class<S> componentType) {
		checkNotNullArgument(componentType, "component-type is null");
		
		List<T> list = toList();
		S[] array = (S[])Array.newInstance(componentType, list.size());
		return list.toArray(array);
	}
	
	public default PrependableFStream<T> toPrependable() {
		return new PrependableFStream<>(this);
	}
	
	//
	// KeyValueFStream 관련 메소드들
	//
	public default <K> KeyValueFStream<K,T> tagKey(Function<? super T,? extends K> keyer) {
		return KeyValueFStream.from(map(t -> KeyValue.of(keyer.apply(t), t)));
	}
	
	public default <K,V> KeyValueFStream<K,V> toKeyValueStream(Function<? super T, KeyValue<K,V>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return KeyValueFStream.from(map(mapper));
	}
	
	public default <K, V> KeyValueFStream<K, V> toKeyValueStream(Function<? super T, ? extends K> keyer,
																Function<? super T, ? extends V> valuer) {
		Preconditions.checkArgument(keyer != null, "keyer is null");
		Preconditions.checkArgument(valuer != null, "valuer is null");
		
		Function<? super T, KeyValue<K,V>> mapper = t -> KeyValue.of(keyer.apply(t), valuer.apply(t));
		return KeyValueFStream.from(map(mapper));
	}
	
	//
	//
	//
	
	public default FStream<T> sort(Comparator<? super T> cmp) {
		List<T> list = toList();
		list.sort(cmp);
		return from(list);
	}
	
	public default <S extends Comparable<S>> FStream<T> sort(Function<? super T,S> keyer) {
		return sort(keyer, false);
	}
	
	public default <S extends Comparable<S>> FStream<T> sort(Function<? super T,S> keyer, boolean reverse) {
		if ( reverse ) {
			return sort((t1,t2) -> (keyer.apply(t2)).compareTo(keyer.apply(t1)));
		}
		else {
			return sort((t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> sort() {
		return sort((t1,t2) -> ((Comparable)t1).compareTo(t2));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> quasiSort(int queueLength) {
		return new QuasiSortedFStream<>(this, queueLength, (v1,v2) -> ((Comparable)v1).compareTo(v2));
	}
	public default FStream<T> quasiSort(int queueLength, Comparator<T> cmptor) {
		return new QuasiSortedFStream<>(this, queueLength, cmptor);
	}
	public default <S extends Comparable<S>> FStream<T> quasiSort(int queueLength,
																	Function<? super T,S> keyer) {
		return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
	}
	public default <S extends Comparable<S>> FStream<T> quasiSort(int queueLength,
															Function<? super T,S> keyer, boolean reverse) {
		if ( reverse ) {
			return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t2)).compareTo(keyer.apply(t1)));
		}
		else {
			return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
		}
	}
	
    public default FStream<T> takeTopK(int k, Comparator<? super T> cmp) {
        return new TopKPickedFStream<>(this, k, cmp);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public default FStream<T> takeTopK(int k) {
        return new TopKPickedFStream<>(this, k, (t1,t2) -> ((Comparable)t1).compareTo(t2));
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
	public default FStream<T> takeTopK(int k, boolean reverse) {
    	Comparator<? super T> cmptor = (reverse)
    									? (t1,t2) -> ((Comparable)t2).compareTo(t1)
    									: (t1,t2) -> ((Comparable)t1).compareTo(t2); 
        return new TopKPickedFStream<>(this, k, cmptor);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public default <S extends Comparable<S>> FStream<T> takeTopK(int k, Function<? super T,S> keyer) {
        return new TopKPickedFStream<>(this, k, (t1,t2) -> ((Comparable)t1).compareTo(t2));
    }
	
	public default FOption<T> max(Comparator<? super T> cmp) {
		try {
			T max = null;
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( max == null || cmp.compare(v, max) > 0 ) {
					max = v;
				}
			}
			return FOption.ofNullable(max);
		}
		finally {
			closeQuietly();
		}
	}
	public default <K extends Comparable<K>>
	FOption<T> max(Function<? super T,? extends K> keyer) {
		return max((v1,v2) -> keyer.apply(v1).compareTo(keyer.apply(v2)));
	}
	@SuppressWarnings("unchecked")
	public default FOption<T> max() {
		return max((v1,v2) -> ((Comparable<T>)v1).compareTo(v2));
	}
	
	public default List<T> maxMultiple(Comparator<? super T> cmp) {
		try {
			List<T> maxList = Lists.newArrayList();
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( maxList.isEmpty() ) {	// 첫번째 iteration인 경우는 첫 데이터가 최대값이다.
					maxList = Lists.newArrayList(v);
				}
				else {
					int ret = cmp.compare(v, maxList.get(0));
					if ( ret > 0 ) {
						maxList = Lists.newArrayList(v);
					}
					else if ( ret == 0 ) {
						maxList.add(v);
					}
				}
			}
			return maxList;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <K extends Comparable<K>> List<T> maxMultiple(Function<T,K> keyer) {
		List<ComparableKeyValue<K,T>> maxValues = map(v -> ComparableKeyValue.of(keyer.apply(v), v))
																			.maxMultiple();
		return FStream.from(maxValues)
						.map(kv -> kv.value())
						.toList();
	}

	@SuppressWarnings("unchecked")
	public default List<T> maxMultiple() {
		return maxMultiple((v1,v2) -> ((Comparable<T>)v1).compareTo(v2));
	}

	public default FOption<T> min(Comparator<T> cmptor) {
		try {
			T min = null;
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( min == null || cmptor.compare(v, min) < 0 ) {
					min = v;
				}
			}
			return FOption.ofNullable(min);
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <K extends Comparable<K>> FOption<T> min(Function<? super T,? extends K> keyer) {
		return min((v1,v2) -> keyer.apply(v1).compareTo(keyer.apply(v2)));
	}

	@SuppressWarnings("unchecked")
	public default FOption<T> min() {
		return min((v1,v2) -> ((Comparable<T>)v1).compareTo(v2));
	}

	public default List<T> minMultiple(Comparator<T> cmptor) {
		try {
			List<T> minList = Lists.newArrayList();
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( minList.isEmpty() ) {	// 첫번째 iteration인 경우는 첫 데이터가 최대값이다.
					minList = Lists.newArrayList(v);
				}
				else {
					int ret = cmptor.compare(v, minList.get(0));
					if ( ret < 0 ) {
						minList = Lists.newArrayList(v);
					}
					else if ( ret == 0 ) {
						minList.add(v);
					}
				}
			}
			return minList;
		}
		finally {
			closeQuietly();
		}
	}
	
	public default <K extends Comparable<K>> List<T> minMultiple(Function<T,K> keyer) {
		List<ComparableKeyValue<K,T>> minValues = map(v -> ComparableKeyValue.of(keyer.apply(v), v))
																			.minMultiple();
		return FStream.from(minValues)
						.map(kv -> kv.value())
						.toList();
	}

	@SuppressWarnings("unchecked")
	public default List<T> minMultiple() {
		return minMultiple((v1,v2) -> ((Comparable<T>)v1).compareTo(v2));
	}
	
	public default String join(String delim, String begin, String end) {
		return zipWithIndex()
				.fold(new StringBuilder(begin),
							(b,t) -> (t.index() > 0) ? b.append(delim).append(""+t.value())
													: b.append(""+t.value()))
					.append(end)
					.toString();
	}
	
	public default String join(String delim) {
		return join(delim, "", "");
	}
	public default String join(char delim) {
		return join(""+delim, "", "");
	}
	
	public default String join(CSV csv) {
		return csv.toString(map(Object::toString));
	}

	public default boolean startsWith(FStream<T> subList) {
		checkNotNullArgument(subList, "subList is null");
		
		FOption<T> subNext = subList.next();
		FOption<T> next = next();
		while ( subNext.isPresent() && next.isPresent() ) {
			if ( !subNext.get().equals(next.get()) ) {
				return false;
			}
			
			subNext = subList.next();
			next = next();
		}
		
		return (next.isPresent() && subNext.isAbsent());
	}
	
	public default FStream<T> distinct() {
		Set<T> keys = Sets.newHashSet();
		return filter(keys::add);
	}
	
	public default <K> FStream<T> distinct(Function<T,K> keyer) {
		Set<K> keys = Sets.newHashSet();
		return this.map(v -> Tuple.of(keyer.apply(v), v))
					.filter(t -> keys.add(t._1))
					.map(t -> t._2);
	}
	
	public default FStream<T> unique() {
		return new UniqueFStream<>(this);
	}
	public default <K> FStream<T> unique(Function<? super T,? extends K> keyer) {
		return new UniqueKeyFStream<>(this, keyer);
	}
	
	public default FStream<T> onClose(Runnable closingTask) {
		checkNotNullArgument(closingTask, "closingTask is null");
		
		return new FStreams.CloserAttachedStream<>(this, closingTask);
	}
	
	public static IntFStream of(int[] values) {
		return IntFStream.of(values);
	}
	
	public static LongFStream of(long[] values) {
		return LongFStream.of(values);
	}
	
	public static DoubleFStream of(double[] values) {
		return DoubleFStream.of(values);
	}
	
	public default IntFStream mapToInt(Function<? super T, Integer> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToIntStream<>(this, mapper);
	}
	
	public default LongFStream mapToLong(Function<? super T, Long> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToLongStream<>(this, mapper);
	}
	
	public default FloatFStream mapToFloat(Function<? super T, Float> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToFloatStream<>(this, mapper);
	}
	
	public default DoubleFStream mapToDouble(Function<? super T, Double> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToDoubleStream<>(this, mapper);
	}
	
	public default BooleanFStream mapToBoolean(Function<? super T, Boolean> mapper) {
		checkNotNullArgument(mapper, "mapper is null");
		
		return new MapToBooleanStream<>(this, mapper);
	}
	
	public default <K extends Comparable<K>, V>
	KeyValueFStream<K, V> mapToKeyValue(Function<? super T, KeyValue<K, V>> mapper) {
		checkNotNullArgument(mapper, "mapper is null");

		return KeyValueFStream.from(map(mapper));
	}
	
	public default <S> FStream<S> lift(Function<FStream<T>, FStream<S>> streamFunc) {
        return streamFunc.apply(this);
    }
}
