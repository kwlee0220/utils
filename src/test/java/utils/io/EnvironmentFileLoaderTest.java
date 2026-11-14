package utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * {@link EnvironmentFileLoader}의 줄 해석을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class EnvironmentFileLoaderTest {
	private static File envFile(Path dir, String content) throws IOException {
		Path file = dir.resolve(".env");
		Files.writeString(file, content);
		return file.toFile();
	}

	@Test
	public void spaces_around_equals_are_trimmed(@TempDir Path dir) throws Exception {
		Map<String,String> env = EnvironmentFileLoader.from(envFile(dir,
										"A=1\n"
										+ "B = two\n"
										+ "C =  \"  quoted  \"  \n"
										+ "# comment\n"
										+ "\n"
										+ "D=x=y\n")).load();

		Assertions.assertEquals(Map.of("A", "1", "B", "two", "C", "  quoted  ", "D", "x=y"), env);
	}

	@Test
	public void empty_key_or_missing_equals_is_an_error(@TempDir Path dir) throws Exception {
		IOException noKey = Assertions.assertThrows(IOException.class,
										() -> EnvironmentFileLoader.from(envFile(dir, "A=1\n = 2\n")).load());
		Assertions.assertTrue(noKey.getMessage().endsWith("line 2"), noKey.getMessage());

		Assertions.assertThrows(IOException.class,
								() -> EnvironmentFileLoader.from(envFile(dir, "NOEQUALS\n")).load());
	}
}
