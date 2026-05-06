package utils.async.command;


import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import utils.async.command.CommandVariable.FileVariable;
import utils.async.command.CommandVariable.StringVariable;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariableTest {
	private File m_tempFile;

	@Before
	public void setup() throws IOException {
		m_tempFile = Files.createTempFile("cmdvar-test-", ".txt").toFile();
		Files.writeString(m_tempFile.toPath(), "hello world", StandardCharsets.UTF_8);
	}

	@After
	public void cleanup() {
		if ( m_tempFile != null && m_tempFile.exists() ) {
			m_tempFile.delete();
		}
	}

	// ----- StringVariable -----

	@Test
	public void testStringVariableBasic() {
		StringVariable var = new StringVariable("foo", "bar");
		Assert.assertEquals("foo", var.getName());
		Assert.assertEquals("bar", var.getValue());
		Assert.assertEquals("foo", var.key());
	}

	@Test
	public void testStringVariableEmptyValueAllowed() {
		StringVariable var = new StringVariable("foo", "");
		Assert.assertEquals("", var.getValue());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringVariableNullNameRejected() {
		new StringVariable(null, "bar");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringVariableNullValueRejected() {
		new StringVariable("foo", null);
	}

	@Test
	public void testStringVariableModifierName() {
		StringVariable var = new StringVariable("foo", "bar");
		Assert.assertEquals("foo", var.getValueByModifier("name"));
	}

	@Test
	public void testStringVariableModifierValue() {
		StringVariable var = new StringVariable("foo", "bar");
		Assert.assertEquals("bar", var.getValueByModifier("value"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringVariableModifierPathUnsupported() {
		// "path"는 StringVariable에서 지원하지 않는다 (FileVariable 한정).
		new StringVariable("foo", "bar").getValueByModifier("path");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringVariableModifierUnknownRejected() {
		new StringVariable("foo", "bar").getValueByModifier("unknown");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringVariableModifierNullRejected() {
		new StringVariable("foo", "bar").getValueByModifier(null);
	}

	@Test
	public void testStringVariableModifierIsCaseSensitive() {
		StringVariable var = new StringVariable("foo", "bar");
		try {
			var.getValueByModifier("Name");
			Assert.fail("'Name'은 대소문자가 달라 미지원이어야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	@Test
	public void testStringVariableCloseIsNoOp() {
		StringVariable var = new StringVariable("foo", "bar");
		var.close();
		// close 후에도 getName/getValue 정상 동작.
		Assert.assertEquals("foo", var.getName());
		Assert.assertEquals("bar", var.getValue());
	}

	@Test
	public void testStringVariableToString() {
		StringVariable var = new StringVariable("foo", "bar");
		String s = var.toString();
		Assert.assertTrue("'" + s + "' should contain name", s.contains("foo"));
		Assert.assertTrue("'" + s + "' should contain value", s.contains("bar"));
	}

	// ----- FileVariable -----

	@Test
	public void testFileVariableBasic() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals("doc", var.getName());
		Assert.assertEquals("doc", var.key());
		Assert.assertSame(m_tempFile, var.getFile());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFileVariableNullNameRejected() {
		new FileVariable(null, m_tempFile);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFileVariableNullFileRejected() {
		new FileVariable("doc", null);
	}

	@Test
	public void testFileVariableGetValueReadsFile() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableGetValueIsCached() throws IOException {
		FileVariable var = new FileVariable("doc", m_tempFile);

		// 첫 호출 — 파일 내용을 읽는다.
		String first = var.getValue();
		Assert.assertEquals("hello world", first);

		// 외부에서 파일 내용을 변경.
		Files.writeString(m_tempFile.toPath(), "different content", StandardCharsets.UTF_8);

		// 두 번째 호출 — 캐시된 값이 반환되어야 한다.
		Assert.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableModifierName() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals("doc", var.getValueByModifier("name"));
	}

	@Test
	public void testFileVariableModifierValue() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals("hello world", var.getValueByModifier("value"));
	}

	@Test
	public void testFileVariableModifierPath() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals(m_tempFile.getAbsolutePath(), var.getValueByModifier("path"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFileVariableModifierUnknownRejected() {
		new FileVariable("doc", m_tempFile).getValueByModifier("unknown");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFileVariableModifierNullRejected() {
		// "path".equals(null)은 false이므로 super.getValueByModifier(null)에서 검증된다.
		new FileVariable("doc", m_tempFile).getValueByModifier(null);
	}

	@Test
	public void testFileVariableCloseDeletesFile() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertTrue(m_tempFile.exists());

		var.close();
		Assert.assertFalse("close() 후 파일이 삭제되어야 한다", m_tempFile.exists());
	}

	@Test
	public void testFileVariableCloseIsIdempotent() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		var.close();
		// 두 번째 close는 파일이 이미 없어도 예외 없이 처리된다.
		var.close();
		Assert.assertFalse(m_tempFile.exists());
	}

	@Test
	public void testFileVariableGetValueAfterCloseFails() {
		// close() 후 첫 read 시도 → 파일이 없어 RuntimeException.
		FileVariable var = new FileVariable("doc", m_tempFile);
		var.close();

		try {
			var.getValue();
			Assert.fail("close 후 getValue는 예외를 던져야 한다");
		}
		catch ( RuntimeException expected ) {
			Assert.assertTrue(expected.getCause() instanceof IOException);
		}
	}

	@Test
	public void testFileVariableGetValueAfterCloseReturnsCachedIfReadFirst() {
		// 캐시된 후 close하면 캐시가 그대로 유지된다.
		FileVariable var = new FileVariable("doc", m_tempFile);
		Assert.assertEquals("hello world", var.getValue());   // 캐시.

		var.close();
		Assert.assertFalse(m_tempFile.exists());

		// 캐시된 값은 여전히 반환 가능.
		Assert.assertEquals("hello world", var.getValue());
	}

	@Test
	public void testFileVariableGetValueOnMissingFile() throws IOException {
		// 생성 시 파일이 없는 경우 첫 getValue에서 RuntimeException.
		File missing = new File(m_tempFile.getParentFile(), "missing-cmdvar-test.txt");
		Assert.assertFalse(missing.exists());

		FileVariable var = new FileVariable("missing", missing);
		try {
			var.getValue();
			Assert.fail("missing file에 대해 예외를 던져야 한다");
		}
		catch ( RuntimeException expected ) {
			Assert.assertTrue(expected.getCause() instanceof IOException);
		}
	}

	@Test
	public void testFileVariableToString() {
		FileVariable var = new FileVariable("doc", m_tempFile);
		String s = var.toString();
		Assert.assertTrue(s.contains("doc"));
		Assert.assertTrue(s.contains(m_tempFile.getAbsolutePath()));
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

		Assert.assertEquals("x", var.getValueByModifier("name"));
		Assert.assertEquals("y", var.getValueByModifier("value"));
		// "path"는 default가 지원하지 않음.
		try {
			var.getValueByModifier("path");
			Assert.fail("'path'는 default 구현이 지원하지 않아야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	@Test
	public void testKeyDefaultEqualsName() {
		// Keyed 인터페이스 default 동작.
		Assert.assertEquals("k", new StringVariable("k", "v").key());
	}

}
