package utils.async.op;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import utils.Preconditions;
import utils.async.AbstractAsyncExecution;
import utils.async.CompletableFutureAsyncExecution;
import utils.async.EventDrivenExecution;
import utils.async.Execution;
import utils.async.Executions;
import utils.async.StartableExecution;
import utils.func.CheckedSupplier;
import utils.func.Result;
import utils.stream.FStream;


/**
 * {@link StartableExecution}을 생성하는 정적 팩토리 모음.
 * <p>
 * 즉시 종료/지연/합성/타임아웃/누적 등 {@code utils.async.op} 패키지의 combinator들을
 * 본 클래스의 정적 메소드를 통해 일관된 방식으로 생성한다.
 * <p>
 * 카테고리별 메소드:
 * <ul>
 *   <li><b>즉시 종료</b> — {@link #nop(Object)}, {@link #nop()}, {@link #throwAsync(Throwable)},
 *       {@link #cancelAsync()}.</li>
 *   <li><b>지연/대기</b> — {@link #idle(Object, Duration)},
 *       {@link #delay(Execution, Duration)},
 *       {@link #timed(StartableExecution, Duration)}.</li>
 *   <li><b>합성</b> — {@link #sequential(FStream)},
 *       {@link #concurrent(StartableExecution...)},
 *       {@link #backgrounded(StartableExecution, StartableExecution)},
 *       {@link #fold(FStream, Supplier, BiFunction)}.</li>
 *   <li><b>스케줄러</b> — {@link #getScheduler()}, {@link #createScheduler()}.</li>
 * </ul>
 * 시간 관련 메소드의 executor 정책:
 * <ul>
 *   <li>{@link #idle(Object, Duration)} / {@link #idle(Duration)} /
 *       {@link #delay(Execution, Duration)}는
 *       {@link CompletableFutureAsyncExecution#getDelayedExecutor}를 사용한다.
 *       {@link #delay(Execution, Duration, Executor)}는 호출자가 지정한 {@link Executor}를 사용한다.</li>
 *   <li>{@link #timed(StartableExecution, Duration)}는 공유
 *       {@link ListeningScheduledExecutorService}({@link #getScheduler()})를 사용한다.
 *       해당 스케줄러는 최초 호출 시 lazy하게 생성된다.</li>
 *   <li>{@link #throwAsync(Throwable)} / {@link #cancelAsync()}는
 *       {@link CompletableFuture#runAsync(Runnable)}로 기본 실행자(공용 ForkJoin pool)에
 *       위임한다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class AsyncExecutions {
	static final Logger s_logger = LoggerFactory.getLogger(AsyncExecutions.class);

	private AsyncExecutions() {
		throw new AssertionError("Should not be called: class=" + AsyncExecutions.class);
	}

	/**
	 * 즉시 주어진 결과로 완료되는 비동기 실행을 생성한다.
	 * <p>
	 * {@code start()} 호출 시 동기적으로 {@code COMPLETED} 상태로 전이된다.
	 *
	 * @param <T>    결과 타입
	 * @param result 완료 결과 값
	 * @return 즉시 완료되는 {@link AbstractAsyncExecution}
	 */
	public static <T> AbstractAsyncExecution<T> nop(T result) {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				if ( !notifyStarted() ) {
					return;
				}
				if ( notifyCompleted(result) ) {
					return;
				}
				if ( notifyCancelled() ) {
					return;
				}
			}
		};
	}

	/**
	 * 즉시 {@code null}로 완료되는 비동기 실행을 생성한다.
	 *
	 * @return 즉시 완료되는 {@link AbstractAsyncExecution}
	 * @see #nop(Object)
	 */
	public static AbstractAsyncExecution<Void> nop() {
		return nop(null);
	}

	/**
	 * 비동기로 주어진 예외와 함께 실패하는 실행을 생성한다.
	 * <p>
	 * {@code start()} 호출 후 별도 스레드({@link CompletableFuture#runAsync})에서
	 * {@code notifyFailed(cause)}을 호출하여 {@code FAILED} 상태로 전이된다.
	 *
	 * @param <T>   결과 타입 (실제 값은 발생하지 않음)
	 * @param cause 실패 원인
	 * @return 비동기로 실패하는 {@link AbstractAsyncExecution}
	 */
	public static <T> AbstractAsyncExecution<T> throwAsync(Throwable cause) {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				if ( !notifyStarted() ) {
					return;
				}
				CompletableFuture.runAsync(() -> notifyFailed(cause));
			}
		};
	}

	/**
	 * 비동기로 취소되는 실행을 생성한다.
	 * <p>
	 * {@code start()} 호출 후 별도 스레드에서 {@code notifyCancelled}을 호출하여
	 * {@code CANCELLED} 상태로 전이된다.
	 *
	 * @param <T> 결과 타입 (실제 값은 발생하지 않음)
	 * @return 비동기로 취소되는 {@link AbstractAsyncExecution}
	 */
	public static <T> AbstractAsyncExecution<T> cancelAsync() {
		return new AbstractAsyncExecution<T>() {
			@Override
			public void start() {
				notifyStarted();
				CompletableFuture.runAsync(this::notifyCancelled);
			}
		};
	}

	/**
	 * 주어진 시간만큼 대기한 후 주어진 결과로 완료되는 실행을 생성한다.
	 * <p>
	 * {@link CompletableFutureAsyncExecution#getDelayedExecutor}를 사용하여
	 * 지연 후 결과를 반환하는 {@link CompletableFuture}로 구현된다.
	 *
	 * @param <T>     결과 타입
	 * @param result  완료 결과 값
	 * @param timeout 대기 시간
	 * @return 대기 후 완료되는 {@link StartableExecution}
	 */
	public static <T> StartableExecution<T> idle(T result, Duration timeout) {
		CheckedSupplier<T> supplier = () -> {
			return Executions.getExecutor()
							.schedule(() -> result, timeout.toMillis(), TimeUnit.MILLISECONDS)
							.get();
		};
		return CompletableFutureAsyncExecution.supplyAsync(supplier.toSneakyThrowSupplier());
	}

	/**
	 * 주어진 시간만큼 대기한 후 {@code null}로 완료되는 실행을 생성한다.
	 *
	 * @param timeout 대기 시간
	 * @return 대기 후 완료되는 {@link StartableExecution}
	 * @see #idle(Object, Duration)
	 */
	public static StartableExecution<Void> idle(Duration timeout) {
		return idle((Void)null, timeout);
	}

	/**
	 * 주어진 실행들을 순차적으로 수행하는 합성 실행을 생성한다.
	 * <p>
	 * 앞 실행이 완료된 후 다음 실행을 시작하며, 어느 하나라도 실패/취소되면 전체가
	 * 실패/취소된다.
	 *
	 * @param <T>      마지막 실행의 결과 타입
	 * @param sequence 순차 실행할 실행 시퀀스
	 * @return {@link SequentialAsyncExecution}
	 */
	public static SequentialAsyncExecution sequential(FStream<StartableExecution<?>> sequence) {
		return new SequentialAsyncExecution(sequence);
	}

	/**
	 * 주어진 실행들을 순차적으로 수행하는 합성 실행을 생성한다.
	 *
	 * @param <T>  마지막 실행의 결과 타입
	 * @param elms 순차 실행할 실행 리스트
	 * @return {@link SequentialAsyncExecution}
	 * @see #sequential(FStream)
	 */
	public static SequentialAsyncExecution sequential(List<StartableExecution<?>> elms) {
		return new SequentialAsyncExecution(FStream.from(elms));
	}

	/**
	 * 주어진 실행들을 순차적으로 수행하는 합성 실행을 생성한다.
	 *
	 * @param <T>      마지막 실행의 결과 타입
	 * @param sequence 순차 실행할 실행 가변인자
	 * @return {@link SequentialAsyncExecution}
	 * @see #sequential(FStream)
	 */
	@SafeVarargs
	public static SequentialAsyncExecution sequential(StartableExecution<?>... sequence) {
		return new SequentialAsyncExecution(FStream.of(sequence));
	}

	/**
	 * 주어진 실행들을 동시에 수행하는 합성 실행을 생성한다.
	 * <p>
	 * 모든 실행이 완료되면 전체가 완료되며, 어느 하나라도 실패하면 전체가 실패한다.
	 *
	 * @param elements 동시에 수행할 실행들
	 * @return {@link ConcurrentAsyncExecution}
	 */
	public static ConcurrentAsyncExecution concurrent(StartableExecution<?>... elements) {
		return new ConcurrentAsyncExecution(elements);
	}

	/**
	 * 전경(foreground) 실행과 배경(background) 실행을 묶은 합성 실행을 생성한다.
	 * <p>
	 * 두 실행을 동시에 시작하지만 합성 실행의 결과는 {@code fg}의 결과를 따른다.
	 * {@code fg}가 종료되면 {@code bg}는 자동으로 취소된다.
	 *
	 * @param <T> {@code fg}의 결과 타입
	 * @param fg  결과를 결정하는 전경 실행
	 * @param bg  보조적으로 수행되는 배경 실행
	 * @return {@link BackgroundedAsyncExecution}
	 */
	public static <T> BackgroundedAsyncExecution<T> backgrounded(StartableExecution<T> fg,
																StartableExecution<?> bg) {
		return new BackgroundedAsyncExecution<>(fg, bg);
	}

	/**
	 * 본 실행에 시작 지연을 적용한 새 합성 실행을 생성한다.
	 * <p>
	 * 반환된 실행의 {@code start()}가 호출되면 즉시 시작되지 않고, 지정된 {@code delay}가 경과한 뒤
	 * 본 실행이 시작된다. 본 실행이 이미 시작된 상태라면 지연 후 결과만 기다린다.
	 *
	 * @param <T>   결과 타입
	 * @param target 지연 시작할 대상 실행.
	 * @param delay 시작 전 적용할 지연 시간 (음수 불가).
	 * @return 지연 시작을 표현하는 {@link DelayedAsyncExecution}.
	 */
	public static <T> DelayedAsyncExecution<T> delay(Execution<T> target, Duration delay) {
		return new DelayedAsyncExecution<>(target, delay, createDelayedExecutor(delay));
	}

	/**
	 * 외부에서 제공된 지연 executor를 사용해 {@link DelayedAsyncExecution}을 생성한다.
	 *
	 * @param <T>           결과 타입.
	 * @param target        지연 시작할 대상 실행.
	 * @param delay         지연 시간 (음수 불가).
	 * @param delayExecutor 지연 후 작업을 디스패치할 {@link Executor}.
	 * @return 새 {@link DelayedAsyncExecution}.
	 */
	public static <T> DelayedAsyncExecution<T> delay(Execution<? extends T> target, Duration delay,
													Executor delayExecutor) {
		return new DelayedAsyncExecution<>(target, delay, delayExecutor);
	}
	
	public static DelayedAsyncExecution<Void> delay(Runnable runnable, Duration delay) {
		var exec = CompletableFutureAsyncExecution.runAsync(runnable);
		return delay(exec, delay, createDelayedExecutor(delay));
	}

	private static Executor createDelayedExecutor(Duration delay) {
		Preconditions.checkNotNullArgument(delay, "delay is null");
		
		return CompletableFutureAsyncExecution.getDelayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
	}

	/**
	 * 주어진 실행에 타임아웃을 적용한 합성 실행을 생성한다.
	 * <p>
	 * 타임아웃 내에 완료되지 않으면 {@code target}이 cancel되고 합성 실행은
	 * 타임아웃 실패로 종료된다.
	 *
	 * @param <T>       결과 타입
	 * @param target    타임아웃을 적용할 실행
	 * @param timeout   허용 시간
	 * @param scheduler 타임아웃 트리거에 사용할 스케줄러
	 * @return {@link TimedAsyncExecution}
	 */
	public static <T> TimedAsyncExecution<T> timed(StartableExecution<T> target, Duration timeout,
													ScheduledExecutorService scheduler) {
		return new TimedAsyncExecution<>(target, timeout, scheduler);
	}

	/**
	 * 주어진 실행에 타임아웃을 적용한 합성 실행을 생성한다. 타임아웃 트리거에는 공유
	 * 스케줄러({@link #getScheduler()})를 사용한다.
	 *
	 * @param <T>     결과 타입
	 * @param target  타임아웃을 적용할 실행
	 * @param timeout 허용 시간
	 * @return {@link TimedAsyncExecution}
	 * @see #timed(StartableExecution, Duration, ScheduledExecutorService)
	 */
	public static <T> TimedAsyncExecution<T> timed(StartableExecution<T> target, Duration timeout) {
		return new TimedAsyncExecution<>(target, timeout, Executions.getExecutor());
	}
	
	/**
	 * {@link FlatMapAsyncExecution}을 생성하는 정적 팩토리 메소드.
	 *
	 * @param <T>    leader 결과 타입.
	 * @param <S>    follower(=본 실행) 결과 타입.
	 * @param leader 선행 실행. 호출자가 외부에서 시작시켜야 한다.
	 * @param chain  leader의 종료 결과를 follower 실행으로 변환하는 함수.
	 * @return 두 실행을 직렬로 잇는 합성 {@link Execution}.
	 */
	public static <T,S> Execution<S> flatMap(Execution<T> leader,
											Function<? super Result<T>, ? extends EventDrivenExecution<S>> chain) {
		return new FlatMapAsyncExecution<>(leader, chain);
	}

	/**
	 * 순차 실행들의 결과를 누적(fold)하는 합성 실행을 생성한다.
	 * <p>
	 * {@code initSupplier}로부터 얻은 초기값에 시퀀스의 각 결과를 {@code folder}로 누적하여
	 * 최종 누적값을 결과로 반환한다.
	 *
	 * @param <T>          누적값 타입
	 * @param <S>          시퀀스 항목 결과 타입
	 * @param seq          fold할 실행 시퀀스
	 * @param initSupplier 초기 누적값 supplier
	 * @param folder       누적 함수
	 * @return {@link FoldedAsyncExecution}
	 */
	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<S>> seq,
														Supplier<? extends T> initSupplier,
														BiFunction<T,S,T> folder) {
		return new FoldedAsyncExecution<>(seq, initSupplier, folder);
	}

	/**
	 * 순차 실행들의 결과를 주어진 초기값으로부터 누적(fold)하는 합성 실행을 생성한다.
	 *
	 * @param <T>    누적값 타입
	 * @param <S>    시퀀스 항목 결과 타입
	 * @param seq    fold할 실행 시퀀스
	 * @param init   초기 누적값
	 * @param folder 누적 함수
	 * @return {@link FoldedAsyncExecution}
	 * @see #fold(FStream, Supplier, BiFunction)
	 */
	public static <T,S> FoldedAsyncExecution<T,S> fold(FStream<StartableExecution<S>> seq,
														T init, BiFunction<T,S,T> folder) {
		return new FoldedAsyncExecution<>(seq, () -> init, folder);
	}

	/**
	 * 새로운 {@link ListeningScheduledExecutorService}를 생성한다.
	 * <p>
	 * {@link Executions#getExecutor()}를 {@link MoreExecutors#listeningDecorator}로 래핑한다.
	 * 일반적으로 신규 인스턴스가 필요하지 않다면 {@link #getScheduler()}로 공유 인스턴스를
	 * 사용한다.
	 *
	 * @return 새로 생성된 스케줄러
	 */
	public static ListeningScheduledExecutorService createScheduler() {
		return MoreExecutors.listeningDecorator(Executions.getExecutor());
	}
}
