package utils.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * {@link LfsPath}의 {@code equals}/{@code hashCode} 계약과 {@code create(overwrite)}를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LfsPathTest {
	@Test
	public void equals_rejects_null_and_other_types_without_exception() {
		LfsPath path = LfsPath.of(new File("/tmp/a.txt"));

		Assertions.assertFalse(path.equals(null));
		Assertions.assertFalse(path.equals("/tmp/a.txt"));
		Assertions.assertFalse(path.equals(new File("/tmp/a.txt")));
	}

	@Test
	public void same_file_is_equal_with_same_hash_code() {
		LfsPath p1 = LfsPath.of(new File("/tmp/a.txt"));
		LfsPath p2 = LfsPath.of(new File("/tmp/a.txt"));
		LfsPath p3 = LfsPath.of(new File("/tmp/b.txt"));

		Assertions.assertEquals(p1, p2);
		Assertions.assertEquals(p1.hashCode(), p2.hashCode());
		Assertions.assertNotEquals(p1, p3);

		Set<LfsPath> set = new HashSet<>(Set.of(p1, p3));
		Assertions.assertTrue(set.contains(p2));
		Assertions.assertEquals(2, set.size());
	}

	@Test
	public void deserialized_absolute_path_equals_the_original() throws Exception {
		LfsPath path = LfsPath.of(new File("/tmp/a.txt"));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try ( ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
			oos.writeObject(path);
		}
		try ( ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray())) ) {
			Object restored = ois.readObject();
			Assertions.assertEquals(path, restored);
			Assertions.assertEquals(path.hashCode(), restored.hashCode());
		}
	}

	@Test
	public void create_without_overwrite_rejects_existing_file(@TempDir Path dir) throws Exception {
		File file = dir.resolve("a.txt").toFile();
		Files.writeString(file.toPath(), "old");

		Assertions.assertThrows(FileAlreadyExistsException.class, () -> LfsPath.of(file).create(false));
		Assertions.assertEquals("old", Files.readString(file.toPath()));
	}

	@Test
	public void create_without_overwrite_creates_new_file_and_parents(@TempDir Path dir) throws Exception {
		File file = dir.resolve("sub/dir/a.txt").toFile();
		try ( OutputStream os = LfsPath.of(file).create(false) ) {
			os.write("new".getBytes());
		}
		Assertions.assertEquals("new", Files.readString(file.toPath()));
	}

	@Test
	public void create_with_overwrite_replaces_existing_content(@TempDir Path dir) throws Exception {
		File file = dir.resolve("a.txt").toFile();
		Files.writeString(file.toPath(), "old and long");

		try ( OutputStream os = LfsPath.of(file).create(true) ) {
			os.write("new".getBytes());
		}
		Assertions.assertEquals("new", Files.readString(file.toPath()));
	}

	@Test
	public void children_of_missing_or_regular_file_are_errors_not_null(@TempDir Path dir) throws Exception {
		File file = dir.resolve("a.txt").toFile();
		Files.writeString(file.toPath(), "x");

		Assertions.assertThrows(NotDirectoryException.class, () -> LfsPath.of(file).streamChildFilePaths());
		Assertions.assertThrows(NoSuchFileException.class,
								() -> LfsPath.of(dir.resolve("none").toFile()).streamChildFilePaths());
		Assertions.assertEquals(List.of(LfsPath.of(file)),
								LfsPath.of(dir.toFile()).streamChildFilePaths().toList());
		Assertions.assertThrows(NotDirectoryException.class, () -> LfsPath.of(file).walkTree(false));
	}

	@Test
	public void renameTo_creates_parents_and_rejects_existing_destination(@TempDir Path dir)
		throws Exception {
		File src = dir.resolve("a.txt").toFile();
		Files.writeString(src.toPath(), "a");
		File dst = dir.resolve("x/y/b.txt").toFile();

		LfsPath.of(src).renameTo(LfsPath.of(dst), false);
		Assertions.assertFalse(src.exists());
		Assertions.assertEquals("a", Files.readString(dst.toPath()));

		File other = dir.resolve("c.txt").toFile();
		Files.writeString(other.toPath(), "c");
		Assertions.assertThrows(FileAlreadyExistsException.class,
								() -> LfsPath.of(other).renameTo(LfsPath.of(dst), false));
		Assertions.assertEquals("a", Files.readString(dst.toPath()));

		LfsPath.of(other).renameTo(LfsPath.of(dst), true);
		Assertions.assertEquals("c", Files.readString(dst.toPath()));
	}

	@Test
	public void mkdirs_creates_intermediate_directories(@TempDir Path dir) {
		File target = dir.resolve("p/q/r").toFile();

		Assertions.assertTrue(LfsPath.of(target).mkdirs());
		Assertions.assertTrue(target.isDirectory());
		Assertions.assertFalse(LfsPath.of(target).mkdirs(), "already exists");
	}
}
