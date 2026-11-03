package utils.jdbc;


/**
 * JDBC 작업 중 발생한 {@link java.sql.SQLException}을 감싸는 unchecked 예외.
 * <p>
 * utils.jdbc의 공개 메소드는 JDBC 오류를 {@link java.sql.SQLException}으로 던진다. 이 예외는
 * {@link java.util.Iterator#next()}나 {@code FStream} 순회처럼 checked 예외를 던질 수 없는 곳에서만 쓰며,
 * 원인({@link #getCause()})으로 원래의 {@link java.sql.SQLException}을 갖는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcException extends RuntimeException {
	private static final long serialVersionUID = -5253204991351254866L;
	
	/**
	 * 상세 메시지와 원인 예외를 갖는 {@code JdbcException}을 생성한다.
	 *
	 * @param details	예외 상세 메시지.
	 * @param cause		원인 예외.
	 */
	public JdbcException(String details, Throwable cause) {
		super(details, cause);
	}

	/**
	 * 원인 예외를 감싸는 {@code JdbcException}을 생성한다.
	 *
	 * @param cause	원인 예외.
	 */
	public JdbcException(Throwable cause) {
		super(cause);
	}
	
	/**
	 * 상세 메시지를 갖는 {@code JdbcException}을 생성한다.
	 *
	 * @param details	예외 상세 메시지.
	 */
	public JdbcException(String details) {
		super(details);
	}
}
