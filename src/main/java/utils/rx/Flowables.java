package utils.rx;

import static utils.Utilities.checkNotNullArgument;

import java.util.Iterator;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Utilities;
import utils.async.Execution;
import utils.async.ExecutionProgress;
import utils.func.CheckedSupplier;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;


/**
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
	 * 
	 * @param <T>
	 * @param stream	변환시킬 {@code FStream} 객체
	 * @return	변환된 {@link Flowable} 객체.
	 */
	public static <T> Flowable<T> from(@Nonnull FStream<? extends T> stream) {
		checkNotNullArgument(stream);
		
		return Flowable.generate(
					() -> stream,
					(s,emitter)-> {
						s.next()
							.ifPresent(emitter::onNext)
							.ifAbsent(emitter::onComplete);
					},
					FStream::closeQuietly);
	}
	
	public static <T> Flowable<T> from(@Nonnull CheckedSupplier<Iterator<? extends T>> iterSuppl) {
		checkNotNullArgument(iterSuppl);
		
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
	
	public static <T> Flowable<T> from(@Nonnull Stream<? extends T> stream) {
		return from(() -> stream.iterator());
	}
	
	/**
	 * 주어진 {@link Flowable}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Flowable에서 반환하는 데이터 타입
	 * @param flowable	입력 {@link Flowable} 객체.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(@Nonnull Flowable<? extends T> flowable) {
		checkNotNullArgument(flowable, "flowable is null");
		
		return new FlowableFStream<>(flowable);
	}
	
	static class FlowableFStream<T> implements FStream<T> {
		private final static int DEFAULT_LENGTH = 128;
		
		private final Flowable<? extends T> m_flowable;
		private final Disposable m_subscription;
		private final SuppliableFStream<T> m_output;
		
		FlowableFStream(@Nonnull Flowable<? extends T> flowable, int queueLength) {
			checkNotNullArgument(flowable);
			Utilities.checkArgument(queueLength > 0, "queueLength > 0, but: " + queueLength);
			
			m_flowable = flowable;
			m_output = new SuppliableFStream<>(queueLength);
			m_subscription = flowable.subscribe(m_output::supply,
												m_output::endOfSupply,
												m_output::endOfSupply);
		}
		
		FlowableFStream(@Nonnull Flowable<? extends T> flowable) {
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
		public void subscribe(@Nonnull FlowableEmitter<ExecutionProgress<T>> emitter) throws Exception {
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
