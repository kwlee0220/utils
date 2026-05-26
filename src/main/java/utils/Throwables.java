package utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * {@link Throwable} 처리에 필요한 공통 유틸리티 메서드 모음.
 * <p>
 * 래퍼 예외를 벗겨 실제 원인을 찾거나, checked exception을 runtime exception으로
 * 변환하는 보조 기능을 제공한다. 이 클래스는 상태를 가지지 않으며 인스턴스화할 수 없다.
 *
 * @author Kang-Woo Lee
 */
public class Throwables {
	private Throwables() {
		throw new AssertionError("Should not be invoked!!: class=" + Throwables.class.getName());
	}
	
	/**
	 * 주어진 예외를 호출자의 {@code throws} 선언과 무관하게 그대로 던진다.
	 * <p>
	 * Java generic type erasure를 이용한 메서드이므로 checked exception도 컴파일러의
	 * checked-exception 검사를 우회해 던질 수 있다. 호출 경계에서 공개 API의 예외 계약을
	 * 흐릴 수 있으므로 내부 어댑터나 테스트처럼 의도가 명확한 곳에서만 사용하는 것이 좋다.
	 *
	 * @param <T> 반환 타입. 실제로 정상 반환하지 않지만 expression 위치에서 사용하기 위한 타입.
	 * @param <X> 던질 예외 타입으로 추론되는 타입.
	 * @param e 던질 예외.
	 * @return 정상 반환하지 않는다.
	 * @throws X 항상 {@code e}를 던진다.
	 */
	@SuppressWarnings("unchecked")
	public static <T,X extends Throwable> T sneakyThrow(Throwable e) throws X {
	    throw (X)e;
	}

	/**
	 * 흔한 래퍼 예외를 반복적으로 벗겨 가장 안쪽의 원인 예외를 반환한다.
	 * <p>
	 * 다음 타입은 래퍼로 간주한다:
	 * {@link InvocationTargetException}, {@link UndeclaredThrowableException},
	 * {@link ExecutionException}, {@link RuntimeExecutionException},
	 * {@link RuntimeInterruptedException}, {@link RuntimeTimeoutException},
	 * {@link CompletionException}, 그리고 cause가 있는 {@link RuntimeException}.
	 * 더 이상 벗길 cause가 없으면 현재 예외를 반환한다.
	 *
	 * @param e 벗길 예외.
	 * @return 래퍼를 제거한 원인 예외. 벗길 수 없으면 {@code e}.
	 */
	public static Throwable unwrapThrowable(Throwable e) {
		Throwable cause = null;
		
		while ( true ) {
			if ( e instanceof InvocationTargetException ) {
				cause = ((InvocationTargetException)e).getTargetException();
			}
			else if ( e instanceof UndeclaredThrowableException ) {
				cause = ((UndeclaredThrowableException)e).getUndeclaredThrowable();
			}
			else if ( e instanceof ExecutionException ) {
				cause = ((ExecutionException)e).getCause();
			}
			else if ( e instanceof RuntimeExecutionException ) {
				cause = ((RuntimeExecutionException)e).getCause();
			}
			else if ( e instanceof RuntimeInterruptedException ) {
				cause = ((RuntimeInterruptedException)e).getCause();
			}
			else if ( e instanceof RuntimeTimeoutException ) {
				cause = ((RuntimeTimeoutException)e).getCause();
			}
			else if ( e instanceof CompletionException ) {
				cause = ((CompletionException)e).getCause();
			}
			else if ( e instanceof RuntimeException ) {
				cause = ((RuntimeException)e).getCause();
			}
			else {
				return e;
			}
			if ( cause == null ) {
				return e;
			}
			e = cause;
		}
	}
	
	/**
	 * 주어진 {@link Throwable}을 {@link RuntimeException}으로 변환한다.
	 * <p>
	 * 이미 {@link RuntimeException}이면 그대로 반환하고, {@link Error}이면 감싸지 않고 다시 던진다.
	 * 그 외 checked exception은 {@link RuntimeException}으로 감싸 반환한다.
	 *
	 * @param e 변환할 예외.
	 * @return runtime exception.
	 * @throws Error {@code e}가 {@link Error}인 경우.
	 */
	public static RuntimeException toRuntimeException(Throwable e) {
		if ( e instanceof RuntimeException ) {
			return (RuntimeException)e;
		}
		else if ( e instanceof Error ) {
			throw (Error)e;
		}
		else {
			return new RuntimeException(e);
		}
	}
	
	/**
	 * 주어진 {@link Throwable}을 지정한 wrapper로 {@link RuntimeException}으로 변환한다.
	 * <p>
	 * 이미 {@link RuntimeException}이면 그대로 반환하고, {@link Error}이면 감싸지 않고 다시 던진다.
	 * 그 외 checked exception만 {@code wrapper}에 전달한다.
	 *
	 * @param e 변환할 예외.
	 * @param wrapper checked exception을 runtime exception으로 감쌀 함수.
	 * @return runtime exception.
	 * @throws Error {@code e}가 {@link Error}인 경우.
	 */
	public static RuntimeException toRuntimeException(Throwable e,
														Function<Throwable,RuntimeException> wrapper) {
		if ( e instanceof RuntimeException ) {
			return (RuntimeException)e;
		}
		else if ( e instanceof Error ) {
			throw (Error)e;
		}
		else {
			return wrapper.apply(e);
		}
	}
	
	/**
	 * 주어진 {@link Throwable}을 {@link Exception}으로 변환한다.
	 * <p>
	 * 이미 {@link Exception}이면 그대로 반환한다. {@link Error}처럼 {@link Exception}이 아닌
	 * {@link Throwable}은 {@link RuntimeException}으로 감싸 반환한다.
	 *
	 * @param e 변환할 예외.
	 * @return exception.
	 */
	public static Exception toException(Throwable e) {
		if ( e instanceof Exception ) {
			return (Exception)e;
		}
		else {
			return new RuntimeException(e);
		}
	}
	
	/**
	 * 주어진 예외가 지정한 타입이면 해당 타입으로 캐스팅하여 던진다.
	 * <p>
	 * 특정 checked exception만 다시 전파하고 나머지는 호출자가 이어서 처리해야 할 때 사용한다.
	 *
	 * @param <T> 다시 던질 예외 타입.
	 * @param e 검사할 예외.
	 * @param thrClas 다시 던질 예외 클래스.
	 * @throws T {@code e}가 {@code thrClas}의 인스턴스인 경우.
	 */
	public static <T extends Throwable> void throwIfInstanceOf(Throwable e,
																Class<T> thrClas) throws T {
		if ( thrClas.isInstance(e) ) {
			throw thrClas.cast(e);
		}
	}
}
