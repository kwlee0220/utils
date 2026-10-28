package utils.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;


/**
 * {@link JdbcUtils#maskPassword(String)}와, 이를 사용하는 JDBC 설정·processor의 문자열 표현을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcUtilsMaskPasswordTest {
	@Test
	public void testMaskQueryParameter() {
		assertEquals("jdbc:postgresql://db:5432/mdt?user=mdt&password=****",
					JdbcUtils.maskPassword("jdbc:postgresql://db:5432/mdt?user=mdt&password=secret"));
		assertEquals("jdbc:postgresql://db/mdt?password=****&ssl=true",
					JdbcUtils.maskPassword("jdbc:postgresql://db/mdt?password=secret&ssl=true"));
		assertEquals("jdbc:sqlserver://db;user=sa;Password=****;encrypt=true",
					JdbcUtils.maskPassword("jdbc:sqlserver://db;user=sa;Password=secret;encrypt=true"));
		assertEquals("jdbc:mysql://db/mdt?pwd=****", JdbcUtils.maskPassword("jdbc:mysql://db/mdt?pwd=secret"));
	}

	@Test
	public void testMaskUserInfo() {
		assertEquals("jdbc:mariadb://mdt:****@db:3306/mdt", JdbcUtils.maskPassword("jdbc:mariadb://mdt:secret@db:3306/mdt"));
	}

	@Test
	public void testNoPassword() {
		assertEquals("jdbc:postgresql://db:5432/mdt?user=mdt",
					JdbcUtils.maskPassword("jdbc:postgresql://db:5432/mdt?user=mdt"));
		assertEquals("jdbc:h2:mem:test", JdbcUtils.maskPassword("jdbc:h2:mem:test"));
		assertNull(JdbcUtils.maskPassword(null));
	}

	@Test
	public void testToStringDoesNotExposePassword() {
		JdbcConfiguration config = new JdbcConfiguration();
		config.setJdbcUrl("jdbc:postgresql://db/mdt?user=mdt&password=secret");
		config.setUser("mdt");
		config.setPassword("secret");
		assertFalse(config.toString().contains("secret"), config.toString());

		JdbcProcessor jdbc = JdbcProcessor.builder(config).pooled().build();
		assertFalse(jdbc.toString().contains("secret"), jdbc.toString());
	}
}
