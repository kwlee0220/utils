package utils.jdbc;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link JdbcUtils#toPostgresInterval(Duration)} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtilsPostgresIntervalTest {
	@Test
	public void wholeUnits() {
		Assertions.assertEquals("0 seconds", JdbcUtils.toPostgresInterval(Duration.ZERO));
		Assertions.assertEquals("1 minutes", JdbcUtils.toPostgresInterval(Duration.ofMinutes(1)));
		Assertions.assertEquals("1 days 2 hours 3 minutes 4 seconds",
								JdbcUtils.toPostgresInterval(Duration.parse("P1DT2H3M4S")));
	}

	/**
	 * 초 미만 값은 버리지 않고 소수 초로 표기한다.
	 */
	@Test
	public void keepsFractionalSeconds() {
		Assertions.assertEquals("1.5 seconds", JdbcUtils.toPostgresInterval(Duration.ofMillis(1500)));
		Assertions.assertEquals("0.5 seconds", JdbcUtils.toPostgresInterval(Duration.ofMillis(500)));
		Assertions.assertEquals("0.000000001 seconds", JdbcUtils.toPostgresInterval(Duration.ofNanos(1)));
		Assertions.assertEquals("1 minutes 0.25 seconds",
								JdbcUtils.toPostgresInterval(Duration.ofMinutes(1).plusMillis(250)));
	}

	@Test
	public void rejectsNegative() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> JdbcUtils.toPostgresInterval(Duration.ofSeconds(-1)));
	}
}
