package utils.async.command;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.async.command.CommandVariable.FileVariable;
import utils.async.command.CommandVariable.StringVariable;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariableTest {
	private File m_tempFile;

	@BeforeEach
	public void setup() throws IOException {
		m_tempFile = Files.createTempFile("cmdvar-test-", ".txt").toFile();
		Files.writeString(m_tempFile.toPath(), "hello world", StandardCharsets.UTF_8);
	}

	@AfterEach
	public void cleanup() {
		if ( m_tempFile != null && m_tempFile.exists() ) {
			m_tempFile.delete();
		}
	}

	// ----- StringVariable -----

	@Test
	public void testStringVariableBasic() {
		StringVariable var = new StringVariable("foo", "bar");
		Assertions.assertEquals("foo", var.getName());
		Assertions.assertEquals("bar", var.getValue());
		Assertions.assertEquals("foo", var.key());
	}

	@Test
	public void testStringVariableEmptyValueAllowed() {
		StringVariable var = new StringVariable("foo", "");
		Assertions.assertEquals("", var.getValue());
	}

	@Test
	public void testStringVariableNullNameRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new StringVariable(null, "bar");
			});
	}

	@Test
	public void testStringVariableNullValueRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new StringVariable("foo", null);
			});
	}

	@Test
	public void testStringVariableModifierName() {
		StringVariable var = new StringVariable("foo", "bar");
		Assertions.assertEquals("foo", var.getValueByModifier("name"));
	}

	@Test
	public void testStringVariableModifierValue() {
		StringVariable var = new StringVariable("foo", "bar");
		Assertions.assertEquals("bar", var.getValueByModifier("value"));
	}

	@Test
	public void testStringVariableModifierPathUnsupported() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			// "path"는 StringVariable에서 지원하지 않는다 (FileVariable 한정).
			new StringVariable("foo", "bar").getValueByModifier("path");
			});
	}

	@Test
	public void testStringVariableModifierUnknownRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new StringVariable("foo", "bar").getValueByModifier("unknown");
			});
	}

	@Test
	public void testStringVariableModifierNullRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new StringVariable("foo", "bar").getValueByModifier(null);
			});
	}

	@Test
	public void testStringVariableModifierIsCaseSensitive() {
		StringVariable var = new StringVariable("foo", "bar");
		try {
			var.getValueByModifier("Name");
			Assertions.fail("'Name'은 대소문자가 달라 미지원이어야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	@Test
	public void testStringVariableCloseIsNoOp() {
		StringVariable var = new StringVariable("foo", "bar");
		var.close();
		// close 후에도 getName/getValue 정상 동작.
		Assertions.assertEquals("foo", var.getName());
		Assertions.assertEquals("bar", var.getValue());
	}

	@Test
	public void testStringVariableToString() {
		StringVariable var = new StringVariable("foo", "bar");
		String s = var.toString();
		Assertions.assertTrue(s.contains("foo"), "'" + s + "' should contain name");
		Assertions.assertTrue(s.contains("bar"), "'" + s + "' should contain value");
	}

	// ----- FileVariable -----

	@Test
	public void testFileVariableBasic() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals("doc", var.getName());
		Assertions.assertEquals("doc", var.key());
		Assertions.assertSame(m_tempFile, var.getFile());
	}

	@Test
	public void testFileVariableNullNameRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FileVariable(null, m_tempFile);
			});
	}

	@Test
	public void testFileVariableNullFileRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FileVariable("doc", null);
			});
	}

	@Test
	public void testFileVariableGetValueReadsFile() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableGetValueIsCached() throws IOException {
		FileVariable var = new FileVariable("doc", m_tempFile);

		// 첫 호출 — 파일 내용을 읽는다.
		String first = var.getValue();
		Assertions.assertEquals("hello world", first);

		// 외부에서 파일 내용을 변경.
		Files.writeString(m_tempFile.toPath(), "different content", StandardCharsets.UTF_8);

		// 두 번째 호출 — 캐시된 값이 반환되어야 한다.
		Assertions.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableModifierName() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals("doc", var.getValueByModifier("name"));
	}

	@Test
	public void testFileVariableModifierValue() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals("hello world", var.getValueByModifier("value"));
	}

	@Test
	public void testFileVariableModifierPath() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals(m_tempFile.getAbsolutePath(), var.getValueByModifier("path"));
	}

	@Test
	public void testFileVariableModifierUnknownRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			new FileVariable("doc", m_tempFile).getValueByModifier("unknown");
			});
	}

	@Test
	public void testFileVariableModifierNullRejected() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			// "path".equals(null)은 false이므로 super.getValueByModifier(null)에서 검증된다.
			new FileVariable("doc", m_tempFile).getValueByModifier(null);
			});
	}

	@Test
	public void testFileVariableCloseDeletesFile() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertTrue(m_tempFile.exists());

		var.close();
		Assertions.assertFalse(m_tempFile.exists(), "close() 후 파일이 삭제되어야 한다");
	}

	@Test
	public void testFileVariableCloseIsIdempotent() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		var.close();
		// 두 번째 close는 파일이 이미 없어도 예외 없이 처리된다.
		var.close();
		Assertions.assertFalse(m_tempFile.exists());
	}

	@Test
	public void testFileVariableGetValueAfterCloseFails() {
		// close() 후 첫 read 시도 → 파일이 없어 RuntimeException.
		FileVariable var = new FileVariable("doc", m_tempFile);
		var.close();

		try {
			var.getValue();
			Assertions.fail("close 후 getValue는 예외를 던져야 한다");
		}
		catch ( RuntimeException expected ) {
			Assertions.assertTrue(expected.getCause() instanceof IOException);
		}
	}

	@Test
	public void testFileVariableGetValueAfterCloseReturnsCachedIfReadFirst() {
		// 캐시된 후 close하면 캐시가 그대로 유지된다.
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assertions.assertEquals("hello world", var.getValue());   // 캐시.

		var.close();
		Assertions.assertFalse(m_tempFile.exists());

		// 캐시된 값은 여전히 반환 가능.
		Assertions.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableGetValueOnMissingFile() throws IOException {
		// 생성 시 파일이 없는 경우 첫 getValue에서 RuntimeException.
		File missing = new File(m_tempFile.getParentFile(), "missing-cmdvar-test.txt");
		Assertions.assertFalse(missing.exists());

		FileVariable var = new FileVariable("missing", missing);
		try {
			var.getValue();
			Assertions.fail("missing file에 대해 예외를 던져야 한다");
		}
		catch ( RuntimeException expected ) {
			Assertions.assertTrue(expected.getCause() instanceof IOException);
		}
	}

	@Test
	public void testFileVariableToString() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		String s = var.toString();
		Assertions.assertTrue(s.contains("doc"));
		Assertions.assertTrue(s.contains(m_tempFile.getAbsolutePath()));
	}

	// ----- Interface default 동작 직접 검증 -----

	@Test
	public void testInterfaceDefaultModifierForCustomImpl() {
		// FileVariable/StringVariable이 아닌 임시 구현체로 default 동작을 검증.
		CommandVariable var = new CommandVariable() {
			@Override public String getName() { return "x"; }
			@Override public String getValue() { return "y"; }
			@Override public void close() { }
		};

		Assertions.assertEquals("x", var.getValueByModifier("name"));
		Assertions.assertEquals("y", var.getValueByModifier("value"));
		// "path"는 default가 지원하지 않음.
		try {
			var.getValueByModifier("path");
			Assertions.fail("'path'는 default 구현이 지원하지 않아야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	@Test
	public void testKeyDefaultEqualsName() {
		// Keyed 인터페이스 default 동작.
		Assertions.assertEquals(new StringVariable("k", "v").key(), "k");
	}

}
