package utils.jdbc;

import java.sql.SQLException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * 설정 오류와 연결 실패의 예외 메시지에 비밀번호가 들어가지 않는지 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcPasswordInMessagesTest {
	private static final String SECRET = "s3cr3t-pw";

	private static void assertHidden(Throwable e) {
		Assertions.assertFalse(e.getMessage().contains(SECRET), e.getMessage());
	}

	@Test
	public void locatorTokenCountErrorHidesPassword() {
		String loc = "mysql:h:3306:u:" + SECRET;
		assertHidden(Assertions.assertThrows(IllegalArgumentException.class,
											() -> JdbcProcessor.builderFromLocator(loc)));
		assertHidden(Assertions.assertThrows(IllegalArgumentException.class,
											() -> new JdbcParameters().jdbcLoc(loc)));
	}

	@Test
	public void unsupportedJdbcUrlErrorHidesPassword() {
		String url = "jdbc:nosuchdb://h/db?user=u&password=" + SECRET;
		assertHidden(Assertions.assertThrows(IllegalArgumentException.class,
											() -> JdbcProcessor.builder().jdbcUrl(url).build()));
	}

	/**
	 * 드라이버 메시지에 URL이 그대로 들어가는 경우(DriverManager의 "No suitable driver found for &lt;url&gt;")에도
	 * 덧붙인 메시지에서는 비밀번호를 가린다.
	 */
	@Test
	public void connectFailureHidesPasswordInDriverMessage() {
		String url = "jdbc:nosuchdb://h/db?user=u&password=" + SECRET;
		JdbcProcessor proc = JdbcProcessor.builder().jdbcUrl(url).driverClassName("org.h2.Driver").build();
		SQLException e = Assertions.assertThrows(SQLException.class, proc::connect);
		assertHidden(e);
		Assertions.assertTrue(e.getMessage().contains("****"), e.getMessage());
	}
}
