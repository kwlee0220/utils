package utils.io;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ZipFileTest {
	@TempDir
	public Path tmp;

	private Path write(Path p, String content) throws Exception {
		Files.createDirectories(p.getParent());
		Files.write(p, content.getBytes(StandardCharsets.UTF_8));
		return p;
	}

	private String read(Path p) throws Exception {
		return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
	}

	// 디렉토리 압축 → 해제 라운드트립: 내용과 구조가 보존되어야 한다.
	@Test
	@Timeout(10)
	public void zipDirectoryAndUnzipRoundTrip() throws Exception {
		Path src = tmp.resolve("src");
		write(src.resolve("a.txt"), "alpha");
		write(src.resolve("sub/b.txt"), "beta");

		Path zip = tmp.resolve("out.zip");
		ZipFile.zipDirectory(zip, src);
		Assertions.assertTrue(Files.exists(zip));

		Path dest = tmp.resolve("dest");
		Files.createDirectories(dest);
		new ZipFile(zip).unzip(dest);

		Assertions.assertEquals("alpha", read(dest.resolve("a.txt")));
		Assertions.assertEquals("beta", read(dest.resolve("sub/b.txt")));
	}

	// listEntries는 경로 문자열 목록을 반환한다(#1).
	@Test
	@Timeout(10)
	public void listEntriesReturnsNames() throws Exception {
		Path src = tmp.resolve("src");
		write(src.resolve("a.txt"), "x");
		write(src.resolve("sub/b.txt"), "y");

		Path zip = tmp.resolve("out.zip");
		ZipFile.zipDirectory(zip, src);

		List<String> entries = new ZipFile(zip).listEntries();
		Assertions.assertTrue(entries.contains("/a.txt"), entries.toString());
		Assertions.assertTrue(entries.contains("/sub/b.txt"), entries.toString());
	}

	// zip()의 파일 평탄화: 상위 경로 구조 없이 파일명만 루트에 들어간다.
	@Test
	@Timeout(10)
	public void zipFlattensFiles() throws Exception {
		Path f1 = write(tmp.resolve("deep/nested/one.txt"), "1");
		Path f2 = write(tmp.resolve("other/two.txt"), "2");

		Path zip = tmp.resolve("flat.zip");
		ZipFile.zip(zip, "", List.of(f1, f2));

		List<String> entries = new ZipFile(zip).listEntries();
		Assertions.assertTrue(entries.contains("/one.txt"), entries.toString());
		Assertions.assertTrue(entries.contains("/two.txt"), entries.toString());
	}

	// #3: 존재하지 않는 ZIP을 unzip하면 빈 ZIP을 만들어 성공하지 않고 오류가 드러나야 한다.
	@Test
	@Timeout(10)
	public void unzipMissingZipFails() throws Exception {
		Path missing = tmp.resolve("nope.zip");
		Path dest = tmp.resolve("dest");
		Files.createDirectories(dest);

		Assertions.assertThrows(Exception.class, () -> new ZipFile(missing).unzip(dest));
		Assertions.assertFalse(Files.exists(missing), "없는 ZIP이 생성되면 안 됨");
	}

	// #4: 디렉토리 엔트리가 없어도 파일 부모가 생성되어 해제가 성공해야 한다.
	@Test
	@Timeout(10)
	public void unzipCreatesParentDirsForNestedFiles() throws Exception {
		Path src = tmp.resolve("src");
		write(src.resolve("x/y/z.txt"), "deep");

		Path zip = tmp.resolve("nested.zip");
		ZipFile.zipDirectory(zip, src);

		Path dest = tmp.resolve("dest");
		Files.createDirectories(dest);
		new ZipFile(zip).unzip(dest);

		Assertions.assertEquals("deep", read(dest.resolve("x/y/z.txt")));
	}

	// baseName을 지정하면 모든 항목이 그 디렉토리 아래에 담긴다.
	@Test
	@Timeout(10)
	public void zipUnderBaseName() throws Exception {
		Path f1 = write(tmp.resolve("one.txt"), "1");

		Path zip = tmp.resolve("based.zip");
		ZipFile.zip(zip, "base", List.of(f1));

		List<String> entries = new ZipFile(zip).listEntries();
		Assertions.assertTrue(entries.contains("/base/one.txt"), entries.toString());
	}

	// #6: 루트 경로는 압축 대상이 될 수 없다.
	@Test
	@Timeout(10)
	public void zipRejectsRootPath() {
		Path zip = tmp.resolve("r.zip");
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> ZipFile.zip(zip, "", List.of(Path.of("/"))));
	}

	// #5: ".." 류 엔트리가 대상 디렉토리 밖에 파일을 쓰지 못해야 한다(Zip Slip 방어).
	@Test
	@Timeout(10)
	public void unzipDoesNotEscapeTargetDirectory() throws Exception {
		Path zip = tmp.resolve("evil.zip");
		try ( ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip)) ) {
			ZipEntry entry = new ZipEntry("../escaped.txt");
			zos.putNextEntry(entry);
			zos.write("pwned".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}

		Path dest = tmp.resolve("dest");
		Files.createDirectories(dest);
		try {
			new ZipFile(zip).unzip(dest);
		}
		catch ( Exception expected ) {
			// resolveDest가 탈출을 막아 예외를 던지는 것도 허용되는 안전한 동작이다.
		}

		// 어떤 경우에도 dest 밖(tmp 바로 아래)에 파일이 생성되면 안 된다.
		Assertions.assertFalse(Files.exists(tmp.resolve("escaped.txt")), "Zip Slip으로 탈출함");
	}
}
