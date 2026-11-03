package utils.func;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import utils.Preconditions;
import utils.Throwables;


/**
 * 자주 사용되는 {@link FailureHandler} 구현들을 생성하는 정적 팩토리 클래스이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FailureHandlers {
	private FailureHandlers() {
		throw new AssertionError("Should not be called: class=" + FailureHandlers.class);
	}

	/**
	 * 전달된 실패를 아무 처리 없이 무시하는 처리기를 반환한다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 * @return	실패를 무시하는 처리기.
	 */
	public static <T> FailureHandler<T> ignore() {
		return new FailureHandler<T>() {
			@Override
			public void handle(FailureCase<T> fcase) { }
		};
	}
	
	/**
	 * 전달된 실패의 원인 예외({@link FailureCase#getCause()})를 검사 예외 선언 없이
	 * 그대로 다시 던지는(sneaky throw) 처리기를 반환한다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 * @return	원인 예외를 다시 던지는 처리기.
	 */
	public static <T> FailureHandler<T> throwSneakily() {
		return new FailureHandler<T>() {
			@Override
			public void handle(FailureCase<T> fcase) {
				Throwables.sneakyThrow(fcase.getCause());
			}
		};
	}
	
	/**
	 * 전달된 실패의 횟수만 세는 처리기이다.
	 * <p>
	 * 실패 정보 자체는 보관하지 않는다. 횟수는 {@link AtomicLong}으로 세므로 여러 스레드에서 함께 써도 된다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 */
	public static class CountingFailureHandler<T> implements FailureHandler<T> {
		private final AtomicLong m_count = new AtomicLong();

		/**
		 * 실패 횟수가 0인 처리기를 생성한다.
		 */
		public CountingFailureHandler() { }

		@Override
		public void handle(FailureCase<T> fcase) {
			m_count.incrementAndGet();
		}
		
		/**
		 * 지금까지 전달된 실패 횟수를 반환한다.
		 *
		 * @return	실패 횟수.
		 */
		public long getErrorCount() {
			return m_count.get();
		}
	}
	/**
	 * 실패 횟수를 세는 새 {@link CountingFailureHandler}를 생성한다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 * @return	새 {@code CountingFailureHandler} 객체.
	 */
	public static <T> CountingFailureHandler<T> count() {
		return new CountingFailureHandler<>();
	}

	/**
	 * 전달된 실패 정보({@link FailureCase})를 리스트에 수집하는 처리기이다.
	 * <p>
	 * 동기화되지 않으므로 thread-safe하지 않다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 */
	public static class CollectingFailureHandler<T> implements FailureHandler<T> {
		private final List<FailureCase<T>> m_fcases;
		
		/**
		 * 내부에 새 {@link ArrayList}를 저장소로 사용하는 처리기를 생성한다.
		 */
		public CollectingFailureHandler() {
			m_fcases = new ArrayList<>();
		}
		
		/**
		 * 주어진 리스트를 저장소로 사용하는 처리기를 생성한다.
		 * <p>
		 * 리스트는 복사되지 않고 그대로 사용되므로, 수집된 실패 정보는 {@code store}에 직접 추가된다.
		 *
		 * @param store	실패 정보를 저장할 리스트.
		 * @throws IllegalArgumentException	{@code store}가 {@code null}인 경우.
		 */
		public CollectingFailureHandler(List<FailureCase<T>> store) {
			Preconditions.checkNotNullArgument(store, "store is null");
			m_fcases = store;
		}

		@Override
		public void handle(FailureCase<T> fcase) {
			Preconditions.checkNotNullArgument(fcase, "FailureCase is null");
			
			m_fcases.add(fcase);
		}
		
		/**
		 * 지금까지 수집된 실패 정보 리스트를 반환한다.
		 * <p>
		 * 반환되는 리스트는 내부 저장소에 대한 수정 불가능한 뷰이므로, 이후 수집되는 실패도 반영된다.
		 *
		 * @return	수집된 실패 정보 리스트(수정 불가).
		 */
		public List<FailureCase<T>> getFailureCases() {
			return Collections.unmodifiableList(m_fcases);
		}
	}
	/**
	 * 실패 정보를 수집하는 새 {@link CollectingFailureHandler}를 생성한다.
	 *
	 * @param <T>	실패한 입력 데이터 타입.
	 * @return	새 {@code CollectingFailureHandler} 객체.
	 */
	public static <T> CollectingFailureHandler<T> collect() {
		return new CollectingFailureHandler<>();
	}
}
