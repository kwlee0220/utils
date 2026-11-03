package utils.rx;

import java.util.Iterator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;

import utils.async.Execution;
import utils.func.CheckedSupplier;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;


/**
 * RxJava3 {@link Flowable}과 {@link FStream}, {@link Iterator}, {@link Stream},
 * {@link Execution} 사이의 변환 기능을 제공하는 정적 유틸리티 클래스.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Flowables {
	static final Logger s_logger = LoggerFactory.getLogger(Flowables.class);
	
	private Flowables() {
		throw new AssertionError("Should not be called: class=" + Flowables.class);
	}
	
	/**
	 * {@link FStream} 객체를 {@link Flowable} 객체로 변환시킨다.
	 * <p>
	 * 구독 요청에 따라 {@code stream}에서 데이터를 하나씩 읽어 발행하고, 스트림이 끝나면 완료를
	 * 통지한다. 구독이 종료(완료/오류/취소)되면 {@code stream}은 조용히 닫힌다.
	 * 반환된 {@code Flowable}은 매 구독마다 같은 {@code stream}을 사용하므로 한 번만 구독하여야 한다.
	 * 
	 * @param <T>	데이터 타입.
	 * @param stream	변환시킬 {@code FStream} 객체
	 * @return	변환된 {@link Flowable} 객체.
	 */
	public static <T> Flowable<T> from(FStream<? extends T> stream) {
		Preconditions.checkNotNull(stream, "stream is null");
		
		return Flowable.generate(
					() -> stream,
					(s,emitter)-> {
						s.next()
							.ifPresent(emitter::onNext)
							.ifAbsent(emitter::onComplete);
					},
					FStream::closeQuietly);
	}
	
	/**
	 * 주어진 {@link Iterator} 공급자로부터 {@link Flowable} 객체를 생성한다.
	 * <p>
	 * 구독할 때마다 {@code iterSuppl}을 호출하여 반복자를 얻고, 구독 요청에 따라 원소를 하나씩 발행한다.
	 * 구독이 종료되면 반복자가 {@link AutoCloseable}인 경우 닫는다
	 * (닫기 중 발생한 예외는 비검사 예외로 감싸져 전달된다).
	 * 
	 * @param <T>	데이터 타입.
	 * @param iterSuppl	반복자 공급자.
	 * @return	생성된 {@link Flowable} 객체.
	 * @throws NullPointerException	{@code iterSuppl}이 {@code null}인 경우.
	 */
	public static <T> Flowable<T> from(CheckedSupplier<Iterator<? extends T>> iterSuppl) {
		Preconditions.checkNotNull(iterSuppl, "iterSuppl is null");
		
		return Flowable.generate(
					() -> iterSuppl.get(),
					(iter, emitter)-> {
						if ( iter.hasNext() ) {
							emitter.onNext(iter.next());
						}
						else {
							emitter.onComplete();
						}
					},
					iter -> {
						if ( iter instanceof AutoCloseable ) {
							Unchecked.runOrRTE(() -> ((AutoCloseable)iter).close());
						}
					});
	}
	
	/**
	 * 주어진 {@link Stream} 객체를 {@link Flowable} 객체로 변환시킨다.
	 * <p>
	 * 구독 시 {@code stream.iterator()}로 원소를 읽어 발행한다.
	 * <b>주의:</b> {@code stream} 자체는 닫지 않으며, {@code Stream}은 한 번만 순회할 수 있으므로
	 * 두 번째 구독은 {@link IllegalStateException}으로 실패한다.
	 * 
	 * @param <T>	데이터 타입.
	 * @param stream	변환시킬 {@code Stream} 객체.
	 * @return	변환된 {@link Flowable} 객체.
	 */
	public static <T> Flowable<T> from(Stream<? extends T> stream) {
		return from(() -> stream.iterator());
	}
	
	/**
	 * 주어진 {@link Flowable}객체로부터 FStream 객체를 생성한다.
	 * <p>
	 * 이 메소드 호출 시점에 즉시 {@code flowable}을 구독하며, 수신된 데이터는 길이 128의 내부 큐에
	 * 저장된다. 큐가 가득 차면 데이터를 발행하는 스레드가 블록된다. 반환된 스트림을 닫으면 구독이 해제된다.
	 * 
	 * @param <T> Flowable에서 반환하는 데이터 타입
	 * @param flowable	입력 {@link Flowable} 객체.
	 * @return FStream 객체
	 * @throws NullPointerException	{@code flowable}이 {@code null}인 경우.
	 */
	public static <T> FStream<T> from(Flowable<? extends T> flowable) {
		Preconditions.checkNotNull(flowable, "flowable is null");
		
		return new FlowableFStream<>(flowable);
	}
	
	static class FlowableFStream<T> implements FStream<T> {
		private final static int DEFAULT_LENGTH = 128;
		
		private final Flowable<? extends T> m_flowable;
		private final Disposable m_subscription;
		private final SuppliableFStream<T> m_output;
		
		FlowableFStream(Flowable<? extends T> flowable, int queueLength) {
			Preconditions.checkNotNull(flowable, "flowable is null");
			Preconditions.checkArgument(queueLength > 0, "queueLength > 0, but: %s", queueLength);
			
			m_flowable = flowable;
			m_output = new SuppliableFStream<>(queueLength);
			m_subscription = flowable.subscribe(m_output::supply,
												m_output::endOfSupply,
												m_output::endOfSupply);
		}
		
		FlowableFStream(Flowable<? extends T> flowable) {
			this(flowable, DEFAULT_LENGTH);
		}

		@Override
		public void close() throws Exception {
			m_subscription.dispose();
			m_output.close();
		}

		@Override
		public FOption<T> next() {
			return m_output.next();
		}
		
		@Override
		public String toString() {
			return String.format("%s[%s]", getClass().getSimpleName(), m_flowable);
		}
	}
	
	/**
	 * 주어진 비동기 수행의 진행 상황을 {@link ExecutionProgress} 이벤트로 발행하는
	 * {@link Flowable}을 생성한다 (배압 전략: {@link BackpressureStrategy#BUFFER}).
	 * <p>
	 * 수행이 시작되면 {@link ExecutionProgress.Started}를 발행한다. 수행이 성공하면
	 * {@link ExecutionProgress.Completed}를 발행한 뒤 완료를, 실패하면 {@link ExecutionProgress.Failed}를
	 * 발행한 뒤 실패 원인으로 오류를, 취소되면 {@link ExecutionProgress.Cancelled}를 발행한 뒤 완료를 통지한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @param exec	관찰 대상 비동기 수행.
	 * @param cancelOnDispose	{@code true}인 경우 구독이 취소되면 {@code exec.cancel(true)}를 호출한다.
	 * @return	진행 상황 이벤트를 발행하는 {@link Flowable} 객체.
	 */
	public static <T> Flowable<ExecutionProgress<T>>
	observeExecution(Execution<? extends T> exec, boolean cancelOnDispose) {
		return Flowable.create(new ExecutionProgressReport<T>(exec, cancelOnDispose),
								BackpressureStrategy.BUFFER);
	}
	
	private static class ExecutionProgressReport<T> implements FlowableOnSubscribe<ExecutionProgress<T>> {
		private final Execution<? extends T> m_exec;
		private final boolean m_cancelOnDispose;
		
		ExecutionProgressReport(Execution<? extends T> exec, boolean cancelOnDispose) {
			m_exec = exec;
			m_cancelOnDispose = cancelOnDispose;
		}

		@Override
		public void subscribe(FlowableEmitter<ExecutionProgress<T>> emitter) throws Exception {
			if ( m_cancelOnDispose ) {
				emitter.setCancellable(() -> m_exec.cancel(true));
			}
			
			m_exec.whenStartedAsync(() -> {
				if ( !emitter.isCancelled() ) {
					emitter.onNext(new ExecutionProgress.Started<>());
				}
			});
			m_exec.whenFinishedAsync(r -> {
				if ( !emitter.isCancelled() ) {
					if ( r.isSuccessful() ) {
						emitter.onNext(new ExecutionProgress.Completed<T>(r.getOrNull()));
						emitter.onComplete();
					}
					else if ( r.isFailed() ) {
						emitter.onNext(new ExecutionProgress.Failed<>(r.getCause()));
						emitter.onError(r.getCause());
					}
					else if ( r.isNone() ) {
						emitter.onNext(new ExecutionProgress.Cancelled<>());
						emitter.onComplete();
					}
				}
			});
		}
	}

}
