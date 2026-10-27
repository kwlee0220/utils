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
		CommandVariable var = CommandVariable.input("x", "hello");
		var.close();

		Assertions.assertNull(var.getFile());
		Assertions.assertEquals("hello", var.getValue());
	}

	@Test
	public void value_only_variable_path_creates_temp_file_with_value() throws Exception {
		// 예전에는 m_file이 null이라 ${x:path}가 NullPointerException으로 실패했다.
		CommandVariable var = CommandVariable.input("x", "hello 한글");

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

		CommandVariable var = CommandVariable.input("secret", "TOP-SECRET");
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
		CommandVariable var = CommandVariable.input("x", "hello", file);

		Assertions.assertEquals(file.getAbsolutePath(), var.getValueByModifier("path"));
		Assertions.assertEquals("hello", Files.readString(file.toPath(), StandardCharsets.UTF_8));
		Assertions.assertEquals("hello", var.getValue());

		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("hello", var.getValue());
	}

	@Test
	public void input_file_is_read_and_not_deleted_on_close() throws Exception {
		// inputFile()의 파일은 호출자 소유이므로 close()가 지우지 않아야 한다. (예전 생성자는 사용자 파일도 지웠다.)
		File file = new File(m_tempDir, "y.txt");
		Files.writeString(file.toPath(), "from-file", StandardCharsets.UTF_8);
		CommandVariable var = CommandVariable.inputFile("y", file);

		Assertions.assertEquals("from-file", var.getValue());
		Assertions.assertEquals(file.getAbsolutePath(), var.getValueByModifier("path"));
		var.close();
		Assertions.assertTrue(file.exists(), "caller-owned input file must survive close()");
		Assertions.assertEquals("from-file", var.getValue());
	}

	@Test
	public void input_file_with_delete_on_close_is_deleted() throws Exception {
		File file = new File(m_tempDir, "copy.txt");
		Files.writeString(file.toPath(), "copied", StandardCharsets.UTF_8);
		CommandVariable var = CommandVariable.inputFile("copy", file, true);

		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("copied", var.getValue());
	}

	@Test
	public void input_value_overwrites_existing_file_on_path() throws Exception {
		// 예전에는 파일이 이미 있으면 주어진 값이 조용히 무시되고 기존 파일 내용이 값이 되었다.
		File file = new File(m_tempDir, "v.txt");
		Files.writeString(file.toPath(), "stale", StandardCharsets.UTF_8);
		CommandVariable var = CommandVariable.input("v", "fresh", file);

		var.getValueByModifier("path");
		Assertions.assertEquals("fresh", Files.readString(file.toPath(), StandardCharsets.UTF_8));
		Assertions.assertEquals("fresh", var.getValue());
		var.close();
	}

	@Test
	public void output_deletes_stale_file_on_creation() throws Exception {
		// 이전 실행이 남긴 출력 파일을 이번 결과로 읽지 않도록 생성 시 지운다.
		File file = new File(m_tempDir, "result.txt");
		Files.writeString(file.toPath(), "previous-run", StandardCharsets.UTF_8);
		CommandVariable var = CommandVariable.output("result", file);

		Assertions.assertFalse(file.exists());
		Assertions.assertThrows(RuntimeException.class, var::getValue, "no output written yet");

		Files.writeString(file.toPath(), "this-run", StandardCharsets.UTF_8);	// 명령이 결과를 기록
		Assertions.assertEquals("this-run", var.getValue());
		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("this-run", var.getValue());
	}

	@Test
	public void factories_reject_null_arguments() {
		File file = new File(m_tempDir, "f.txt");
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.input(null, "v"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.input("n", null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.input("n", "v", null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.inputFile("n", null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.output("n", null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> CommandVariable.input(null, "v", file));
	}

	// ---------- CommandExecution 연동 ----------

	@Test
	public void value_only_variable_path_in_command_line() throws Exception {
		Assumptions.assumeTrue(new File("/bin/cat").canExecute(), "/bin/cat required");

		File outFile = new File(m_tempDir, "out.txt");
		CommandVariable var = CommandVariable.input("msg", "via-temp-file");
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
		CommandVariable var = CommandVariable.input("out", "", file);
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
		CommandVariable var = CommandVariable.inputFile("late", file, true);

		var.close();
		Assertions.assertFalse(file.exists());
		Assertions.assertEquals("read-by-close", var.getValue());
	}

	@Test
	public void close_with_missing_file_does_not_fail_but_value_is_unavailable() throws Exception {
		// 명령이 결과 파일을 만들지 않은 경우: close()는 실패하지 않고, 값은 얻을 수 없다.
		CommandVariable var = CommandVariable.output("missing", new File(m_tempDir, "missing.txt"));

		var.close();
		Assertions.assertThrows(IllegalStateException.class, var::getValue);
	}

	// ----- close 이후 -----

	@Test
	public void path_after_close_is_rejected() throws Exception {
		// 닫힌 변수가 파일을 다시 만들면 그 파일은 정리되지 않으므로 거부한다. 값 읽기는 계속 가능하다.
		CommandVariable var = CommandVariable.input("payload", "sample");
		var.close();
		Assertions.assertThrows(IllegalStateException.class, () -> var.getValueByModifier("path"));
		Assertions.assertEquals("sample", var.getValue());
		Assertions.assertEquals("payload", var.getValueByModifier("name"));
	}

	@Test
	public void try_with_resources_deletes_owned_file() throws Exception {
		File file;
		try ( CommandVariable var = CommandVariable.input("payload", "sample") ) {
			file = new File(var.getValueByModifier("path"));
			Assertions.assertTrue(file.exists());
		}
		Assertions.assertFalse(file.exists());
	}
}
