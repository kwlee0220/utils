package utils.jdbc;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * 인메모리 H2 DB로 {@link JdbcProcessor}의 메타데이터 조회·식별자·풀 관리 동작을 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JdbcProcessorMetadataH2Test {
	private String m_url;
	private JdbcProcessor m_proc;

	@BeforeEach
	public void setUp() throws SQLException {
		m_url = "jdbc:h2:mem:meta-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
		m_proc = JdbcProcessor.builder().jdbcUrl(m_url).user("sa").password("").build();
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			stmt.executeUpdate("create table my_table (a int, b varchar(10))");
			stmt.executeUpdate("create table myxtable (x int)");
			stmt.executeUpdate("create view my_view as select a from my_table");
			stmt.executeUpdate("create table \"MixedCase\" (m int)");
			stmt.executeUpdate("create schema other");
			stmt.executeUpdate("create table other.my_table (z int)");
			stmt.executeUpdate("create table 품목 (id int)");
			stmt.executeUpdate("insert into 품목 values (1), (2)");
		}
	}

	@AfterEach
	public void tearDown() throws SQLException {
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			stmt.execute("drop all objects delete files");
		}
		JdbcConnectionPools.closeAll();
	}

	/**
	 * 이름의 {@code _}는 글자 그대로 비교하고, 현재 schema만 보며, 따옴표 없는 이름은 저장 대소문자로도 찾는다.
	 */
	@Test
	public void getColumnInfos_matchesExactTableInCurrentSchema() throws SQLException {
		Assertions.assertEquals(List.of("A", "B"), List.copyOf(m_proc.getColumns("my_table").keySet()));
		Assertions.assertEquals(List.of("Z"), List.copyOf(m_proc.getColumns("other", "my_table").keySet()));
		Assertions.assertEquals(List.of("M"), List.copyOf(m_proc.getColumns("MixedCase").keySet()));
		Assertions.assertEquals(List.of("A"), List.copyOf(m_proc.getColumns("my_view").keySet()));
		Assertions.assertTrue(m_proc.getColumns("no_such_table").isEmpty());
	}

	@Test
	public void existsTable_usesSameRulesAndIncludesViews() throws SQLException {
		Assertions.assertTrue(m_proc.tableExists("my_table"));
		Assertions.assertTrue(m_proc.tableExists("MY_TABLE"));
		Assertions.assertTrue(m_proc.tableExists("my_view"));
		Assertions.assertTrue(m_proc.tableExists("MixedCase"));
		Assertions.assertFalse(m_proc.tableExists("mixedcase"));		// 따옴표로 만든 이름은 대소문자까지 같아야 한다
		Assertions.assertFalse(m_proc.tableExists("my%"));				// 패턴 문자는 글자 그대로
		Assertions.assertFalse(m_proc.tableExists("my_tabl_"));
		Assertions.assertTrue(m_proc.tableExists("other", "my_table"));
		Assertions.assertFalse(m_proc.tableExists("other", "myxtable"));
	}

	/**
	 * {@code countRows}·{@code dropTable}은 한글 이름과 큰따옴표 식별자를 받고, {@code countQueryRows}는 끝의 {@code ;}를
	 * 지운다.
	 */
	@Test
	public void identifiersAndCountQuery() throws SQLException {
		Assertions.assertEquals(2L, m_proc.countRows("품목"));
		Assertions.assertEquals(0L, m_proc.countRows("\"MixedCase\""));
		Assertions.assertEquals(0L, m_proc.countRows("other.my_table"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> m_proc.countRows("item; drop table x"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> m_proc.countRows("\"a\"b\""));

		Assertions.assertEquals(2L, m_proc.countQueryRows("select * from 품목 ;  "));
		Assertions.assertEquals(1L, m_proc.countQueryRows("select * from 품목 where id = 1;;"));

		m_proc.dropTable("\"MixedCase\"");
		Assertions.assertFalse(m_proc.tableExists("MixedCase"));
	}

	/**
	 * URL로 드라이버를 추정할 때 하위 프로토콜 이름 전체를 비교한다.
	 */
	@Test
	public void driverGuessComparesWholeSubprotocol() {
		Assertions.assertEquals("com.mysql.cj.jdbc.Driver",
								JdbcProcessor.builder().jdbcUrl("jdbc:mysql://h/db").build().getDriverClassName());
		Assertions.assertEquals("org.h2.Driver",
								JdbcProcessor.builder().jdbcUrl("jdbc:h2:mem:x").build().getDriverClassName());
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> JdbcProcessor.builder().jdbcUrl("jdbc:my:foo").build());
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> JdbcProcessor.builder().jdbcUrl("jdbc:postgres:foo").build());
	}

	@Test
	public void toStringHasOneQueryMarkAndNoPassword() {
		JdbcProcessor proc = JdbcProcessor.builder().jdbcUrl("jdbc:mysql://h/db?useSSL=false&password=secret")
											.user("u").password("secret").pooled(2).build();
		String str = proc.toString();
		Assertions.assertFalse(str.contains("secret"), str);
		Assertions.assertTrue(str.startsWith("JdbcProcessor[url=jdbc:mysql://h/db?useSSL=false"), str);
		Assertions.assertTrue(str.endsWith(", user=u, driver=com.mysql.cj.jdbc.Driver, maxPoolSize=2]"), str);
		Assertions.assertEquals(1, str.chars().filter(c -> c == '?').count(), str);
	}

	/**
	 * {@link JdbcConnectionPools#close(JdbcProcessor)}는 그 processor의 풀만 닫고, processor는 다음 연결에서 새 풀을
	 * 만든다.
	 */
	@Test
	public void closePoolOfOneProcessor() throws SQLException {
		JdbcProcessor pooled = JdbcProcessor.builder().jdbcUrl(m_url).user("sa").password("").pooled(1).build();
		JdbcProcessor other = JdbcProcessor.builder().jdbcUrl(m_url).user("SA").password("").pooled(1).build();
		Assertions.assertFalse(JdbcConnectionPools.close(pooled));			// 아직 풀이 없다
		Assertions.assertFalse(JdbcConnectionPools.close(m_proc));			// 풀을 쓰지 않는다

		Assertions.assertEquals(2L, pooled.countRows("품목"));
		Assertions.assertEquals(2L, other.countRows("품목"));
		Assertions.assertEquals(2, JdbcConnectionPools.size());

		Assertions.assertTrue(JdbcConnectionPools.close(pooled));
		Assertions.assertEquals(1, JdbcConnectionPools.size());
		Assertions.assertEquals(2L, pooled.countRows("품목"));				// 새 풀
		Assertions.assertEquals(2, JdbcConnectionPools.size());
	}

	/**
	 * jar 파일로 드라이버를 적재하면 jar 파일별로 하나의 ClassLoader를 공유한다.
	 */
	@Test
	public void jarFileLoaderIsSharedPerJar() throws Exception {
		File h2Jar = new File(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		JdbcProcessor p1 = JdbcProcessor.builder().jdbcUrl(m_url).driverClassName("org.h2.Driver")
										.jarFile(h2Jar).user("sa").password("").build();
		JdbcProcessor p2 = JdbcProcessor.builder().jdbcUrl(m_url).driverClassName("org.h2.Driver")
										.jarFile(h2Jar).user("sa").password("").build();
		try ( Connection conn = p1.connect() ) {
			Assertions.assertTrue(conn.isValid(1));
		}
		ClassLoader loader = p1.getClassLoader();
		Assertions.assertNotNull(loader);
		Assertions.assertSame(loader, p2.getClassLoader());
		// 테스트 classpath에도 H2가 있어서 URLClassLoader(부모 우선 위임)가 드라이버 클래스를 부모에게서 받을 수 있다.
		// 그래서 연결 객체의 ClassLoader는 비교하지 않고, ClassLoader 공유와 연결 성공만 확인한다.
		try ( Connection conn = p2.connect() ) {
			Assertions.assertTrue(conn.isValid(1));
		}
		Assertions.assertSame(loader, p2.getClassLoader());

		Assertions.assertNull(m_proc.getClassLoader());
	}

	/**
	 * 대소문자를 구분해 만든 schema도 주어진 이름 그대로 찾는다. 따옴표 없는 이름은 저장 대소문자로도 찾는다.
	 */
	@Test
	public void caseSensitiveSchemaIsFoundAsGiven() throws SQLException {
		try ( Connection conn = m_proc.connect();
				Statement stmt = conn.createStatement() ) {
			stmt.executeUpdate("create schema \"MixedSchema\"");
			stmt.executeUpdate("create table \"MixedSchema\".t (id int)");
		}
		Assertions.assertTrue(m_proc.tableExists("MixedSchema", "t"));
		Assertions.assertEquals(List.of("ID"), List.copyOf(m_proc.getColumns("MixedSchema", "t").keySet()));
		Assertions.assertFalse(m_proc.tableExists("mixedschema", "t"));
		Assertions.assertTrue(m_proc.tableExists("OTHER", "my_table"));
		Assertions.assertTrue(m_proc.tableExists("other", "MY_TABLE"));
	}

	/**
	 * 경로에 공백·{@code #}·{@code %}가 있는 jar 파일로도 드라이버를 적재하며, 같은 파일의 다른 경로 표기는
	 * 같은 ClassLoader를 쓴다.
	 */
	@Test
	public void jarFileWithSpecialCharactersInPath(@TempDir Path dir) throws Exception {
		File h2Jar = new File(org.h2.Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		Path copy = dir.resolve("driver with spaces #1 %20.jar");
		Files.copy(h2Jar.toPath(), copy);

		JdbcProcessor p1 = JdbcProcessor.builder().jdbcUrl(m_url).driverClassName("org.h2.Driver")
										.jarFile(copy.toFile()).user("sa").password("").build();
		try ( Connection conn = p1.connect() ) {
			Assertions.assertTrue(conn.isValid(1));
		}

		File sameFile = new File(new File(dir.toFile(), "."), copy.getFileName().toString());
		JdbcProcessor p2 = JdbcProcessor.builder().jdbcUrl(m_url).driverClassName("org.h2.Driver")
										.jarFile(sameFile).user("sa").password("").build();
		try ( Connection conn = p2.connect() ) {
			Assertions.assertTrue(conn.isValid(1));
		}
		Assertions.assertNotNull(p1.getClassLoader());
		Assertions.assertSame(p1.getClassLoader(), p2.getClassLoader());
	}
}
