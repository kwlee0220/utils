package utils.rx;

import static utils.Utilities.checkNotNullArgument;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Utilities;
import utils.async.Execution;
import utils.async.ExecutionProgress;
import utils.func.CheckedSupplier;
import utils.func.FOption;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Observables {
	static final Logger s_logger = LoggerFactory.getLogger(Observables.class);
	
	private Observables() {
		throw new AssertionError("Should not be called: class=" + Observables.class);
	}
	
	public static final <T> Observable<T> from(@Nonnull CheckedSupplier<Iterator<? extends T>> iterSuppl) {
		checkNotNullArgument(iterSuppl);
		
		return Observable.create(emitter -> {
			Iterator<? extends T> iter = null;
			try {
				iter = iterSuppl.get();
				while ( iter.hasNext() ) {
					if ( emitter.isDisposed() ) {
						return;
					}
					emitter.onNext(iter.next());
				}
				emitter.onComplete();
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
			if ( iter != null && iter instanceof AutoCloseable ) {
				try {
					((AutoCloseable)iter).close();
				}
				catch ( Throwable ignored ) { }
			}
		});
	}
	
	public static final <T> Observable<T> from(@Nonnull Stream<T> stream) {
		return Observable.fromIterable(() -> stream.iterator());
	}
	
	/**
	 * {@link FStream} 객체를 {@link Observable} 객체로 변환시킨다.
	 * 
	 * @param <T>
	 * @param stream	변환시킬 {@code FStream} 객체
	 * @return	변환된 {@link Observable} 객체.
	 */
	public static <T> Observable<T> from(@Nonnull FStream<T> stream) {
		return Observable.create(emitter -> {
			try {
				stream.takeWhile(v -> !emitter.isDisposed())
					  .forEach(emitter::onNext);
				if ( !emitter.isDisposed() ) {
					emitter.onComplete();
				}
			}
			catch ( Throwable e ) {
				emitter.onError(e);
			}
		});
	}
	
	/**
	 * 주어진 {@link Observable}객체로부터 FStream 객체를 생성한다.
	 * 
	 * @param <T> Observable에서 반환하는 데이터 타입
	 * @param ob	입력 {@link Observable} 객체.
	 * @return FStream 객체
	 */
	public static <T> FStream<T> from(Observable<? extends T> ob) {
		checkNotNullArgument(ob, "Observable is null");
		
		return new ObservableStream<>(ob);
	}
	
	public static <T> Observable<ExecutionProgress<T>>
	observeExecution(Execution<? extends T> exec, boolean cancelOnDispose) {
		return Observable.create(new ExecutionProgressReport<T>(exec, cancelOnDispose));
	}
	
	public static Completable fromVoidExecution(Execution<Void> exec) {
		return Completable.create(emitter -> {
			exec.whenFinished(r -> {
				if ( !emitter.isDisposed() ) {
					if ( r.isCompleted() ) {
						emitter.onComplete();
					}
					else if ( r.isFailed() ) {
						emitter.onError(r.getCause());
					}
					else if ( r.isCancelled() ) {
						emitter.onError(new CancellationException());
					}
				}
			});
		});
	}
	
	public static final Observable<String> lines(Path path, Charset cs) throws IOException {
		return Observables.from(Files.lines(path, cs));
	}
	
	public static final Observable<String> lines(Path path) throws IOException {
		return Observables.from(Files.lines(path));
	}
	
	private static class ObservableStream<T> implements FStream<T> {
		private final static int DEFAULT_LENGTH = 128;
		
		private final Observable<? extends T> m_ob;
		private final Disposable m_subscription;
		private final SuppliableFStream<T> m_output;
		
		ObservableStream(Observable<? extends T> ob, int queueLength) {
			Utilities.checkArgument(queueLength > 0, "queueLength > 0, but: " + queueLength);
			
			m_ob = ob;
			m_output = new SuppliableFStream<>(queueLength);
			m_subscription = ob.subscribe(m_output::supply,
											m_output::endOfSupply,
											m_output::endOfSupply);
		}
		
		ObservableStream(Observable<? extends T> ob) {
			this(ob, DEFAULT_LENGTH);
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
			return String.format("%s[%s]", getClass().getSimpleName(), m_ob);
		}
	}
	
	public static final Observable<Path> walk(Path start, FileVisitOption... options) {
		return walk(start, Integer.MAX_VALUE, options);
	}
	public static final Observable<Path> walk(Path start, int maxDepth, FileVisitOption... options) {
		return from(() -> Files.walk(start, maxDepth, options).iterator());
	}
	
	public static Observable<WatchEvent<?>> watchDir(FileSystem fs, Path dir,
													WatchEvent.Kind<?>... events) {
		return Observable.create(new ObservableOnSubscribe<WatchEvent<?>>() {
			@Override
			public void subscribe(ObservableEmitter<WatchEvent<?>> emitter) throws Exception {
				try ( WatchService watch = fs.newWatchService() ) {
					dir.register(watch, events);
					
			        WatchKey key;
					while ( true ) {
						key = watch.take();
						if ( emitter.isDisposed() ) {
							return;
						}
						
						for ( WatchEvent<?> ev : key.pollEvents() ) {
							emitter.onNext(ev);
						}
						key.reset();
					}
				}
				catch ( ClosedWatchServiceException | InterruptedException e ) {
					emitter.onComplete();
				}
				catch ( Throwable e ) {
					emitter.onError(e);
				}
			}
		});
	}
	
	public static Observable<WatchEvent<?>> watchFile(FileSystem fs, Path file,
														WatchEvent.Kind<?>... events) {
		return watchDir(fs, file.getParent(), events)
				.filter(ev -> Files.isSameFile(file, (Path)ev.context()));
	}
}
