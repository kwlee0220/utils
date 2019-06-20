package utils.func;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import utils.Throwables;
import utils.Utilities;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Unchecked {
	public static <T> FailureHandler<T> ignore() {
		return new IgnoreFailure<>();
	}
	
	public static <T> CountingErrorHandler<T> count() {
		return new CountingErrorHandler<>();
	}
	
	public static <T> CollectingErrorHandler<T> collect() {
		return new CollectingErrorHandler<>();
	}
	
	public static <T> CollectingErrorHandler<T> collect(List<FailureCase<T>> store) {
		return new CollectingErrorHandler<>(store);
	}
	
	public static <T> FailurePublisher<T> publish() {
		return new FailurePublisher<>();
	}
	
// ******************************************************************************
// ****************************** Runnable **************************************
// ******************************************************************************
	
	public static <T> Runnable liftIE(CheckedRunnable checked) {
		Utilities.checkNotNullArgument(checked, "CheckedRunnable is null");
		
		return new UncheckedRunnable<>(checked, ignore());
	}
	
	public static <T> Runnable liftSneakily(CheckedRunnable checked) {
		Utilities.checkNotNullArgument(checked, "CheckedRunnable is null");
		
		return () -> {
			try {
				checked.run();
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
			}
		};
	}
	
	public static void runSneakily(CheckedRunnable checked) {
		Utilities.checkNotNullArgument(checked, "CheckedRunnable is null");
		
		try {
			checked.run();
		}
		catch ( Throwable e ) {
			Throwables.sneakyThrow(e);
			throw new AssertionError("Should not be here");
		}
	}
	
	private static class UncheckedRunnable<R> implements Runnable {
		private final CheckedRunnable m_checked;
		private final FailureHandler<Void> m_handler;
		
		UncheckedRunnable(CheckedRunnable checked, FailureHandler<Void> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public void run() {
			try {
				m_checked.run();
			}
			catch ( Throwable e ) {
				m_handler.handle(new FailureCase<>(null, e));
			}
		}
	}
	

// ******************************************************************************
// ****************************** Consumer **************************************
// ******************************************************************************
	
	public static <T> Consumer<T> liftIE(CheckedConsumer<T> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedConsumer is null");
		
		return new UncheckedConsumer<>(checked, ignore());
	}
	
	public static <T> Consumer<T> liftSneakily(CheckedConsumer<T> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedConsumer is null");
		
		return (data) -> {
			try {
				checked.accept(data);
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
			}
		};
	}
	
	public static <T> Consumer<T> lift(CheckedConsumer<? super T> checked,
										FailureHandler<? super T> handler) {
		return new UncheckedConsumer<>(checked, handler);
	}
	
	private static class UncheckedConsumer<T,R> implements Consumer<T> {
		private final CheckedConsumer<? super T> m_checked;
		private final FailureHandler<? super T> m_handler;
		
		UncheckedConsumer(CheckedConsumer<? super T> checked, FailureHandler<? super T> handler) {
			m_checked = checked;
			m_handler = handler;
		}

		@Override
		public void accept(T data) {
			try {
				m_checked.accept(data);
			}
			catch ( Throwable e ) {
				m_handler.handle(new FailureCase<>(data, e));
			}
		}
	}
	

// ******************************************************************************
// ****************************** Supplier **************************************
// ******************************************************************************
	
	public static <T> Supplier<T> liftSneakily(CheckedSupplier<T> checked) {
		Utilities.checkNotNullArgument(checked, "CheckedSupplier is null");
		
		return () -> {
			try {
				return checked.get();
			}
			catch ( Throwable e ) {
				Throwables.sneakyThrow(e);
				throw new AssertionError("Should not be here");
			}
		};
	}


// ******************************************************************************
// ****************************** Function **************************************
// ******************************************************************************
	
	
// ******************************************************************************
// ****************************** Predicate *************************************
// ******************************************************************************
	



// ******************************************************************************
// ******************************************************************************
// ******************************************************************************
	
	private static class IgnoreFailure<T> implements FailureHandler<T> {
		@Override
		public void handle(FailureCase<T> fcase) { }
	}
	
	public static class CollectingErrorHandler<T> implements FailureHandler<T> {
		private final List<FailureCase<T>> m_fcases;
		
		public CollectingErrorHandler() {
			m_fcases = new ArrayList<>();
		}
		
		public CollectingErrorHandler(List<FailureCase<T>> store) {
			m_fcases = store;
		}

		@Override
		public void handle(FailureCase<T> fcase) {
			m_fcases.add(fcase);
		}
		
		public List<FailureCase<T>> getFailureCases() {
			return Collections.unmodifiableList(m_fcases);
		}
	}

	public static class CountingErrorHandler<T> implements FailureHandler<T> {
		private long m_count = 0;

		@Override
		public void handle(FailureCase<T> fcase) {
			++m_count;
		}
		
		public long getErrorCount() {
			return m_count;
		}
	}

	public static class FailurePublisher<T> extends Observable<FailureCase<T>> implements Observer<FailureCase<T>> {
		private final Subject<FailureCase<T>> m_subject = PublishSubject.create();

		@Override
		protected void subscribeActual(Observer<? super FailureCase<T>> observer) {
			m_subject.subscribe(observer);
		}

		@Override
		public void onSubscribe(Disposable d) {
			m_subject.onSubscribe(d);
		}

		@Override
		public void onNext(FailureCase<T> t) {
			m_subject.onNext(t);
		}

		@Override
		public void onError(Throwable e) {
			m_subject.onError(e);
		}

		@Override
		public void onComplete() {
			m_subject.onComplete();
		}
		
	}
}
