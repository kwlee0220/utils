package utils.rx;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;

import utils.async.Execution;
import utils.func.CheckedSupplier;
import utils.func.FOption;
import utils.stream.FStream;
import utils.stream.SuppliableFStream;


/**
 * RxJava3 {@link Observable}/{@link Completable}과 {@link FStream}, {@link Iterator}, {@link Stream},
 * {@link Execution} 사이의 변환 및 파일 관련 {@link Observable} 생성 기능을 제공하는 정적 유틸리티 클래스.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Observables {
	static final Logger s_logger = LoggerFactory.getLogger(Observables.class);
	
	private Observables() {
		throw new AssertionError("Should not be called: class=" + Observables.class);
	}
	
	/**
	 * 주어진 {@link Iterator} 공급자로부터 {@link Observable} 객체를 생성한다.
	 * <p>
	 * 구독할 때마다 {@code iterSuppl}을 호출하여 반복자를 얻고, 구독 스레드에서 모든 원소를 차례로
	 * 발행한 뒤 완료를 통지한다. 반복 중 예외가 발생하면 오류로 통지된다. 반복자가 {@link AutoCloseable}인
	 * 경우 발행이 끝나면 닫는다 (닫기 중 예외는 무시).
	 * <b>주의:</b> 발행 도중 구독이 해제되어 중단되는 경우에는 반복자를 닫지 않는다.
	 * 
	 * @param <T>	데이터 타입.
	 * @param iterSuppl	반복자 공급자.
	 * @return	생성된 {@link Observable} 객체.
	 * @throws NullPointerException	{@code iterSuppl}이 {@code null}인 경우.
	 */
	public static final <T> Observable<T> from(CheckedSupplier<Iterator<? extends T>> iterSuppl) {
		Preconditions.checkNotNull(iterSuppl, "iterSuppl is null");
		
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
	
	/**
	 * 주어진 {@link Stream} 객체를 {@link Observable} 객체로 변환시킨다.
	 * <p>
	 * <b>주의:</b> {@code stream} 자체는 닫지 않으며, {@code Stream}은 한 번만 순회할 수 있으므로
	 * 두 번째 구독은 {@link IllegalStateException} 오류로 실패한다.
	 * 
	 * @param <T>	데이터 타입.
	 * @param stream	변환시킬 {@code Stream} 객체.
	 * @return	변환된 {@link Observable} 객체.
	 */
	public static final <T> Observable<T> from(Stream<T> stream) {
		return Observable.fromIterable(() -> stream.iterator());
	}
	
	/**
	 * {@link FStream} 객체를 {@link Observable} 객체로 변환시킨다.
	 * <p>
	 * 구독 스레드에서 {@code stream}의 모든 데이터를 발행한 뒤 완료를 통지한다. 구독이 해제되면 발행을
	 * 중단한다. 발행이 끝나면 {@code stream}은 닫힌다. 반환된 {@code Observable}은 매 구독마다 같은
	 * {@code stream}을 사용하므로 한 번만 구독하여야 한다.
	 * 
	 * @param <T>	데이터 타입.
	 * @param stream	변환시킬 {@code FStream} 객체
	 * @return	변환된 {@link Observable} 객체.
	 */
	public static <T> Observable<T> from(FStream<T> stream) {
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
	 * <p>
	 * 이 메소드 호출 시점에 즉시 {@code ob}를 구독하며, 수신된 데이터는 길이 128의 내부 큐에
	 * 저장된다. 큐가 가득 차면 데이터를 발행하는 스레드가 블록된다. 반환된 스트림을 닫으면 구독이 해제된다.
	 * 
	 * @param <T> Observable에서 반환하는 데이터 타입
	 * @param ob	입력 {@link Observable} 객체.
	 * @return FStream 객체
	 * @throws NullPointerException	{@code ob}가 {@code null}인 경우.
	 */
	public static <T> FStream<T> from(Observable<? extends T> ob) {
		Preconditions.checkNotNull(ob, "Observable is null");
		
		return new ObservableStream<>(ob);
	}
	
	/**
	 * 주어진 비동기 수행의 진행 상황을 {@link ExecutionProgress} 이벤트로 발행하는
	 * {@link Observable}을 생성한다.
	 * <p>
	 * 수행이 시작되면 {@link ExecutionProgress.Started}를 발행한다. 수행이 성공하면
	 * {@link ExecutionProgress.Completed}를 발행한 뒤 완료를, 실패하면 {@link ExecutionProgress.Failed}를
	 * 발행한 뒤 실패 원인으로 오류를, 취소되면 {@link ExecutionProgress.Cancelled}를 발행한 뒤 완료를 통지한다.
	 * 
	 * @param <T>	수행 결과 값의 타입.
	 * @param exec	관찰 대상 비동기 수행.
	 * @param cancelOnDispose	{@code true}인 경우 구독이 해제되면 {@code exec.cancel(true)}를 호출한다.
	 * @return	진행 상황 이벤트를 발행하는 {@link Observable} 객체.
	 */
	public static <T> Observable<ExecutionProgress<T>>
	observeExecution(Execution<? extends T> exec, boolean cancelOnDispose) {
		return Observable.create(new ExecutionProgressReport<T>(exec, cancelOnDispose));
	}
	
	/**
	 * 결과 값이 없는 비동기 수행의 종료를 {@link Completable}로 변환한다.
	 * <p>
	 * 수행이 성공하면 완료를, 실패하면 실패 원인으로 오류를, 취소되면 {@link CancellationException}으로
	 * 오류를 통지한다. 구독이 해제되어도 {@code exec}는 취소되지 않는다.
	 * 
	 * @param exec	대상 비동기 수행.
	 * @return	수행 종료를 통지하는 {@link Completable} 객체.
	 */
	public static Completable fromVoidExecution(Execution<Void> exec) {
		return Completable.create(emitter -> {
			exec.whenFinishedAsync(r -> {
				if ( !emitter.isDisposed() ) {
					if ( r.isSuccessful() ) {
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
	
	/**
	 * 주어진 텍스트 파일의 각 줄을 발행하는 {@link Observable}을 생성한다.
	 * <p>
	 * 파일은 이 메소드 호출 시점에 {@link Files#lines(Path, Charset)}로 열린다.
	 * <b>주의:</b> 열린 파일 스트림은 닫히지 않으며, 한 번만 구독할 수 있다
	 * ({@link #from(Stream)} 참고).
	 * 
	 * @param path	읽을 파일 경로.
	 * @param cs	파일 문자 인코딩.
	 * @return	파일의 각 줄을 발행하는 {@link Observable} 객체.
	 * @throws IOException	파일 열기에 실패한 경우.
	 */
	public static final Observable<String> lines(Path path, Charset cs) throws IOException {
		return Observables.from(Files.lines(path, cs));
	}
	
	/**
	 * 주어진 UTF-8 텍스트 파일의 각 줄을 발행하는 {@link Observable}을 생성한다.
	 * <p>
	 * 파일은 이 메소드 호출 시점에 {@link Files#lines(Path)}로 열린다.
	 * <b>주의:</b> 열린 파일 스트림은 닫히지 않으며, 한 번만 구독할 수 있다
	 * ({@link #from(Stream)} 참고).
	 * 
	 * @param path	읽을 파일 경로.
	 * @return	파일의 각 줄을 발행하는 {@link Observable} 객체.
	 * @throws IOException	파일 열기에 실패한 경우.
	 */
	public static final Observable<String> lines(Path path) throws IOException {
		return Observables.from(Files.lines(path));
	}
	
	private static class ObservableStream<T> implements FStream<T> {
		private final static int DEFAULT_LENGTH = 128;
		
		private final Observable<? extends T> m_ob;
		private final Disposable m_subscription;
		private final SuppliableFStream<T> m_output;
		
		ObservableStream(Observable<? extends T> ob, int queueLength) {
			Preconditions.checkArgument(queueLength > 0, "queueLength > 0, but: %s", queueLength);
			Preconditions.checkNotNull(ob, "Observable is null");
			
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
	
	/**
	 * 주어진 디렉토리 아래의 모든 파일/디렉토리 경로를 깊이 제한 없이 발행하는 {@link Observable}을 생성한다.
	 * 
	 * @param start	탐색 시작 경로.
	 * @param options	탐색 옵션.
	 * @return	경로를 발행하는 {@link Observable} 객체.
	 * @see #walk(Path, int, FileVisitOption...)
	 */
	public static final Observable<Path> walk(Path start, FileVisitOption... options) {
		return walk(start, Integer.MAX_VALUE, options);
	}
	/**
	 * 주어진 디렉토리 아래의 파일/디렉토리 경로를 {@link Files#walk(Path, int, FileVisitOption...)}
	 * 순서로 발행하는 {@link Observable}을 생성한다.
	 * <p>
	 * 탐색은 구독할 때마다 새로 수행되며, 탐색 중 발생한 오류는 오류로 통지된다.
	 * 
	 * @param start	탐색 시작 경로 ({@code start} 자신도 발행된다).
	 * @param maxDepth	최대 탐색 깊이.
	 * @param options	탐색 옵션.
	 * @return	경로를 발행하는 {@link Observable} 객체.
	 */
	public static final Observable<Path> walk(Path start, int maxDepth, FileVisitOption... options) {
		return from(() -> Files.walk(start, maxDepth, options).iterator());
	}
	
	/**
	 * 주어진 디렉토리에서 발생하는 파일 시스템 이벤트를 발행하는 {@link Observable}을 생성한다.
	 * <p>
	 * 구독 스레드에서 {@link WatchService}를 생성하여 이벤트를 기다리며 무한히 발행하므로 구독 스레드가
	 * 블록된다. 감시 서비스가 닫히거나 스레드가 인터럽트되면 완료를, 그 밖의 예외는 오류를 통지한다.
	 * 구독 해제는 다음 이벤트가 도착한 시점에야 감지되어 종료된다.
	 * 
	 * @param fs	감시 서비스를 생성할 파일 시스템.
	 * @param dir	감시할 디렉토리.
	 * @param events	감시할 이벤트 종류.
	 * @return	파일 시스템 이벤트를 발행하는 {@link Observable} 객체.
	 */
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
	
	/**
	 * 주어진 파일에 대한 파일 시스템 이벤트를 발행하는 {@link Observable}을 생성한다.
	 * <p>
	 * {@code file}의 상위 디렉토리를 {@link #watchDir}로 감시하고 {@code file}에 해당하는 이벤트만 걸러낸다.
	 * <b>주의:</b> 이벤트의 {@code context()}는 디렉토리 기준 상대 경로인데 이를 그대로
	 * {@link Files#isSameFile}로 비교하므로, 상대 경로가 현재 작업 디렉토리 기준으로 해석되어 기대와 다르게
	 * 걸러지거나 오류({@link IOException})가 발생할 수 있다.
	 * 
	 * @param fs	감시 서비스를 생성할 파일 시스템.
	 * @param file	감시할 파일.
	 * @param events	감시할 이벤트 종류.
	 * @return	파일 시스템 이벤트를 발행하는 {@link Observable} 객체.
	 */
	public static Observable<WatchEvent<?>> watchFile(FileSystem fs, Path file,
														WatchEvent.Kind<?>... events) {
		return watchDir(fs, file.getParent(), events)
				.filter(ev -> Files.isSameFile(file, (Path)ev.context()));
	}
}
