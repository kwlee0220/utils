package utils.stream;

import java.io.Closeable;
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import utils.CSV;
import utils.ComparableKeyValue;
import utils.Indexed;
import utils.KeyValue;
import utils.Preconditions;
import utils.Suppliable;
import utils.Tuple;
import utils.Utilities;
import utils.func.CheckedConsumer;
import utils.func.CheckedConsumerX;
import utils.func.CheckedFunction;
import utils.func.CheckedFunctionX;
import utils.func.FOption;
import utils.func.Funcs;
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
 * Pull 기반의 lazy·자원-인식 함수형 스트림 인터페이스.
 * <p>
 * 자체 스트림 모델로 {@link java.util.stream.Stream}과 다음 차이를 가진다.
 * <ul>
 *   <li><b>Pull 모델</b>: 소비자가 {@link #next()}를 호출해 다음 원소를 가져온다. 원소가 더 없으면
 *       {@link FOption#empty()}가 반환되고, 이후 호출도 idempotent하게 빈 결과를 돌려준다.</li>
 *   <li><b>{@link AutoCloseable}</b>: 모든 스트림은 자원을 보유할 수 있다. terminal 메소드
 *       ({@code forEach}, {@code count}, {@code toList} 등)는 정상/예외 흐름 모두에서
 *       {@link #closeQuietly()}를 호출해 자원을 해제한다. {@code map}/{@code filter} 같은
 *       intermediate 연산은 close를 source 스트림으로 위임한다.</li>
 *   <li><b>{@link FOption}/{@link Tuple}/{@link KeyValue}와 일체화</b>: 결과 표현, key-value 스트림
 *       변환({@link #toKeyValueStream}, {@link #tagKey}), zip 결과 등에서 라이브러리 고유 타입을
 *       사용한다.</li>
 *   <li><b>비동기 매핑/flatMap</b>: {@link #mapAsync}, {@link #flatMapAsync}로 워커 수/순서 보존을
 *       옵션으로 지정한 병렬 매핑을 지원한다.</li>
 *   <li><b>primitive 변환</b>: {@link #mapToInt}, {@link #mapToLong}, {@link #mapToFloat},
 *       {@link #mapToDouble}, {@link #mapToBoolean}으로 box 비용 없는 변환을 지원한다.</li>
 * </ul>
 * <p>
 * <b>사용 흐름</b>:
 * <pre>{@code
 * try (FStream<Foo> s = FStream.from(source)) {
 *     s.filter(Foo::isValid)
 *      .map(Foo::summary)
 *      .forEach(System.out::println);   // forEach가 자동 close
 * }
 * }</pre>
 * <p>
 * <b>close 책임</b>: terminal 메소드를 호출하면 자동 close된다. terminal 호출 없이 중간에 폐기될
 * 가능성이 있으면 try-with-resources를 사용한다. {@link #next()}만 직접 호출하는 코드는
 * {@link #close()}를 명시적으로 호출해야 한다.
 * <p>
 * <b>소비 횟수</b>: FStream은 <b>일회성</b>이다 — terminal/{@code next()}로 소진된 스트림은 다시
 * 소비할 수 없다. 재사용이 필요하면 {@code toList()} 등으로 컬렉션화한 뒤 다시 {@link #from}으로
 * 감싸야 한다.
 *
 * @param <T> 스트림 원소 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface FStream<T> extends Iterable<T>, AutoCloseable {
	/**
	 * 스트림에 포함된 다음 데이터를 반환한다.
	 * <p>
	 * 더 이상의 데이터가 없는 경우에는 {@link FOption#empty()}을 반환함.
	 * 더 이상 데이터가 없는 경우에는 메소드 호출은 idempotent하여,
	 * FStream이 close될 때까지 계속해서 호출하더라도 {@link FOption#empty()}을 반환한다.
	 * 이미 close된 경우에는 {@link IllegalStateException} 예외를 발생시킨다.
	 * 
	 * @return	스트림 데이터. 없는 경우는 {@link FOption#empty()}.
	 * @throws IllegalStateException 스트림이 이미 close된 경우.
	 */
	public FOption<T> next() throws IllegalStateException;
	
	/**
	 * 스트림을 닫는다. 예외는 던지지 않고 {@link Try}로 감싸 반환한다.
	 * <p>
	 * 스트림을 위해 할당된 모든 자원을 반환한다. close 도중 예외가 발생하지 않으면
	 * {@link Try#success(Object)}을 (값은 {@code null}), 예외가 발생하면 그 예외를 담은
	 * {@link Try#failure(Throwable)}을 반환한다.
	 *
	 * @return	close 결과를 감싼 {@link Try}.
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
		Preconditions.checkNotNullArgument(values, "null values");
		
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
		Preconditions.checkNotNullArgument(iter, "Iterator is null");
		
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
		Preconditions.checkNotNullArgument(values, "Iterable is null");

		return new AbstractFStream<T>() {
			private Iterator<? extends T> m_iter = values.iterator();

			@Override
			protected void closeInGuard() throws Exception {
				IOUtils.closeQuietly(m_iter);
			}

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
		Preconditions.checkNotNullArgument(stream, "Stream is null");

		return FStream.<T>from(stream.iterator()).onClose(stream::close);
	}

	/**
	 * 주어진 값을 무한 반복하여 반환하는 무한 스트림 객체를 반환한다.
	 * 
	 * @param <T>		스트림이 반환하는 데이터의 타입
	 * @param value		스트림이 반복하여 반환할 데이터 값.
	 * @return	스트림 객체.
	 */
	public static <T> FStream<T> repeat(T value) {
		Preconditions.checkNotNullArgument(value, "repeat value");
		
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
		Preconditions.checkNotNullArgument(value, "repeat value");
		Preconditions.checkArgument(count >= 0, "count >= 0, but: %d", count);

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
		Preconditions.checkNotNullArgument(init, "initial value is null");
		Preconditions.checkNotNullArgument(inc, "next value generator is null");
		
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
		Preconditions.checkNotNullArgument(generator, "generator is null");
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
		Preconditions.checkNotNullArgument(initialState, "initial state is null");
		Preconditions.checkNotNullArgument(gen, "next value generator is null");
		
		return new UnfoldStream<>(initialState, gen);
	}

	/**
	 * 별도의 쓰레드에서 실행되는 데이터 생성 모듈 {@code gen}이 생성하는 데이터로
	 * 구성된 스트림 객체를 생성한다.
	 *
	 * @param <T>		데이터 생성 함수에 의해 생성되는 데이터의 타입
	 * @param gen		데이터 생성 모듈. 데이터 생성 모듈은 데이터를 생성하여 {@link Suppliable}를 통해 제공한다.
	 * @param length	데이터 생성 모듈이 생성하는 데이터를 받을 채널의 내부 버퍼 크기.
	 * @param threadName	데이터 생성 모듈이 실행되는 쓰레드 이름.
	 * 					null인 경우는 시스템에서 자동으로 할당된 이름이 사용된다.
	 * @return	FStream 객체
	 */
	public static <T> FStream<T> asynchronouslyFrom(Generator<T> gen, int length, String threadName) {
		return new GeneratorBasedFStream<>(gen, length, threadName);
	}

	/**
	 * 본 스트림에서 주어진 조건을 만족하는 데이터로만 구성된 스트림을 생성한다.
	 * 
	 * @param pred	조건 객체
	 * @return FStream 객체
	 */
	public default FStream<T> filter(final Predicate<? super T> pred) {
		Preconditions.checkNotNullArgument(pred, "predicate is null");
		
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
		Preconditions.checkNotNullArgument(pred, "predicate is null");
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
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
	FStream<S> mapOrThrow(CheckedFunctionX<? super T,? extends S,X> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
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
		Preconditions.checkNotNullArgument(pred, "predicate is null");
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		
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
	 * <p>
	 * 본 메소드는 {@link AsyncExecutionOptions#create()}로 얻은 기본 옵션을 사용한다. 기본 옵션은:
	 * <ul>
	 *   <li>{@code keepOrder = false} (출력 순서 보존하지 않음)</li>
	 *   <li>{@code workerCount = max(1, availableProcessors - 2)}</li>
	 *   <li>{@code executor = null} (CompletableFuture 기본 풀 사용)</li>
	 *   <li>{@code timeout = -1L} (무제한)</li>
	 * </ul>
	 *
	 * @param <S>		매핑 결과 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @return FStream 객체. 각 원소는 {@code (입력 원소, 매핑 결과)} 형태의 {@link Tuple}이며,
	 *         매핑 결과는 {@link Try}로 감싸져 있다. mapper가 예외를 던지면
	 *         {@link Try#failure(Throwable)}로 결과 스트림에 포함된다.
	 * @see FStream#mapAsync(Function, AsyncExecutionOptions)
	 */
	public default <S> FStream<Tuple<T,Try<S>>> mapAsync(CheckedFunction<? super T, ? extends S> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		return mapCheckedAsync(mapper, AsyncExecutionOptions.create());
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
	 * <p>
	 * 내부적으로 {@code options.getKeepOrder()} 값에 따라 두 구현체 중 하나를 선택한다:
	 * {@code true}이면 입력 순서를 보존하는 {@link OrderedMapAsyncStream}, {@code false}이면 처리량을
	 * 우선하는 {@link UnorderedMapAsyncStream}.
	 *
	 * @param <S>		매핑 결과 데이터의 타입
	 * @param mapper	매핑 함수 객체.
	 * @param options	매핑 적용 옵션
	 * @return FStream 객체. 각 원소는 {@code (입력 원소, 매핑 결과)} 형태의 {@link Tuple}이며,
	 *         매핑 결과는 {@link Try}로 감싸져 있다. mapper가 예외를 던지면
	 *         {@link Try#failure(Throwable)}로 결과 스트림에 포함된다.
	 */
	public default <S> FStream<Tuple<T,Try<S>>> mapCheckedAsync(CheckedFunction<? super T,? extends S> mapper,
													AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		Preconditions.checkNotNullArgument(options, "AsyncExecutionOptions is null");
		
		if ( options.getKeepOrder() ) {
			return new OrderedMapAsyncStream<>(this, mapper, options);
		}
		else {
			return new UnorderedMapAsyncStream<>(this, mapper, options);
		}
	}

	/**
	 * 본 스트림의 각 원소에 매핑 함수 {@code mapper}를 병렬로 적용한 결과로 구성된 스트림을 생성한다.
	 * <p>
	 * {@link #mapCheckedAsync(CheckedFunction, AsyncExecutionOptions)}와 달리 반환되는 스트림의 원소는
	 * 입력 원소를 동반하지 않는 {@link Try} 단독 값이다. mapper가 예외를 던지면
	 * {@link Try#failure(Throwable)}, 정상이면 {@link Try#success(Object)}로 노출된다.
	 * <p>
	 * 동작 방식은 {@code options}에 따라 결정된다.
	 * <dl>
	 * 	<dt>{@link AsyncExecutionOptions#getKeepOrder()}</dt>
	 * 	<dd>{@code true}이면 입력 순서가 보존되고, {@code false}이면 매핑 완료 순서로 출력된다.</dd>
	 * 	<dt>{@link AsyncExecutionOptions#getWorkerCount()}</dt>
	 * 	<dd>동시에 실행되는 최대 워커 수.</dd>
	 * 	<dt>{@link AsyncExecutionOptions#getExecutor()}</dt>
	 * 	<dd>매핑 실행에 사용할 {@link Executor}. {@code null}이면 기본 풀을 사용한다.</dd>
	 * </dl>
	 *
	 * @param <S>		매핑 결과 데이터의 타입.
	 * @param mapper	매핑 함수.
	 * @param options	매핑 적용 옵션.
	 * @return	각 원소가 {@link Try}로 감싸진 결과 스트림.
	 * @see #mapCheckedAsync(CheckedFunction, AsyncExecutionOptions)
	 */
	public default <S> FStream<Tuple<T,Try<S>>> mapAsync(Function<? super T,? extends S> mapper,
														AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		Preconditions.checkNotNullArgument(options, "AsyncExecutionOptions is null");

		CheckedFunction<? super T,? extends S> checkedMapper = (t) -> mapper.apply(t);

		if ( options.getKeepOrder() ) {
			return new OrderedMapAsyncStream<>(this, checkedMapper, options);
		}
		else {
			return new UnorderedMapAsyncStream<>(this, checkedMapper, options);
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		
		return concat(map(mapper));
	}
	
	/**
	 * 본 스트림의 각 원소에 {@code mapper}를 적용하여 얻은 {@link Iterable}들을 flattening한 스트림을
	 * 생성한다.
	 * <p>
	 * {@code map(mapper).flatMap(it -> FStream.from(it))}과 동치이다.
	 *
	 * @param <V>		결과 스트림 원소 데이터 타입.
	 * @param mapper	각 원소를 {@link Iterable}로 변환하는 함수.
	 * @return	flattening된 결과 스트림.
	 */
	public default <V> FStream<V> flatMapIterable(Function<? super T, ? extends Iterable<V>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		return flatMap(mapper.andThen(FStream::from));
	}

	/**
	 * 본 스트림의 각 원소에 {@code mapper}를 적용하여 얻은 배열들을 flattening한 스트림을 생성한다.
	 * <p>
	 * {@code map(mapper).flatMap(arr -> FStream.of(arr))}와 동치이다.
	 *
	 * @param <V>		결과 스트림 원소 데이터 타입.
	 * @param mapper	각 원소를 배열로 변환하는 함수.
	 * @return	flattening된 결과 스트림.
	 */
	public default <V> FStream<V> flatMapArray(Function<? super T,V[]> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		
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
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

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
	 * @return	flattening된 결과 스트림. {@code keepOrder=true}이면 입력 순서 보존,
	 *          {@code false}이면 워커 스레드의 종료 순서로 출력된다. mapper가 예외를 던지면 그 시점의
	 *          입력 원소는 결과 스트림에 포함되지 않는다(예외 처리 정책은 내부 dispatcher에 의해 결정).
	 */
	public default <V> FStream<V>
	flatMapCheckedAsync(CheckedFunction<? super T, ? extends FStream<V>> mapper, AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		Preconditions.checkNotNullArgument(options, "options is null");
		
		if ( options.getKeepOrder() ) {
			return mapCheckedAsync(mapper, options)
					.flatMapTry(tried -> tried._2())
					.flatMap(strm -> strm);
		}
		else {
			Function<? super T, ? extends FStream<V>> asyncMapper = mapper.toSneakyThrowFunction();
			FStream<FStream<V>> strmOfStreams = this.map(t -> new FlatMapDataSupplier<T,V>(t, asyncMapper));
			return FStream.mergeParallel(strmOfStreams, options.getWorkerCount(), options.getExecutor());
		}
	}
	
	public default <V> FStream<V>
	flatMapAsync(Function<? super T, ? extends FStream<V>> mapper, AsyncExecutionOptions options) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		Preconditions.checkNotNullArgument(options, "options is null");
		
		if ( options.getKeepOrder() ) {
			return mapAsync(mapper, options)
					.flatMapTry(tried -> tried._2())
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
			private final ArrayDeque<T> m_tail = new ArrayDeque<T>(count+1);
			private boolean m_filled = false;

			@Override
			public FOption<T> getNext(FStream<T> src) {
				if ( !m_filled ) {
					m_filled = true;

					for ( int i = 0; i < count; ++i ) {
						FOption<T> next = src.next();
						if ( next.isAbsent() ) {
							return FOption.empty();
						}

						m_tail.addLast(next.getUnchecked());
					}
				}

				FOption<T> next = src.next();
				if ( next.isAbsent() ) {
					return FOption.empty();
				}

				m_tail.addLast(next.getUnchecked());
				return FOption.of(m_tail.pollFirst());
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
		Preconditions.checkNotNullArgument(pred, "predicate is null");
		
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
		Preconditions.checkNotNullArgument(pred, "predicate is null");
		
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

	/**
	 * 본 스트림의 각 원소에 인덱스를 결합하여 {@link Indexed} 객체로 구성된 스트림을 생성한다.
	 * <p>
	 * 인덱스는 {@code start}에서 시작하여 1씩 증가한다.
	 *
	 * @param start	첫 원소에 부여될 인덱스 값.
	 * @return	{@link Indexed} 원소로 구성된 스트림 객체.
	 */
	public default FStream<Indexed<T>> zipWithIndex(int start) {
		return zipWith(FStream.generate(start, v -> v+1), Indexed::with);
	}

	/**
	 * 본 스트림의 각 원소에 0부터 시작하는 인덱스를 결합하여 {@link Indexed} 객체로 구성된
	 * 스트림을 생성한다.
	 *
	 * @return	{@link Indexed} 원소로 구성된 스트림 객체.
	 */
	public default FStream<Indexed<T>> zipWithIndex() {
		return zipWithIndex(0);
	}

	/**
	 * 두 {@link Iterable}의 원소를 같은 위치끼리 짝지어 {@link Tuple}로 구성된 스트림을 생성한다.
	 * <p>
	 * {@code longest}가 {@code false}이면 둘 중 한 쪽이 먼저 끝날 때 결과 스트림도 종료되고,
	 * {@code true}이면 양쪽이 모두 끝날 때까지 진행하며 부족한 쪽은 {@code null}로 채워진다.
	 *
	 * @param <T>		첫번째 입력 원소 타입.
	 * @param <S>		두번째 입력 원소 타입.
	 * @param first		첫번째 입력 {@link Iterable}.
	 * @param second	두번째 입력 {@link Iterable}.
	 * @param longest	두 입력의 길이가 다를 때의 동작 결정 플래그.
	 * @return	두 입력 원소가 짝지어진 {@link Tuple} 스트림.
	 */
	public static <T,S> FStream<Tuple<T, S>> zip(Iterable<T> first, Iterable<S> second, boolean longest) {
		return FStream.from(first).zipWith(FStream.from(second), longest);
	}

	/**
	 * 두 {@link Iterable}의 원소를 같은 위치끼리 짝지어 {@link Tuple}로 구성된 스트림을 생성한다.
	 * <p>
	 * 둘 중 한 쪽이 먼저 끝나면 결과 스트림도 종료된다.
	 * ({@link #zip(Iterable, Iterable, boolean)}에 {@code longest=false}를 적용한 것과 동일.)
	 *
	 * @param <T>		첫번째 입력 원소 타입.
	 * @param <S>		두번째 입력 원소 타입.
	 * @param first		첫번째 입력 {@link Iterable}.
	 * @param second	두번째 입력 {@link Iterable}.
	 * @return	두 입력 원소가 짝지어진 {@link Tuple} 스트림.
	 */
	public static <T,S> FStream<Tuple<T, S>> zip(Iterable<T> first, Iterable<S> second) {
		return FStream.zip(first, second, false);
	}

	/**
	 * 본 스트림과 주어진 스트림의 원소를 같은 위치끼리 짝지어 {@link Tuple}로 구성된 스트림을 생성한다.
	 * <p>
	 * 둘 중 한 쪽이 먼저 끝나면 결과 스트림도 종료된다.
	 *
	 * @param <S>	{@code other} 스트림의 원소 타입.
	 * @param other	짝지을 대상 스트림.
	 * @return	{@link Tuple} 원소로 구성된 결과 스트림.
	 */
	public default <S> FStream<Tuple<T,S>> zipWith(FStream<S> other) {
		return zipWith(other, false);
	}

	/**
	 * 본 스트림과 주어진 스트림의 원소를 같은 위치끼리 짝지어 {@code zipper}로 결합한 결과로 구성된
	 * 스트림을 생성한다.
	 * <p>
	 * 둘 중 한 쪽이 먼저 끝나면 결과 스트림도 종료된다.
	 *
	 * @param <S>		{@code other} 스트림의 원소 타입.
	 * @param <Z>		결합 결과 타입.
	 * @param other		짝지을 대상 스트림.
	 * @param zipper	두 원소를 결합하는 함수.
	 * @return	{@code zipper}의 결과로 구성된 스트림.
	 */
	public default <S,Z> FStream<Z> zipWith(FStream<S> other,
											BiFunction<? super T, ? super S, ? extends Z> zipper) {
		return zipWith(other, zipper, false);
	}

	/**
	 * 본 스트림과 주어진 스트림의 원소를 같은 위치끼리 짝지어 {@link Tuple}로 구성된 스트림을 생성한다.
	 * <p>
	 * {@code longest}가 {@code false}이면 둘 중 한 쪽이 먼저 끝날 때 결과 스트림도 종료되고,
	 * {@code true}이면 양쪽이 모두 끝날 때까지 진행하며 부족한 쪽은 {@code null}로 채워진다.
	 *
	 * @param <S>		{@code other} 스트림의 원소 타입.
	 * @param other		짝지을 대상 스트림.
	 * @param longest	두 입력의 길이가 다를 때의 동작 결정 플래그.
	 * @return	{@link Tuple} 원소로 구성된 결과 스트림.
	 */
	public default <S> FStream<Tuple<T,S>> zipWith(FStream<S> other, boolean longest) {
		Preconditions.checkNotNullArgument(other, "zip FStream is null");

		return zipWith(other, (t,s) -> Tuple.of(t,s), longest);
	}

	/**
	 * 본 스트림과 주어진 스트림의 원소를 같은 위치끼리 짝지어 {@code zipper}로 결합한 결과로 구성된
	 * 스트림을 생성한다.
	 * <p>
	 * {@code longest}가 {@code false}이면 둘 중 한 쪽이 먼저 끝날 때 결과 스트림도 종료되고,
	 * {@code true}이면 양쪽이 모두 끝날 때까지 진행하며 부족한 쪽 입력은 {@code null}로 {@code zipper}에
	 * 전달된다.
	 *
	 * @param <S>		{@code other} 스트림의 원소 타입.
	 * @param <Z>		결합 결과 타입.
	 * @param other		짝지을 대상 스트림.
	 * @param zipper	두 원소를 결합하는 함수.
	 * @param longest	두 입력의 길이가 다를 때의 동작 결정 플래그.
	 * @return	{@code zipper}의 결과로 구성된 스트림.
	 */
	public default <S,Z> FStream<Z> zipWith(FStream<S> other,
											BiFunction<? super T, ? super S, ? extends Z> zipper,
											boolean longest) {
		Preconditions.checkNotNullArgument(other, "zip FStream is null");
		Preconditions.checkNotNullArgument(zipper, "zipper is null");

		return new ZippedFStream<>(this, other, zipper, longest);
	}
	
	/**
	 * 본 스트림에 주어진 {@link Slice}를 적용하여 부분 구간 스트림을 생성한다.
	 * <p>
	 * 입력 스트림의 첫 원소를 인덱스 0으로 셀 때, 결과 스트림에는 다음 조건을 모두 만족하는
	 * 원소만 포함된다.
	 * <ul>
	 *   <li>인덱스가 {@link Slice#start()} 이상 (start가 {@code null}이거나 0 이하이면 처음부터)</li>
	 *   <li>인덱스가 {@link Slice#end()} 미만 (end가 {@code null}이면 끝까지)</li>
	 *   <li>{@code (인덱스 - start) % step == 0} (step이 {@code null}이면 전부 포함)</li>
	 * </ul>
	 * Python의 {@code list[start:end:step]} 와 동일한 의미를 가지며, 음수 인덱스는 지원하지 않는다.
	 *
	 * @param slice	부분 구간 정의 객체.
	 * @return	부분 구간 원소들로 구성된 {@link FStream} 객체.
	 */
	public default FStream<T> slice(Slice slice) {
		Preconditions.checkNotNullArgument(slice, "Slice was null");
		return SlicedFStream.from(this, slice);
	}
	
	/**
	 * 인자로 주어진 배열에 포함된 iterable들을 차례대로 순환하는 스트림을 반환한다.
	 * 
	 * @param <T>			스트림의 원소 타입
	 * @param iterables		스트림을 구성할 iterable들의 배열.
	 * @return	{@code FStream} 객체.
	 */
	@SafeVarargs
	public static <T> FStream<T> concat(Iterable<T>... iterables) {
		Preconditions.checkNotNullArgument(iterables, "source iterables");
		FStream<FStream<T>> streamOfStreams = FStream.of(iterables).map((Iterable<T> it) -> FStream.from(it));
		return FStream.concat(streamOfStreams);
	}

	/**
	 * 주어진 스트림들을 차례대로 이어 붙인 하나의 스트림을 생성한다.
	 * <p>
	 * 결과 스트림은 첫번째 스트림의 모든 원소를 먼저 방출한 뒤 두번째 스트림으로 넘어가는 식으로
	 * 진행된다. 결과 스트림이 close되면 아직 소비하지 못한 모든 입력 스트림도 close된다.
	 *
	 * @param <T>		스트림의 원소 타입.
	 * @param streams	이어 붙일 스트림들.
	 * @return	{@code FStream} 객체.
	 */
	@SafeVarargs
	public static <T> FStream<T> concat(FStream<T>... streams) {
		Preconditions.checkNotNullArgument(streams, "source streams");
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
		Preconditions.checkNotNullArgument(fact, "source FStream factory");
		
		return new ConcatedStream<>(fact);
	}
	
	/**
	 * 현 스트림에 주어진 스트림을 연결하여 하나의 스트림 객체를 생성한다.
	 * 
	 * @param follower	본 스트림에 뒤에 붙일 스트림 객체.
	 * @return	{@code FStream} 객체.
	 */
	public default FStream<T> concatWith(FStream<T> follower) {
		Preconditions.checkNotNullArgument(follower, "follower is null");
		
		return concat(FStream.of(this, follower));
	}
	/**
	 * 현 스트림 뒤에 주어진 {@link Iterable}을 이어 붙인 스트림을 생성한다.
	 *
	 * @param follower	본 스트림 뒤에 이어 붙일 {@link Iterable} 객체.
	 * @return	{@code FStream} 객체.
	 */
	public default FStream<T> concatWith(Iterable<T> follower) {
		Preconditions.checkNotNullArgument(follower, "follower is null");

		return concat(FStream.of(this, FStream.from(follower)));
	}
	
	/**
	 * 현 스트림에 주어진 원소를 추가하여 하나의 스트림 객체를 생성한다.
	 * 
	 * @param tail	본 스트림에 뒤에 붙일 원소 객체.
	 * @return	{@code FStream} 객체.
	 */
	public default FStream<T> concatWith(T tail) {
		Preconditions.checkNotNullArgument(tail, "tail is null");
		
		return concatWith(FStream.of(tail));
	}
	
	/**
	 * {@code inputStreamFact}가 반환하는 {@link FStream}들에서 생성하는 데이터들이 합쳐진
	 * 데이터로 구성된 스트림을 반환한다.
	 * <p>
	 * {@code inputStreamFact}가 반환하는 {@link FStream}마다 별도의 쓰레드가 할당되어
	 * 병렬적으로 자신이 맡은 FStream에서 데이터 얻어 출력 스트림으로 전송하기 때문에,
	 * 출력 스트림에는 여러 입력 스트림에서 생성된 데이터가 섞일 수 있다.
	 * <p>
	 * 이때 수행되는 쓰레드의 수는 인자로 주어진 {@code workerCount}에 의해 결정된다.
	 * 
	 * @param <T>	스트림의 원소 타입
	 * @param inputStreamFact	입력 스트림의 스트림 객체.
	 * @param workerCount	스트림 merge 작업을 수행하는 쓰레드의 갯수.
	 * @param executor		쓰레드 풀 객체.
	 * 						{@code null}인 경우는 별도의 쓰레드 풀을 사용하지 않고,
	 * 						각 작업마다 {@link Thread}를 새로 생성하여 수행한다.
	 * @return	{@code FStream} 객체.
	 */
	public static <T> FStream<T> mergeParallel(FStream<? extends FStream<? extends T>> inputStreamFact,
												int workerCount, @Nullable Executor executor) {
		return new MergeParallelFStream<>(inputStreamFact, workerCount, executor);
	}
	
	/**
	 * 스트림의 각 원소를 좌측 누적으로 결합하며 <b>중간 누적값을 모두 노출</b>하는 스트림을 생성한다.
	 * <p>
	 * "Leak"은 종결 연산(축소된 단일 값)이 아닌 lazy 스트림(중간 결과의 시퀀스)으로 결과를 흘려보낸다는
	 * 의미이다. 결과 스트림의 길이는 입력 스트림의 길이와 같다.
	 * <pre>
	 *   입력: [1, 2, 3, 4],   combiner: (a,b) -&gt; a+b
	 *   결과: [1, 3, 6, 10]
	 * </pre>
	 * <p>
	 * <b>close 의미</b>: 본 메소드는 lazy 스트림을 반환하므로 본 스트림을 즉시 close하지 않는다. 결과
	 * 스트림에 대해 terminal 연산을 호출하거나 결과 스트림을 close하면 본 스트림으로 close가 전파된다.
	 *
	 * @param combiner 직전 누적값과 다음 원소로부터 새 누적값을 계산하는 함수.
	 * @return 중간 누적값들로 구성된 스트림.
	 */
	public default FStream<T> reduceLeak(BiFunction<? super T,? super T,? extends T> combiner) {
		Preconditions.checkNotNullArgument(combiner, "combiner is null");

		return new ScannedStream<>(this, combiner);
	}

	/**
	 * 스트림의 원소들을 binary 함수로 결합한다.
	 * <p>
	 * 빈 스트림이면 {@link FOption#empty()}를 반환한다. 원소가 1개면 그 원소가, 2개 이상이면 첫
	 * 원소부터 좌측 누적으로 {@code reducer}를 적용한 결과가 {@link FOption#of(Object)}로 감싸여
	 * 반환된다. 메소드 종료 시 스트림은 {@link #closeQuietly()}를 통해 닫힌다.
	 * <p>
	 * 초기값이 필요하면 {@link #fold(Object, BiFunction)}을 사용한다.
	 *
	 * @param reducer 두 원소를 결합하는 함수.
	 * @return 누적 결과를 감싼 {@link FOption}. 빈 스트림이면 {@link FOption#empty()}.
	 * @throws IllegalArgumentException {@code reducer}가 {@code null}인 경우.
	 */
	public default FOption<T> reduce(BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkNotNullArgument(reducer, "reducer is null");

		try {
			FOption<T> initial = next();
			if ( initial.isAbsent() ) {
				return FOption.empty();
			}

			T accum = initial.get();
			FOption<T> n;
			while ( (n = next()).isPresent() ) {
				accum = reducer.apply(accum, n.getUnchecked());
			}
			return FOption.of(accum);
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 초기 누적값과 스트림 원소로부터 (새 누적값, 방출할 원소) 쌍을 생성하는 누적 스트림을 만든다.
	 * <p>
	 * {@code folder}는 매 원소마다 {@link Tuple}을 반환하며, 첫 요소는 다음 단계로 전달될 누적값,
	 * 두번째 요소는 결과 스트림에 방출되는 원소이다. {@link #foldLeak}는 종결 연산이 아닌 lazy 스트림을
	 * 반환한다.
	 * <p>
	 * <b>close 의미</b>: 본 메소드는 lazy 스트림을 반환하므로 본 스트림을 즉시 close하지 않는다. 결과
	 * 스트림에 대해 terminal 연산을 호출하거나 결과 스트림을 close하면 본 스트림으로 close가 전파된다.
	 *
	 * @param <S>    누적값 타입.
	 * @param accum  초기 누적값.
	 * @param folder 현 누적값과 원소를 받아 (새 누적값, 방출 원소) 쌍을 반환하는 함수.
	 * @return 각 단계의 방출 원소로 구성된 스트림.
	 */
	public default <S> FStream<T> foldLeak(S accum,
											BiFunction<? super S,? super T,? extends Tuple<S,T>> folder) {
		Preconditions.checkNotNullArgument(folder, "folder is null");

		return new FoldLeftLeakFStream<S,T>(this, accum, folder);
	}

	/**
	 * 초기 누적값 {@code accum}에 스트림의 각 원소를 차례로 {@code folder}로 누적한다.
	 * <p>
	 * 메소드 종료 시 스트림은 {@link #closeQuietly()}를 통해 닫힌다.
	 *
	 * @param <S>    누적값 타입.
	 * @param accum  초기 누적값. {@code null} 허용.
	 * @param folder 현 누적값과 다음 원소로부터 새 누적값을 계산하는 함수.
	 * @return 모든 원소를 소비한 후의 최종 누적값.
	 */
	public default <S> S fold(S accum, BiFunction<? super S,? super T,? extends S> folder) {
		Preconditions.checkNotNullArgument(folder, "folder is null");

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
	
	/**
	 * 초기 누적값 {@code accum}에 스트림의 각 원소를 차례로 {@code folder}로 누적하되, 누적 결과가
	 * {@code stopper}와 같아지면 즉시 종료한다.
	 * <p>
	 * {@code accum}과 {@code stopper}는 모두 {@code null}을 허용하며, 비교는
	 * {@link Objects#equals(Object, Object)}로 수행된다. 메소드 종료 시 스트림은
	 * {@link #closeQuietly()}를 통해 닫힌다.
	 *
	 * @param <S>     누적값 타입.
	 * @param accum   초기 누적값.
	 * @param stopper 누적 중단 신호값. 누적 결과가 이 값과 같아지면 즉시 결과를 반환한다.
	 * @param folder  현 누적값과 다음 원소로부터 새 누적값을 계산하는 함수.
	 * @return 누적 결과.
	 */
	public default <S> S fold(S accum, S stopper, BiFunction<? super S,? super T,? extends S> folder) {
		Preconditions.checkNotNullArgument(folder, "folder is null");

		try {
			if ( Objects.equals(accum, stopper) ) {
				return accum;
			}

			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				accum = folder.apply(accum, next.get());
				if ( Objects.equals(accum, stopper) ) {
					return accum;
				}
			}

			return accum;
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 주어진 mutable 누적기에 스트림의 각 원소를 차례로 {@code collect}로 적용한다.
	 * <p>
	 * {@link #fold}와 달리 누적기는 매번 새 객체가 아닌 in-place로 변형되는 mutable 객체이다 (예:
	 * {@link Collection}, {@link Map}, {@link StringBuilder}). 메소드 종료 시 스트림은
	 * {@link #closeQuietly()}를 통해 닫힌다.
	 *
	 * @param <S>     누적기 타입.
	 * @param accum   누적기 객체. {@code null}이면 안 된다.
	 * @param collect 누적기와 원소를 받아 누적기를 in-place로 갱신하는 함수.
	 * @return 모든 원소가 적용된 누적기 (인자와 동일한 인스턴스).
	 */
	public default <S> S collect(S accum, BiConsumer<? super S,? super T> collect) {
		Preconditions.checkNotNullArgument(accum, "accum is null");
		Preconditions.checkNotNullArgument(collect, "collect is null");

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
	 * 수행된 결과는 key별로 {@link Map}에 저장되어 반환된다. 내부적으로 {@link #collect}를 사용하므로
	 * 메소드 종료 시 스트림은 {@link #closeQuietly()}를 통해 닫힌다.
	 *
	 * @param <K>	{@code keyer}를 통해 생성되는 키 타입 클래스.
	 * @param keyer	입력 데이터에서 키를 뽑아내는 함수.
	 * @param reducer	reduce 함수.
	 * @return	키 별로 reduce된 결과를 담은 Map 객체.
	 */
	public default <K> Map<K,T> reduceByKey(Function<? super T,? extends K> keyer,
											BiFunction<? super T,? super T,? extends T> reducer) {
		Preconditions.checkNotNullArgument(keyer, "keyer is null");
		Preconditions.checkNotNullArgument(reducer, "reducer is null");
		
		return collect(Maps.newHashMap(), (accums,v) ->
			accums.compute(keyer.apply(v), (k,old) -> (old != null) ? reducer.apply(old, v) : v));
	}
	
	/**
	 * 키 별로 원소들을 fold하여 {@code Map<K,S>}으로 반환한다.
	 * <p>
	 * 각 원소에 대해 {@code keyer}로 키를 얻은 뒤, 해당 키의 누적값에 {@code folder}를 적용한다.
	 * 키가 처음 등장하면 {@code accumInitializer}로 초기 누적값을 만든다. {@code accumInitializer}가
	 * {@code null}을 반환하면 {@link IllegalStateException}이 발생한다.
	 * <p>
	 * 내부적으로 {@link #collect}를 사용하므로 메소드 종료 시 스트림은 {@link #closeQuietly()}를 통해
	 * 닫힌다.
	 *
	 * @param <K>              키 타입.
	 * @param <S>              누적값 타입.
	 * @param keyer            원소에서 키를 추출하는 함수.
	 * @param accumInitializer 키별 초기 누적값을 생성하는 함수.
	 * @param folder           현 누적값과 원소로부터 새 누적값을 계산하는 함수.
	 * @return 키별로 fold된 결과를 담은 {@link Map}.
	 * @throws IllegalStateException {@code accumInitializer}가 {@code null}을 반환한 경우.
	 */
	public default <K,S> Map<K,S> foldByKey(Function<? super T,? extends K> keyer,
											Function<? super K,? extends S> accumInitializer,
											BiFunction<? super S,? super T,? extends S> folder) {
		Map<K,S> accumMap = Maps.newHashMap();
		return collect(accumMap,
					(accums,v) -> accums.compute(keyer.apply(v),
												(k,accum) -> {
													if ( accum == null ) {
														accum = accumInitializer.apply(k);
														if ( accum == null ) {
															throw new IllegalStateException("accumInitializer returned null for key: " + k);
														}
													}
													return folder.apply(accum, v);
												})
		);
	}

	/**
	 * 키 별로 원소들을 mutable 누적기에 collect하여 {@code Map<K,S>}으로 반환한다.
	 * <p>
	 * 각 원소에 대해 {@code keyer}로 키를 얻고, 해당 키의 누적기가 없으면 {@code initState}로
	 * 새로 만든다. 그 다음 {@code collector}를 통해 누적기에 원소를 in-place로 적용한다.
	 * <p>
	 * 내부적으로 {@link #collect}를 사용하므로 메소드 종료 시 스트림은 {@link #closeQuietly()}를 통해
	 * 닫힌다.
	 *
	 * @param <K>       키 타입.
	 * @param <S>       누적기 타입.
	 * @param keyer     원소에서 키를 추출하는 함수.
	 * @param initState 키별 누적기를 생성하는 함수.
	 * @param collector 누적기와 원소를 받아 누적기를 in-place로 갱신하는 함수.
	 * @return 키별로 collect된 결과를 담은 {@link Map}.
	 */
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
		Preconditions.checkArgument(count >= 0, "count >= 0: but: %d", count);
		
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
		Preconditions.checkNotNullArgument(effect, "effect is null");
		
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
		Preconditions.checkNotNullArgument(effect, "effect is null");

		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T input = next.getUnchecked();
				Try.run(() -> effect.accept(input));
			}
		}
		finally {
			closeQuietly();
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
		Preconditions.checkNotNullArgument(effect, "effect is null");
		
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
	 * <p>
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
	 * <p>
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
	 * <p>
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
		return findFirst().isPresent();
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
	public default boolean allMatch(Predicate<? super T> pred) {
		Preconditions.checkNotNullArgument(pred, "predicate");
		
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
	 * 스트림에 포함된 모든 데이터가 주어진 조건을 만족하지 않는지 여부를 반환한다.
	 * <p>
	 * 즉, 한 개라도 {@code pred}를 만족하면 {@code false}, 그렇지 않으면 {@code true}를 반환한다.
	 * 본 메소드가 호출된 후에는 본 스트림 객체는 폐쇄된다.
	 *
	 * @param pred	조건.
	 * @return	모든 데이터가 조건을 만족하지 않는 경우 {@code true}, 그렇지 않은 경우 {@code false}.
	 */
	public default boolean noneMatch(Predicate<? super T> pred) {
		Preconditions.checkNotNullArgument(pred, "predicate");
		
		try {
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				if ( pred.test(next.getUnchecked()) ) {
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
	 * 본 스트림의 원소들로 구성된 {@link Stream} 객체를 반환한다.
	 * <p>
	 * 반환된 {@link Stream}이 close되면 본 {@code FStream}도 {@link #closeQuietly()}를 통해 닫힌다.
	 * {@link Stream}은 일회성이므로 결과를 다시 소비할 수 없다.
	 *
	 * @return	본 스트림의 원소들로 구성된 {@link Stream} 객체.
	 */
	public default Stream<T> stream() {
		return Utilities.stream(iterator()).onClose(this::closeQuietly);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * 본 스트림의 각 원소를 주어진 클래스 타입 {@code V}로 캐스팅한 결과로 구성된 스트림을 생성한다.
	 * <p>
	 * 캐스팅이 불가능한 원소를 만나면 해당 원소를 처리하는 시점에 {@link ClassCastException}이
	 * 발생한다. 부적합한 원소를 걸러내고 안전하게 캐스팅하려면 {@link #castSafely(Class)}를 사용한다.
	 *
	 * @param <V>	대상 타입.
	 * @param cls	대상 타입의 {@link Class} 객체.
	 * @return	캐스팅된 원소들로 구성된 스트림.
	 */
	public default <V> FStream<V> cast(Class<? extends V> cls) {
		Preconditions.checkNotNullArgument(cls, "target class is null");

		return map(cls::cast);
	}

	/**
	 * 본 스트림의 원소들 중 주어진 클래스의 인스턴스인 원소만 골라 해당 타입으로 캐스팅한 스트림을
	 * 생성한다.
	 * <p>
	 * {@link #cast(Class)}와 달리 {@link Class#isInstance(Object)}로 먼저 검사하므로
	 * {@link ClassCastException}이 발생하지 않는다.
	 *
	 * @param <V>	대상 타입.
	 * @param cls	대상 타입의 {@link Class} 객체.
	 * @return	{@code cls}의 인스턴스인 원소들로 구성된 스트림.
	 */
	public default <V> FStream<V> castSafely(Class<? extends V> cls) {
		Preconditions.checkNotNullArgument(cls, "target class is null");

		return filter(cls::isInstance).map(cls::cast);
	}

	/**
	 * 본 스트림의 원소들 중 정확히 주어진 클래스의 인스턴스인 원소만 골라 해당 타입으로 캐스팅한
	 * 스트림을 생성한다.
	 * <p>
	 * {@link #castSafely(Class)}와 달리 하위 클래스 인스턴스는 제외하고, 클래스가 정확히 일치하는
	 * 원소만 포함한다 ({@code v.getClass().equals(cls)}).
	 *
	 * @param <V>	대상 타입. 본 스트림의 원소 타입의 하위 타입이어야 한다.
	 * @param cls	대상 타입의 {@link Class} 객체.
	 * @return	정확히 {@code cls} 타입인 원소들로 구성된 스트림.
	 */
	public default <V extends T> FStream<V> ofExactClass(Class<V> cls) {
		Preconditions.checkNotNullArgument(cls, "target class is null");

		return filter(v -> v.getClass().equals(cls)).map(cls::cast);
	}

	/**
	 * 본 스트림의 각 원소가 다음 단계로 전달되기 직전에 주어진 {@code effect}를 호출하도록 한
	 * 스트림을 생성한다.
	 * <p>
	 * 결과 스트림의 원소는 본 스트림의 원소와 동일하다. {@code effect}는 부수효과(로깅, 디버깅 등)
	 * 용도로 사용되며, 결과 값에 영향을 주지 않는다.
	 *
	 * @param effect	각 원소에 적용할 부수효과 함수.
	 * @return	동일한 원소를 그대로 흘려 보내는 스트림 객체.
	 */
	public default FStream<T> peek(Consumer<? super T> effect) {
		Preconditions.checkNotNullArgument(effect, "effect is null");

		return new PeekedStream<>(this, effect);
	}

	/**
	 * 본 스트림의 원소들을 길이 {@code count}의 슬라이딩 윈도우로 묶어 그 결과 리스트의 스트림을
	 * 생성한다.
	 * <p>
	 * 각 윈도우는 직전 윈도우의 시작 위치에서 {@code skip}만큼 이동한 위치에서 시작한다.
	 * {@code skip == 1}이면 인접한 두 윈도우가 {@code count - 1}개의 원소를 공유하고,
	 * {@code skip == count}이면 윈도우들이 겹치지 않는다.
	 *
	 * @param count	각 윈도우의 길이.
	 * @param skip	윈도우 사이의 시작 위치 간격. {@code 0}보다 커야 한다.
	 * @return	각 윈도우 리스트로 구성된 스트림.
	 */
	public default FStream<List<T>> buffer(int count, int skip) {
		Preconditions.checkArgument(count >= 0, "count >= 0, but: " + count);
		Preconditions.checkArgument(skip > 0, "skip > 0, but: " + skip);

		return new BufferedStream<>(this, count, skip);
	}

	/**
	 * {@code delimiter}를 만족하는 원소를 구분자로 사용하여 본 스트림을 여러 부분 리스트로 나눈
	 * 스트림을 생성한다.
	 * <p>
	 * 구분자에 해당하는 원소는 결과 리스트에 포함되지 않는다.
	 *
	 * @param delimiter	구분자 판단 조건.
	 * @return	구분자 사이의 원소들을 모은 리스트로 구성된 스트림.
	 */
	public default FStream<List<T>> split(Predicate<? super T> delimiter) {
		Preconditions.checkNotNullArgument(delimiter, "delimiter is null");

		return new SplitFStream<>(this, delimiter);
	}

	/**
	 * 본 스트림에 right-fold(우측 결합 fold)를 적용하여 단일 결과를 반환한다.
	 * <p>
	 * 내부적으로 본 스트림을 모두 리스트로 수집한 뒤 마지막 원소부터 거꾸로 결합한다.
	 * 즉, 입력이 {@code [a, b, c]}이고 초기값이 {@code z}이면 결과는
	 * {@code folder(a, folder(b, folder(c, z)))}이다.
	 * 본 메소드는 모든 원소를 메모리에 적재하므로 무한 스트림에는 사용할 수 없다.
	 *
	 * @param <S>		fold 결과 타입.
	 * @param accum		초기값.
	 * @param folder	각 원소와 누적값을 결합하는 함수.
	 * @return	fold 결과.
	 */
	public default <S> S foldRight(S accum, BiFunction<? super T,? super S,? extends S> folder) {
		return Funcs.foldRight(toList(), accum, folder);
	}
	
	
	
	
	/**
	 * 본 스트림의 원소를 확률 기반으로 sampling한 스트림을 생성한다.
	 * <p>
	 * 각 원소는 {@code ratio}의 확률로 결과 스트림에 포함된다.
	 * {@code ratio >= 1.0}이면 모든 원소가 그대로 통과한다.
	 *
	 * @param ratio	각 원소가 결과 스트림에 포함될 확률 (0 이상).
	 * @return	sampling된 스트림.
	 */
	public default FStream<T> sample(double ratio) {
		Preconditions.checkArgument(ratio >= 0, "ratio >= 0");

		return new FStreams.SampledStream<>(this, ratio);
	}

	/**
	 * 본 스트림에서 총 원소 수가 {@code total}임을 알고 있을 때 sampling 비율을 보장하는
	 * 적응형 sampling 스트림을 생성한다.
	 *
	 * @param total	본 스트림의 총 원소 수 (예상값).
	 * @param ratio	sampling 비율 (0 이상).
	 * @return	적응형 sampling 스트림.
	 */
	public default FStream<T> sample(long total, double ratio) {
		Preconditions.checkArgument(total >= 0, "total >= 0");
		Preconditions.checkArgument(ratio >= 0, "ratio >= 0");

		return new AdaptiveSamplingStream<>(this, total, ratio);
	}

	/**
	 * 본 스트림의 원소를 무작위 순서로 섞은 스트림을 생성한다.
	 * <p>
	 * 본 메소드는 모든 원소를 메모리에 적재한 뒤 무작위로 꺼내는 방식이므로 무한 스트림이나 매우 큰
	 * 스트림에는 사용할 수 없다.
	 *
	 * @return	원소가 무작위 순서로 섞인 스트림.
	 */
	public default FStream<T> shuffle() {
		return new ShuffledFStream<>(this);
	}

	/**
	 * 본 스트림의 원소를 {@link Integer}로 캐스팅한 primitive {@link IntFStream}을 생성한다.
	 * <p>
	 * 원소가 {@link Integer}가 아닌 경우 매핑 시점에 {@link ClassCastException}이 발생한다.
	 *
	 * @return	{@link IntFStream} 객체.
	 */
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
		Preconditions.checkNotNullArgument(componentType, "component-type is null");
		
		List<T> list = toList();
		S[] array = (S[])Array.newInstance(componentType, list.size());
		return list.toArray(array);
	}
	
	/**
	 * 본 스트림을 원소 prepend가 가능한 {@link PrependableFStream}으로 감싼다.
	 * <p>
	 * 반환된 스트림은 일반 {@link FStream} 연산 외에 머리에 원소를 다시 끼워 넣어 다음 {@code next()}
	 * 호출에서 꺼낼 수 있는 기능을 제공한다.
	 *
	 * @return	{@link PrependableFStream} 객체.
	 */
	public default PrependableFStream<T> toPrependable() {
		return new PrependableFStream<>(this);
	}
	
	//
	// KeyValueFStream 관련 메소드들
	//
	/**
	 * 본 스트림의 각 원소에 {@code keyer}로 추출한 키를 결합하여 {@link KeyValueFStream}을 생성한다.
	 * <p>
	 * 결과 스트림의 각 원소는 {@code (keyer.apply(value), value)} 형태의 {@link KeyValue}이다.
	 *
	 * @param <K>	키 타입.
	 * @param keyer	각 원소에서 키를 추출하는 함수.
	 * @return	키-원소 쌍으로 구성된 {@link KeyValueFStream}.
	 */
	public default <K> KeyValueFStream<K,T> tagKey(Function<? super T,? extends K> keyer) {
		Preconditions.checkNotNullArgument(keyer, "keyer is null");
		return KeyValueFStream.from(map(t -> KeyValue.of(keyer.apply(t), t)));
	}

	/**
	 * 본 스트림의 각 원소를 {@code mapper}로 {@link KeyValue}로 변환하여 {@link KeyValueFStream}을
	 * 생성한다.
	 *
	 * @param <K>		변환 결과의 키 타입.
	 * @param <V>		변환 결과의 값 타입.
	 * @param mapper	원소를 {@link KeyValue}로 변환하는 함수.
	 * @return	{@link KeyValueFStream} 객체.
	 */
	public default <K,V> KeyValueFStream<K,V> toKeyValueStream(Function<? super T, KeyValue<K,V>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return KeyValueFStream.from(map(mapper));
	}

	/**
	 * 본 스트림의 각 원소에서 {@code keyer}로 키를, {@code valuer}로 값을 각각 추출하여
	 * {@link KeyValueFStream}을 생성한다.
	 *
	 * @param <K>		키 타입.
	 * @param <V>		값 타입.
	 * @param keyer		각 원소에서 키를 추출하는 함수.
	 * @param valuer	각 원소에서 값을 추출하는 함수.
	 * @return	{@link KeyValueFStream} 객체.
	 */
	public default <K, V> KeyValueFStream<K, V> toKeyValueStream(Function<? super T, ? extends K> keyer,
																Function<? super T, ? extends V> valuer) {
		Preconditions.checkNotNullArgument(keyer, "keyer is null");
		Preconditions.checkNotNullArgument(valuer, "valuer is null");

		Function<? super T, KeyValue<K,V>> mapper = t -> KeyValue.of(keyer.apply(t), valuer.apply(t));
		return KeyValueFStream.from(map(mapper));
	}
	
	//
	//
	//
	
	/**
	 * 본 스트림의 원소들을 주어진 비교자 기준으로 정렬한 스트림을 생성한다.
	 * <p>
	 * 본 메소드는 모든 원소를 메모리에 적재하여 정렬하므로 무한 스트림이나 매우 큰 스트림에는
	 * 사용할 수 없다. 메모리 사용을 제한하면서 근사 정렬이 필요하면 {@link #quasiSort(int, Comparator)}를
	 * 사용한다.
	 *
	 * @param cmp	비교자.
	 * @return	정렬된 스트림.
	 */
	public default FStream<T> sort(Comparator<? super T> cmp) {
		List<T> list = toList();
		list.sort(cmp);
		return from(list);
	}

	/**
	 * 각 원소에서 추출한 키의 자연 순서로 정렬한 스트림을 생성한다.
	 *
	 * @param <S>	키 타입.
	 * @param keyer	각 원소에서 비교 키를 추출하는 함수.
	 * @return	정렬된 스트림.
	 */
	public default <S extends Comparable<S>> FStream<T> sort(Function<? super T,S> keyer) {
		return sort(keyer, false);
	}

	/**
	 * 각 원소에서 추출한 키의 자연 순서(또는 그 역순)로 정렬한 스트림을 생성한다.
	 *
	 * @param <S>		키 타입.
	 * @param keyer		각 원소에서 비교 키를 추출하는 함수.
	 * @param reverse	{@code true}이면 키의 역순으로 정렬, {@code false}이면 자연 순서.
	 * @return	정렬된 스트림.
	 */
	public default <S extends Comparable<S>> FStream<T> sort(Function<? super T,S> keyer, boolean reverse) {
		if ( reverse ) {
			return sort((t1,t2) -> (keyer.apply(t2)).compareTo(keyer.apply(t1)));
		}
		else {
			return sort((t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
		}
	}

	/**
	 * 본 스트림의 원소들을 자연 순서로 정렬한 스트림을 생성한다.
	 * <p>
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @return	정렬된 스트림.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> sort() {
		return sort((Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}

	/**
	 * 길이 {@code queueLength}의 우선순위 큐를 사용하는 근사 정렬 스트림을 생성한다.
	 * <p>
	 * 입력 원소를 자연 순서 기준으로 큐에 적재하면서 가장 작은 원소부터 출력한다. 큐 크기를
	 * 넘는 범위의 역순열은 정확히 정렬되지 않을 수 있다는 의미에서 "근사" 정렬이다.
	 * 메모리 사용은 {@code queueLength}로 제한되므로 매우 긴 스트림에도 사용할 수 있다.
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @param queueLength	내부 우선순위 큐의 길이.
	 * @return	근사 정렬된 스트림.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default FStream<T> quasiSort(int queueLength) {
		return quasiSort(queueLength, (Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}

	/**
	 * 길이 {@code queueLength}의 우선순위 큐를 사용하는 근사 정렬 스트림을 비교자 기반으로 생성한다.
	 *
	 * @param queueLength	내부 우선순위 큐의 길이.
	 * @param cmptor		비교자.
	 * @return	근사 정렬된 스트림.
	 */
	public default FStream<T> quasiSort(int queueLength, Comparator<? super T> cmptor) {
		return new QuasiSortedFStream<>(this, queueLength, cmptor);
	}

	/**
	 * 길이 {@code queueLength}의 우선순위 큐를 사용하는 근사 정렬 스트림을 키 기반으로 생성한다.
	 *
	 * @param <S>			키 타입.
	 * @param queueLength	내부 우선순위 큐의 길이.
	 * @param keyer			각 원소에서 비교 키를 추출하는 함수.
	 * @return	근사 정렬된 스트림.
	 */
	public default <S extends Comparable<S>> FStream<T> quasiSort(int queueLength,
																	Function<? super T,S> keyer) {
		return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
	}

	/**
	 * 길이 {@code queueLength}의 우선순위 큐를 사용하는 근사 정렬 스트림을 키 기반(자연 순서 또는
	 * 역순)으로 생성한다.
	 *
	 * @param <S>			키 타입.
	 * @param queueLength	내부 우선순위 큐의 길이.
	 * @param keyer			각 원소에서 비교 키를 추출하는 함수.
	 * @param reverse		{@code true}이면 키의 역순, {@code false}이면 자연 순서.
	 * @return	근사 정렬된 스트림.
	 */
	public default <S extends Comparable<S>> FStream<T> quasiSort(int queueLength,
															Function<? super T,S> keyer, boolean reverse) {
		if ( reverse ) {
			return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t2)).compareTo(keyer.apply(t1)));
		}
		else {
			return quasiSort(queueLength, (t1,t2) -> (keyer.apply(t1)).compareTo(keyer.apply(t2)));
		}
	}
	
    /**
     * 본 스트림에서 주어진 비교자 기준으로 상위 {@code k}개 원소만으로 구성된 스트림을 생성한다.
     * <p>
     * 입력 스트림의 모든 원소를 한 번씩 순회하며, {@code cmp} 기준의 큰 값 {@code k}개를
     * 유지하는 방식으로 동작한다. 결과 스트림에 포함되는 원소의 순서는 정렬되어 있지 않을 수 있다.
     * 입력 원소 수가 {@code k}보다 작으면 모든 원소가 그대로 결과에 포함된다.
     *
     * @param k		선택할 상위 원소 개수.
     * @param cmp	상위 판단에 사용할 비교자.
     * @return	상위 {@code k}개 원소로 구성된 스트림.
     */
    public default FStream<T> takeTopK(int k, Comparator<? super T> cmp) {
        return new TopKPickedFStream<>(this, k, cmp);
    }

    /**
     * 본 스트림에서 자연 순서 기준으로 상위 {@code k}개 원소만으로 구성된 스트림을 생성한다.
     * <p>
     * 원소 타입은 {@link Comparable}을 구현해야 한다.
     *
     * @param k	선택할 상위 원소 개수.
     * @return	상위 {@code k}개 원소로 구성된 스트림.
     */
    public default FStream<T> takeTopK(int k) {
        return takeTopK(k, false);
    }

    /**
     * 본 스트림에서 자연 순서 또는 그 역순 기준으로 상위 {@code k}개 원소만으로 구성된
     * 스트림을 생성한다.
     * <p>
     * 원소 타입은 {@link Comparable}을 구현해야 한다.
     *
     * @param k			선택할 상위 원소 개수.
     * @param reverse	{@code true}이면 자연 순서의 역순(즉, 하위 {@code k}개)을 선택, {@code false}이면 자연 순서.
     * @return	상위/하위 {@code k}개 원소로 구성된 스트림.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public default FStream<T> takeTopK(int k, boolean reverse) {
        Comparator<? super T> cmp = (Comparator)Comparator.naturalOrder();
        return takeTopK(k, reverse ? cmp.reversed() : cmp);
    }

    /**
     * 각 원소에 대해 {@code keyer}로 추출한 키의 자연 순서 기준으로 상위 {@code k}개 원소만으로
     * 구성된 스트림을 생성한다.
     *
     * @param <S>	키의 타입.
     * @param k		선택할 상위 원소 개수.
     * @param keyer	각 원소에서 비교 키를 추출하는 함수.
     * @return	상위 {@code k}개 원소로 구성된 스트림.
     */
    public default <S extends Comparable<S>> FStream<T> takeTopK(int k, Function<? super T,S> keyer) {
        return takeTopK(k, keyer, false);
    }

    /**
     * 각 원소에 대해 {@code keyer}로 추출한 키의 자연 순서(또는 그 역순) 기준으로
     * 상위 {@code k}개 원소만으로 구성된 스트림을 생성한다.
     *
     * @param <S>		키의 타입.
     * @param k			선택할 상위 원소 개수.
     * @param keyer		각 원소에서 비교 키를 추출하는 함수.
     * @param reverse	{@code true}이면 키의 역순(즉, 하위 {@code k}개)을 선택, {@code false}이면 자연 순서.
     * @return	상위/하위 {@code k}개 원소로 구성된 스트림.
     */
    public default <S extends Comparable<S>> FStream<T> takeTopK(int k, Function<? super T,S> keyer,
                                                                    boolean reverse) {
        if ( reverse ) {
            return takeTopK(k, (t1,t2) -> keyer.apply(t2).compareTo(keyer.apply(t1)));
        }
        else {
            return takeTopK(k, (t1,t2) -> keyer.apply(t1).compareTo(keyer.apply(t2)));
        }
    }
	
	/**
	 * 비교자 기준으로 본 스트림에서 가장 큰 원소 하나를 반환한다.
	 * <p>
	 * 동률이 여러 개인 경우 그 중 가장 먼저 등장한 원소가 반환된다. 빈 스트림이면
	 * {@link Optional#empty()}를 반환한다. 메소드 종료 시 스트림은 {@link #closeQuietly()}로 닫힌다.
	 *
	 * @param cmp	비교자.
	 * @return	최대 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	public default Optional<T> max(Comparator<? super T> cmp) {
		try {
			T max = null;

			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( max == null || cmp.compare(v, max) > 0 ) {
					max = v;
				}
			}
			return Optional.ofNullable(max);
		}
		finally {
			closeQuietly();
		}
	}
	/**
	 * 각 원소에서 추출한 키의 자연 순서 기준으로 최대 원소를 반환한다.
	 *
	 * @param <K>	키 타입.
	 * @param keyer	각 원소에서 비교 키를 추출하는 함수.
	 * @return	최대 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	public default <K extends Comparable<K>>
	Optional<T> max(Function<? super T,? extends K> keyer) {
		return max((v1,v2) -> keyer.apply(v1).compareTo(keyer.apply(v2)));
	}
	/**
	 * 원소의 자연 순서 기준으로 최대 원소를 반환한다.
	 * <p>
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @return	최대 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default Optional<T> max() {
		return max((Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}

	/**
	 * 비교자 기준으로 최대값을 갖는 모든 원소를 리스트로 반환한다 (동률 포함).
	 * <p>
	 * 빈 스트림이면 빈 리스트를 반환한다. 메소드 종료 시 스트림은 {@link #closeQuietly()}로 닫힌다.
	 *
	 * @param cmp	비교자.
	 * @return	최대값과 동일한 원소들의 리스트.
	 */
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
	
	/**
	 * 각 원소에서 추출한 키의 자연 순서 기준으로 최대값을 갖는 모든 원소를 리스트로 반환한다.
	 *
	 * @param <K>	키 타입.
	 * @param keyer	각 원소에서 비교 키를 추출하는 함수.
	 * @return	최대 키와 동일한 키를 갖는 원소들의 리스트.
	 */
	public default <K extends Comparable<K>> List<T> maxMultiple(Function<T,K> keyer) {
		List<ComparableKeyValue<K,T>> maxValues = map(v -> ComparableKeyValue.of(keyer.apply(v), v))
																			.maxMultiple();
		return FStream.from(maxValues)
						.map(kv -> kv.value())
						.toList();
	}

	/**
	 * 원소의 자연 순서 기준으로 최대값을 갖는 모든 원소를 리스트로 반환한다.
	 * <p>
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @return	최대값과 동일한 원소들의 리스트.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default List<T> maxMultiple() {
		return maxMultiple((Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}

	/**
	 * 비교자 기준으로 본 스트림에서 가장 작은 원소 하나를 반환한다.
	 * <p>
	 * 동률이 여러 개인 경우 그 중 가장 먼저 등장한 원소가 반환된다. 빈 스트림이면
	 * {@link Optional#empty()}를 반환한다. 메소드 종료 시 스트림은 {@link #closeQuietly()}로 닫힌다.
	 *
	 * @param cmptor	비교자.
	 * @return	최소 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	public default Optional<T> min(Comparator<? super T> cmptor) {
		try {
			T min = null;
			
			FOption<T> next;
			while ( (next = next()).isPresent() ) {
				T v = next.get();
				if ( min == null || cmptor.compare(v, min) < 0 ) {
					min = v;
				}
			}
			return Optional.ofNullable(min);
		}
		finally {
			closeQuietly();
		}
	}
	
	/**
	 * 각 원소에서 추출한 키의 자연 순서 기준으로 최소 원소를 반환한다.
	 *
	 * @param <K>	키 타입.
	 * @param keyer	각 원소에서 비교 키를 추출하는 함수.
	 * @return	최소 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	public default <K extends Comparable<K>> Optional<T> min(Function<? super T,? extends K> keyer) {
		return min((v1,v2) -> keyer.apply(v1).compareTo(keyer.apply(v2)));
	}

	/**
	 * 원소의 자연 순서 기준으로 최소 원소를 반환한다.
	 * <p>
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @return	최소 원소를 감싼 {@link Optional}, 빈 스트림이면 {@link Optional#empty()}.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default Optional<T> min() {
		return min((Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}

	/**
	 * 비교자 기준으로 최소값을 갖는 모든 원소를 리스트로 반환한다 (동률 포함).
	 * <p>
	 * 빈 스트림이면 빈 리스트를 반환한다. 메소드 종료 시 스트림은 {@link #closeQuietly()}로 닫힌다.
	 *
	 * @param cmptor	비교자.
	 * @return	최소값과 동일한 원소들의 리스트.
	 */
	public default List<T> minMultiple(Comparator<? super T> cmptor) {
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
	
	/**
	 * 각 원소에서 추출한 키의 자연 순서 기준으로 최소값을 갖는 모든 원소를 리스트로 반환한다.
	 *
	 * @param <K>	키 타입.
	 * @param keyer	각 원소에서 비교 키를 추출하는 함수.
	 * @return	최소 키와 동일한 키를 갖는 원소들의 리스트.
	 */
	public default <K extends Comparable<K>> List<T> minMultiple(Function<T,K> keyer) {
		List<ComparableKeyValue<K,T>> minValues = map(v -> ComparableKeyValue.of(keyer.apply(v), v))
																			.minMultiple();
		return FStream.from(minValues)
						.map(kv -> kv.value())
						.toList();
	}

	/**
	 * 원소의 자연 순서 기준으로 최소값을 갖는 모든 원소를 리스트로 반환한다.
	 * <p>
	 * 원소 타입은 {@link Comparable}을 구현해야 한다.
	 *
	 * @return	최소값과 동일한 원소들의 리스트.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public default List<T> minMultiple() {
		return minMultiple((Comparator<? super T>)(Comparator)Comparator.naturalOrder());
	}
	
	/**
	 * 스트림의 원소들을 {@code String}으로 변환하여 구분자/시작/종료 문자열로 결합한다.
	 *
	 * @param delim 원소 사이 구분자.
	 * @param begin 결과 문자열의 시작에 붙일 prefix.
	 * @param end   결과 문자열의 끝에 붙일 suffix.
	 * @return 결합된 문자열.
	 */
	public default String join(String delim, String begin, String end) {
		return zipWithIndex()
				.fold(new StringBuilder(begin),
							(b,t) -> (t.index() > 0) ? b.append(delim).append(t.value())
													: b.append(t.value()))
					.append(end)
					.toString();
	}

	/**
	 * {@link #join(String, String, String) join(delim, "", "")}와 동일.
	 *
	 * @param delim 원소 사이 구분자.
	 * @return 결합된 문자열.
	 */
	public default String join(String delim) {
		return join(delim, "", "");
	}

	/**
	 * 문자 구분자를 사용하는 {@link #join(String)}.
	 *
	 * @param delim 원소 사이 구분자 문자.
	 * @return 결합된 문자열.
	 */
	public default String join(char delim) {
		return join(String.valueOf(delim), "", "");
	}

	/**
	 * 주어진 {@link CSV} 설정으로 원소들을 직렬화한다.
	 *
	 * @param csv CSV 직렬화 설정.
	 * @return CSV 직렬화 결과.
	 */
	public default String join(CSV csv) {
		return csv.toString(map(Object::toString));
	}

	/**
	 * 본 스트림이 주어진 prefix 스트림으로 시작하는지 여부를 반환한다.
	 * <p>
	 * 두 스트림에서 동시에 원소를 꺼내며 {@code equals}로 비교하고, prefix가 먼저 소진되면
	 * {@code true}를, 본 스트림이 먼저 소진되거나 비교가 어긋나면 {@code false}를 반환한다.
	 * 메소드 종료 시 본 스트림과 {@code subList} 모두 {@link #closeQuietly()}를 통해 닫힌다.
	 *
	 * @param subList 비교할 prefix 스트림. {@code null}이면 안 된다.
	 * @return prefix가 일치하면 {@code true}.
	 * @throws IllegalArgumentException {@code subList}가 {@code null}인 경우.
	 */
	public default boolean startsWith(FStream<T> subList) {
		Preconditions.checkNotNullArgument(subList, "subList is null");

		try {
			FOption<T> subNext = subList.next();
			FOption<T> next = next();
			while ( subNext.isPresent() && next.isPresent() ) {
				if ( !subNext.get().equals(next.get()) ) {
					return false;
				}

				subNext = subList.next();
				next = next();
			}

			return subNext.isAbsent();
		}
		finally {
			subList.closeQuietly();
			closeQuietly();
		}
	}

	/**
	 * 중복 원소를 제거한 스트림을 반환한다.
	 * <p>
	 * 내부에 {@link HashSet}을 유지하며 새로 등장한 원소만 통과시킨다. 따라서 메모리 사용량은
	 * 결과 원소 수에 비례하며, 입력 스트림이 무한이면 메모리 누수 위험이 있다. 입력이 이미 정렬되어
	 * 인접 중복만 제거하면 충분한 경우 {@link #unique()}를 사용하라.
	 *
	 * @return 중복이 제거된 스트림.
	 */
	public default FStream<T> distinct() {
		Set<T> keys = Sets.newHashSet();
		return filter(keys::add);
	}

	/**
	 * 키 추출 함수가 반환하는 값 기준으로 중복을 제거한 스트림을 반환한다.
	 *
	 * @param <K>   키 타입.
	 * @param keyer 원소에서 키를 추출하는 함수.
	 * @return 키 기준 중복이 제거된 스트림.
	 * @see #distinct()
	 */
	public default <K> FStream<T> distinct(Function<T,K> keyer) {
		Set<K> keys = Sets.newHashSet();
		return this.map(v -> Tuple.of(keyer.apply(v), v))
					.filter(t -> keys.add(t._1))
					.map(t -> t._2);
	}

	/**
	 * <b>인접한</b> 중복 원소만 제거한 스트림을 반환한다 (Unix {@code uniq}와 유사).
	 * <p>
	 * 내부 상태로 직전 원소 하나만 유지하므로 메모리 사용량이 상수이며 무한 스트림에서도 안전하다.
	 * 전역 중복 제거가 필요하면 {@link #distinct()}를 사용하라.
	 *
	 * @return 인접 중복이 제거된 스트림.
	 */
	public default FStream<T> unique() {
		return new UniqueFStream<>(this);
	}

	/**
	 * 키 추출 함수의 결과 기준으로 인접한 중복만 제거한 스트림을 반환한다.
	 *
	 * @param <K>   키 타입.
	 * @param keyer 원소에서 키를 추출하는 함수.
	 * @return 키 기준 인접 중복이 제거된 스트림.
	 * @see #unique()
	 */
	public default <K> FStream<T> unique(Function<? super T,? extends K> keyer) {
		return new UniqueKeyFStream<>(this, keyer);
	}

	/**
	 * 본 스트림이 close될 때 추가로 실행할 작업을 등록한 새 스트림을 반환한다.
	 * <p>
	 * {@code closingTask}는 본 스트림의 close 작업이 완료된 직후 실행된다. 여러 번 등록하면 등록한
	 * 순서대로 실행된다.
	 *
	 * @param closingTask close 시점에 실행할 작업.
	 * @return close hook이 부착된 스트림.
	 * @throws IllegalArgumentException {@code closingTask}가 {@code null}인 경우.
	 */
	public default FStream<T> onClose(Runnable closingTask) {
		Preconditions.checkNotNullArgument(closingTask, "closingTask is null");

		return new FStreams.CloserAttachedStream<>(this, closingTask);
	}

	/**
	 * {@code int[]} 배열로부터 primitive int 스트림을 생성한다.
	 *
	 * @param values 입력 배열.
	 * @return {@link IntFStream} 객체.
	 */
	public static IntFStream of(int[] values) {
		return IntFStream.of(values);
	}

	/**
	 * {@code long[]} 배열로부터 primitive long 스트림을 생성한다.
	 *
	 * @param values 입력 배열.
	 * @return {@link LongFStream} 객체.
	 */
	public static LongFStream of(long[] values) {
		return LongFStream.of(values);
	}

	/**
	 * {@code double[]} 배열로부터 primitive double 스트림을 생성한다.
	 *
	 * @param values 입력 배열.
	 * @return {@link DoubleFStream} 객체.
	 */
	public static DoubleFStream of(double[] values) {
		return DoubleFStream.of(values);
	}

	/**
	 * 각 원소를 int 값으로 매핑한 primitive 스트림을 생성한다.
	 *
	 * @param mapper 원소를 {@link Integer}로 변환하는 함수.
	 * @return {@link IntFStream} 객체.
	 */
	public default IntFStream mapToInt(Function<? super T, Integer> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return new MapToIntStream<>(this, mapper);
	}

	/**
	 * 각 원소를 long 값으로 매핑한 primitive 스트림을 생성한다.
	 *
	 * @param mapper 원소를 {@link Long}으로 변환하는 함수.
	 * @return {@link LongFStream} 객체.
	 */
	public default LongFStream mapToLong(Function<? super T, Long> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return new MapToLongStream<>(this, mapper);
	}

	/**
	 * 각 원소를 float 값으로 매핑한 primitive 스트림을 생성한다.
	 *
	 * @param mapper 원소를 {@link Float}로 변환하는 함수.
	 * @return {@link FloatFStream} 객체.
	 */
	public default FloatFStream mapToFloat(Function<? super T, Float> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return new MapToFloatStream<>(this, mapper);
	}

	/**
	 * 각 원소를 double 값으로 매핑한 primitive 스트림을 생성한다.
	 *
	 * @param mapper 원소를 {@link Double}로 변환하는 함수.
	 * @return {@link DoubleFStream} 객체.
	 */
	public default DoubleFStream mapToDouble(Function<? super T, Double> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return new MapToDoubleStream<>(this, mapper);
	}

	/**
	 * 각 원소를 boolean 값으로 매핑한 primitive 스트림을 생성한다.
	 *
	 * @param mapper 원소를 {@link Boolean}으로 변환하는 함수.
	 * @return {@link BooleanFStream} 객체.
	 */
	public default BooleanFStream mapToBoolean(Function<? super T, Boolean> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");

		return new MapToBooleanStream<>(this, mapper);
	}

	/**
	 * 각 원소를 {@link KeyValue}로 매핑한 후 key-value 전용 {@link KeyValueFStream}을 생성한다.
	 *
	 * @param <K>    키 타입.
	 * @param <V>    값 타입.
	 * @param mapper 원소를 {@link KeyValue}로 변환하는 함수.
	 * @return {@link KeyValueFStream} 객체.
	 */
	public default <K, V> KeyValueFStream<K, V> mapToKeyValue(Function<T, KeyValue<K, V>> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		return KeyValueFStream.from(map(mapper));
	}

	/**
	 * Checked exception을 던질 수 있는 mapper로 {@link KeyValueFStream}을 생성한다.
	 * 매핑 중 예외가 발생하면 그대로 전파된다.
	 *
	 * @param <K>    키 타입.
	 * @param <V>    값 타입.
	 * @param <X>    던질 수 있는 예외 타입.
	 * @param mapper 원소를 {@link KeyValue}로 변환하는 checked 함수.
	 * @return {@link KeyValueFStream} 객체.
	 */
	public default <K, V, X extends Throwable> KeyValueFStream<K, V>
	mapToKeyValueOrThrow(CheckedFunctionX<? super T, ? extends KeyValue<K, V>,X> mapper) {
		Preconditions.checkNotNullArgument(mapper, "mapper is null");
		return KeyValueFStream.from(mapOrThrow(mapper));
	}

	/**
	 * 본 스트림 자체를 인자로 받는 함수를 적용하여 스트림 파이프라인을 합성한다.
	 * <p>
	 * {@code s.lift(f)}는 {@code f.apply(s)}와 동치이며, 메소드 체인을 끊지 않고 외부에서 정의된
	 * 스트림 변환 함수를 끼워 넣는 용도로 사용한다.
	 *
	 * @param <S>        변환 결과 스트림 원소 타입.
	 * @param streamFunc 본 스트림을 받아 새 스트림으로 변환하는 함수.
	 * @return 변환된 스트림.
	 */
	public default <S> FStream<S> lift(Function<FStream<T>, FStream<S>> streamFunc) {
        return streamFunc.apply(this);
    }
}
