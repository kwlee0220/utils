package utils.io;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * {@link FileUtils}의 glob 순회와 실행 파일 검색을 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FileUtilsTest {
	@Test
	public void walk_with_glob_matches_file_names(@TempDir Path dir) throws Exception {
		Files.createDirectories(dir.resolve("a/b"));
		Files.writeString(dir.resolve("a/x.shp"), "");
		Files.writeString(dir.resolve("a/b/y.shp"), "");
		Files.writeString(dir.resolve("a/b/z.txt"), "");

		List<String> names = FileUtils.walk(dir.toFile(), "*.shp").map(File::getName).sort().toList();
		Assertions.assertEquals(List.of("x.shp", "y.shp"), names);
	}

	@Test
	public void findExecutable_returns_empty_instead_of_throwing() {
		Assertions.assertTrue(FileUtils.findExecutable("no-such-executable-0f3a9").isAbsent());
		Assertions.assertThrows(IllegalArgumentException.class, () -> FileUtils.findExecutable(" "));
	}

	@Test
	public void findExecutable_finds_a_program_on_the_path() {
		Assumptions.assumeTrue(new File("/bin/sh").canExecute(), "needs a POSIX shell");
		File sh = FileUtils.findExecutable("sh").getOrNull();
		Assertions.assertNotNull(sh);
		Assertions.assertTrue(sh.isAbsolute() && sh.canExecute());
	}
}
