package utils.http;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link TypedServerErrorMessage#toException()} 테스트.
 * <p>
 * 원격 응답의 {@code code}로 임의 클래스를 복원하지 않고, 항상 {@link RESTfulRemoteException}으로
 * 변환함을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TypedServerErrorMessageTest {
	@Test
	public void toExceptionWithCodeAndText() {
		TypedServerErrorMessage entity = new TypedServerErrorMessage("java.io.IOException", "boom");

		Throwable ex = entity.toException();

		assertInstanceOf(RESTfulRemoteException.class, ex);
		assertTrue(ex.getMessage().contains("java.io.IOException"), ex.getMessage());
		assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
	}

	@Test
	public void toExceptionWithCodeOnly() {
		TypedServerErrorMessage entity = new TypedServerErrorMessage("some.Code", null);

		Throwable ex = entity.toException();

		assertInstanceOf(RESTfulRemoteException.class, ex);
		assertTrue(ex.getMessage().contains("some.Code"), ex.getMessage());
	}

	@Test
	public void toExceptionDoesNotThrowAndDoesNotLoadRemoteClass() {
		// code가 Throwable이 아닌 클래스여도 더 이상 로딩/복원하지 않으므로 안전하게 RemoteException을 반환한다.
		TypedServerErrorMessage entity = new TypedServerErrorMessage("java.lang.String", "boom");

		Throwable ex = assertDoesNotThrow(() -> entity.toException());

		assertInstanceOf(RESTfulRemoteException.class, ex);
	}
}
