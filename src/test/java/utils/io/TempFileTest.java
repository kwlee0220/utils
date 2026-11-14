package utils.io;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link TempFile}의 생성·삭제를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TempFileTest {
	@Test
	public void create_with_name_and_delete_on_close() throws Exception {
		Path path;
		try ( TempFile tmp = TempFile.create("tf-", ".json") ) {
			path = tmp.getPath();
			Assertions.assertTrue(path.getFileName().toString().startsWith("tf-"));
			Assertions.assertTrue(path.getFileName().toString().endsWith(".json"));
			Assertions.assertEquals(path.toFile(), tmp.getFile());

			try ( OutputStream os = tmp.getOutputStream() ) {
				os.write("x".getBytes());
			}
			Assertions.assertEquals("x", Files.readString(path));
		}
		Assertions.assertFalse(Files.exists(path));
	}

	@Test
	public void close_after_external_delete_is_fine() throws Exception {
		TempFile tmp = TempFile.create();
		Files.delete(tmp.getPath());
		tmp.close();
	}
}
