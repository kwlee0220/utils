package utils.async.command;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.async.command.CommandExecution.Output;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariableTest {
	@TempDir File m_tempDir;

	// ---------- 값만으로 생성된 변수 ----------

	@Test
	public void value_only_variable_close_without_path_does_not_fail() throws Exception {
		// 예전에는 m_file이 null이라 close()가 NullPointerException으로 실패했다.
		CommandVariable var = new CommandVariable("x", "hello");
		var.close();

		Assertions.assertNull(var.getFile());
		Assertions.assertEquals("hello", var.getValue());
	}

	@Test
	public void value_only_variable_path_creates_temp_file_with_value() throws Exception {
		// 예전에는 m_file이 null이라 ${x:path}가 NullPointerException으로 실패했다.
		CommandVariable var = new CommandVariable("x", "hello 한글");

		String path = var.getValueByModifier("path");
		File file = new File(path);
		Assertions.assertTrue(file.isFile());
		Assertions.assertEquals(file.getAbsoluteFile(), var.getFile().getAbsoluteFile());
		Assertions.assertEquals("hello 한글", Files.readString(file.toPath(), StandardCharsets.UTF_8));
		Assertions.assertEquals("hello 한글", var.getValue());

		// 같은 변수에 대해 다시 요청하면 같은 파일을 돌려준다.
		Assertions.assertEquals(path, var.getValueByModifier("path"));

		var.close();
		Assertions.assertFalse(file.exists(), "temp file must be deleted on close");
		Assertions.assertEquals("hello 한글", var.getValue());
	}

	@Test
	public void value_only_variable_temp_file_is_owner_only() throws Exception {
		Assumptions.assumeTrue(File.separatorChar == '/', "POSIX only");

		CommandVariable var = new CommandVariable("secret", "TOP-SECRET");
		try {
			File file = new File(var.getValueByModifier("path"));
			Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file.toPath());
			Assertions.assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms);
		}
		finally {
			var.close();
		}
	}

	// ---------- 파일이 지정된 변수 ----------

	@Test
	public void value_with_file_path_writes_value_to_given_file() throws Exception {
		File file = new File(m_tempDir, "x.txt");
		CommandVariable var = new CommandVariable("x", "hello", file);

		Assertions.assertEquals(file.getAbsolutePath(), var.getValueByModifier("path"));
		Assertions.assertEquals("hello", Files.readString(file.toPath(), StandardCharsets.UTF_8));
		Assertions.assertEquals("hello", var.getValue());

		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("hello", var.getValue());
	}

	@Test
	public void file_variable_reads_value_from_file() throws Exception {
		File file = new File(m_tempDir, "y.txt");
		Files.writeString(file.toPath(), "from-file", StandardCharsets.UTF_8);
		CommandVariable var = new CommandVariable("y", file);

		Assertions.assertEquals("from-file", var.getValue());
		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("from-file", var.getValue());
	}

	// ---------- CommandExecution 연동 ----------

	@Test
	public void value_only_variable_path_in_command_line() throws Exception {
		Assumptions.assumeTrue(new File("/bin/cat").canExecute(), "/bin/cat required");

		File outFile = new File(m_tempDir, "out.txt");
		CommandVariable var = new CommandVariable("msg", "via-temp-file");
		try ( CommandExecution exec = CommandExecution.builder()
														.addCommand("/bin/cat", "${msg:path}")
														.workingDirectory(m_tempDir)
														.addVariable(var)
														.stdout(Output.file(outFile))
														.build() ) {
			exec.start();
			var result = exec.waitForFinished();
			Assertions.assertEquals(utils.async.AsyncState.COMPLETED, result.getState(),
									() -> "cause: " + result.getFailureCause());
			Assertions.assertEquals("via-temp-file", Files.readString(outFile.toPath(), StandardCharsets.UTF_8));
		}
		Assertions.assertFalse(var.getFile().exists(), "temp file must be deleted when the execution is closed");
	}

	// ---------- 파일 모드 값 읽기 ----------

	@Test
	public void file_variable_value_is_reread_until_closed() throws Exception {
		// 예전에는 처음 읽은 값이 고정되어, 명령이 끝나기 전에 출력 변수를 읽으면 빈 값이 남았다.
		File file = new File(m_tempDir, "out.txt");
		CommandVariable var = new CommandVariable("out", "", file);
		var.getValueByModifier("path");			// 파일 모드로 전환 (빈 파일 생성)
		Assertions.assertEquals("", var.getValue());

		Files.writeString(file.toPath(), "result", StandardCharsets.UTF_8);	// 명령이 결과를 기록
		Assertions.assertEquals("result", var.getValue());

		var.close();
		Assertions.assertEquals("result", var.getValue(), "last read value after close");
	}

	@Test
	public void close_keeps_value_of_unread_file() throws Exception {
		// close() 전에 읽지 않았더라도 close()가 읽어 둔 값을 이후에 얻을 수 있어야 한다.
		// (RESTfulAsyncRpcServer는 실행이 끝나면 곧바로 close하고 출력은 나중에 읽는다.)
		File file = new File(m_tempDir, "late.txt");
		Files.writeString(file.toPath(), "read-by-close", StandardCharsets.UTF_8);
		CommandVariable var = new CommandVariable("late", file);

		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("read-by-close", var.getValue());
	}

	@Test
	public void close_with_missing_file_does_not_fail_but_value_is_unavailable() throws Exception {
		// 명령이 결과 파일을 만들지 않은 경우: close()는 실패하지 않고, 값은 얻을 수 없다.
		CommandVariable var = new CommandVariable("missing", new File(m_tempDir, "missing.txt"));

		var.close();
		Assertions.assertThrows(IllegalStateException.class, var::getValue);
	}
}
