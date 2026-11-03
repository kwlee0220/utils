package utils.jdbc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link JdbcConfiguration#parse(String)}와 {@code builder(JdbcConfiguration.parse(s))}의
 * full JDBC URL 파싱 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcConfigurationParseTest {
	private static final String FULL = "jdbc:mysql://h:3306/db?useSSL=false&characterEncoding=utf8"
										+ "&user=a&password=x+y%2Bz&maxPoolSize=4&serverTimezone=Asia%2FSeoul";

	@Test
	public void parse_keepsDriverOptionsAndExtractsCredentials() {
		JdbcConfiguration config = JdbcConfiguration.parse(FULL);
		Assertions.assertEquals("jdbc:mysql://h:3306/db?useSSL=false&characterEncoding=utf8&serverTimezone=Asia%2FSeoul",
								config.getJdbcUrl());
		Assertions.assertEquals("a", config.getUser());
		Assertions.assertEquals("x+y+z", config.getPassword());		// '+'는 그대로, %2B는 '+'
		Assertions.assertEquals(4, config.getMaxPoolSize());
	}

	@Test
	public void parse_withoutQueryOrOnlyCredentials() {
		Assertions.assertEquals("jdbc:postgresql://db/mdt",
								JdbcConfiguration.parse("jdbc:postgresql://db/mdt").getJdbcUrl());

		JdbcConfiguration config = JdbcConfiguration.parse("jdbc:postgresql://db/mdt?user=u&password=p");
		Assertions.assertEquals("jdbc:postgresql://db/mdt", config.getJdbcUrl());
		Assertions.assertEquals("p", config.getPassword());
	}

	@Test
	public void parse_rejectsInvalidInput() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> JdbcConfiguration.parse(null));
		IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
								() -> JdbcConfiguration.parse("jdbc:mysql://h/db?maxPoolSize=abc"));
		Assertions.assertEquals("invalid maxPoolSize: abc", e.getMessage());
	}

	@Test
	public void builderFromParsedConfig() {
		JdbcProcessor proc = JdbcProcessor.builder(JdbcConfiguration.parse(FULL)).build();
		Assertions.assertEquals("jdbc:mysql://h:3306/db?useSSL=false&characterEncoding=utf8&serverTimezone=Asia%2FSeoul",
								proc.getJdbcUrl());
		Assertions.assertEquals("x+y+z", proc.getPassword());

		// '?'가 없거나 자격 증명이 없어도 된다.
		JdbcProcessor noCreds = JdbcProcessor.builder(JdbcConfiguration.parse("jdbc:mysql://h/db")).build();
		Assertions.assertEquals("jdbc:mysql://h/db", noCreds.getJdbcUrl());
		Assertions.assertNull(noCreds.getUser());
		Assertions.assertNull(noCreds.getPassword());
	}

	/**
	 * 드라이버 클래스 이름·사용자·비밀번호는 선택 항목이라 {@code null}을 받고, JDBC URL만 {@code null}을 거부한다.
	 * {@code toString()}은 비밀번호를 빼고 {@code ?}를 한 번만 쓴다.
	 */
	@Test
	public void optionalFieldsAcceptNullAndToStringHidesPassword() {
		JdbcConfiguration config = JdbcConfiguration.parse("jdbc:mysql://h/db?useSSL=false&user=a&password=secret");
		config.setDriverClassName(null);
		config.setUser(null);
		config.setPassword(null);
		Assertions.assertNull(config.getDriverClassName());
		Assertions.assertThrows(IllegalArgumentException.class, () -> config.setJdbcUrl(null));

		config.setUser("a");
		config.setPassword("secret");
		Assertions.assertEquals("JdbcConfiguration[url=jdbc:mysql://h/db?useSSL=false, user=a]", config.toString());
		Assertions.assertEquals("JdbcConfiguration[url=null, user=null]", new JdbcConfiguration().toString());
	}

	@Test
	public void builderFromLocator() {
		JdbcProcessor proc = JdbcProcessor.builderFromLocator("postgresql:db.local:5432:u:p:mdt").build();
		Assertions.assertEquals("jdbc:postgresql://db.local:5432/mdt", proc.getJdbcUrl());
		Assertions.assertEquals("u", proc.getUser());
		Assertions.assertThrows(IllegalArgumentException.class, () -> JdbcProcessor.builderFromLocator("a:b:c"));
	}
}
