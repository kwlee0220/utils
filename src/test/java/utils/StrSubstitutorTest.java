package utils;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StrSubstitutorTest {

	@Rule
	public TemporaryFolder tmpFolder = new TemporaryFolder();

	// ----- 기본 치환 (기존 testBasic) -----

	@Test
	public void testBasic() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "3");

		StrSubstitutor subst = StrSubstitutor.with(facts).failOnUndefinedVariable(false);

		Assert.assertEquals("ABC", subst.replace("ABC"));
		Assert.assertEquals("1", subst.replace("${A}"));
		Assert.assertEquals("2", subst.replace("${B}"));
		Assert.assertEquals("3", subst.replace("${C}"));
		Assert.assertEquals("1-2-3", subst.replace("${A}-${B}-${C}"));
		Assert.assertEquals("${D}", subst.replace("${D}"));
	}

	@Test
	public void testNoSubstitutionWhenNoTokens() {
		StrSubstitutor subst = StrSubstitutor.with(Map.of("a", "1"));
		Assert.assertEquals("plain text", subst.replace("plain text"));
	}

	@Test
	public void testNullTemplateReturnsNull() {
		Assert.assertNull(StrSubstitutor.with(Map.of()).replace(null));
	}

	@Test
	public void testEmptyTemplateReturnsEmpty() {
		Assert.assertEquals("", StrSubstitutor.with(Map.of()).replace(""));
	}

	// ----- 미정의 변수 (기존 testSymbolNotFound 확장) -----

	@Test(expected = IllegalArgumentException.class)
	public void testSymbolNotFound() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "3");

		StrSubstitutor subst = StrSubstitutor.with(facts);
		subst.failOnUndefinedVariable(true);
		subst.replace("${D}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUndefinedVariableThrowsByDefault() {
		StrSubstitutor.with(Map.of()).replace("${missing}");
	}

	@Test
	public void testUndefinedVariableLeftWhenDisabled() {
		StrSubstitutor subst = StrSubstitutor.with(Map.of()).failOnUndefinedVariable(false);
		Assert.assertEquals("hello ${missing}", subst.replace("hello ${missing}"));
	}

	@Test
	public void testFailOnUndefinedVariableReenable() {
		StrSubstitutor subst = StrSubstitutor.with(Map.of())
									.failOnUndefinedVariable(false)
									.failOnUndefinedVariable(true);
		try {
			subst.replace("${x}");
			Assert.fail("재활성화 후 IAE를 던져야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	// ----- 중첩 치환 (기존 testNested 확장) -----

	@Test
	public void testNested() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "${A}a", "C", "B");

		StrSubstitutor subst = StrSubstitutor.with(facts);
		subst.enableNestedSubstitution(false);
		Assert.assertEquals("${A}a", subst.replace("${B}"));

		subst.enableNestedSubstitution(true);
		Assert.assertEquals("1a", subst.replace("${B}"));
	}

	@Test
	public void testNestedSubstitutionInVariableNames() {
		// 변수 이름 자체에 참조 — ${${key}} 형태.
		StrSubstitutor subst = StrSubstitutor.with(Map.of("key", "name", "name", "Bob"));
		Assert.assertEquals("Bob", subst.replace("${${key}}"));
	}

	// ----- Apache 기본값 시맨틱 (기존 testFallback) -----

	@Test
	public void testFallback() throws Exception {
		Map<String,String> facts = Map.of("A", "1", "B", "2", "C", "${A:-K}");

		StrSubstitutor subst = StrSubstitutor.with(facts);
		Assert.assertEquals("1", subst.replace("${C}"));

		facts = Map.of("B", "2", "C", "${A:-K}");
		subst = StrSubstitutor.with(facts);
		Assert.assertEquals("K", subst.replace("${C}"));
	}

	// ----- 내장 lookup (sys/env) -----

	@Test
	public void testSysLookup() {
		System.setProperty("strsubst.test.prop", "myvalue");
		try {
			Assert.assertEquals("myvalue", StrSubstitutor.with(Map.of()).replace("${sys:strsubst.test.prop}"));
		}
		finally {
			System.clearProperty("strsubst.test.prop");
		}
	}

	@Test
	public void testNoArgConstructorUsesBuiltinLookup() {
		// 내장 lookup만 사용 (사용자 키 없음).
		String userHome = System.getProperty("user.home");
		Assert.assertEquals(userHome, StrSubstitutor.with(Map.of()).replace("${sys:user.home}"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUndefinedSysLookupThrows() {
		StrSubstitutor.with(Map.of()).replace("${sys:strsubst.test.nonexistent}");
	}

	@Test
	public void testUserKeyAndSysLookupCoexist() {
		// 사용자 키 ${X}와 내장 ${sys:user.home}이 함께 사용 가능.
		StrSubstitutor subst = StrSubstitutor.with(Map.of("greeting", "hi"));
		String userHome = System.getProperty("user.home");
		Assert.assertEquals("hi at " + userHome, subst.replace("${greeting} at ${sys:user.home}"));
	}

	// ----- 생성자 / null 검증 -----

	@Test(expected = IllegalArgumentException.class)
	public void testNullKeyValuesRejected() {
		StrSubstitutor.with(null);
	}

	// ----- 맵 mutation 반영 (참조 보관 시맨틱) -----

	@Test
	public void testMapMutationReflectedInSubsequentReplace() {
		// 생성자에 전달된 맵을 mutate하면 이후 replace에 반영된다 (Javadoc 명시).
		HashMap<String,String> map = new HashMap<>();
		map.put("k", "v1");

		StrSubstitutor subst = StrSubstitutor.with(map);
		Assert.assertEquals("v1", subst.replace("${k}"));

		map.put("k", "v2");
		Assert.assertEquals("v2", subst.replace("${k}"));
	}

	// ----- replaceIncrementally -----

	@Test
	public void testReplaceIncrementallyBasic() {
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("A", "1"),
				KeyValue.of("B", "${A}-2"),
				KeyValue.of("C", "${B}-3")
		);
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of()).replaceIncrementally(kvs, Map.of());

		Assert.assertEquals("1", result.get("A"));
		Assert.assertEquals("1-2", result.get("B"));
		Assert.assertEquals("1-2-3", result.get("C"));
	}

	@Test
	public void testReplaceIncrementallyWithFacts() {
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("greeting", "hello, ${name}"),
				KeyValue.of("loud", "${greeting}!")
		);
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of())
				.replaceIncrementally(kvs, Map.of("name", "Alice"));

		Assert.assertEquals("hello, Alice", result.get("greeting"));
		Assert.assertEquals("hello, Alice!", result.get("loud"));
	}

	@Test
	public void testReplaceIncrementallyPreservesInputOrder() {
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("z", "1"),
				KeyValue.of("a", "2"),
				KeyValue.of("m", "3")
		);
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of()).replaceIncrementally(kvs, Map.of());

		Assert.assertEquals(Arrays.asList("z", "a", "m"), List.copyOf(result.keySet()));
	}

	@Test
	public void testReplaceIncrementallyDuplicateKeyLastWins() {
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("x", "first"),
				KeyValue.of("x", "second")
		);
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of()).replaceIncrementally(kvs, Map.of());

		Assert.assertEquals("second", result.get("x"));
		Assert.assertEquals(1, result.size());
	}

	@Test
	public void testReplaceIncrementallyDoesNotMutateFacts() {
		HashMap<String,String> facts = new HashMap<>();
		facts.put("base", "v");
		int sizeBefore = facts.size();

		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("a", "${base}-a"),
				KeyValue.of("b", "${a}-b")
		);
		StrSubstitutor.with(Map.of()).replaceIncrementally(kvs, facts);

		// 원본 facts 맵은 변경되지 않아야 한다.
		Assert.assertEquals(sizeBefore, facts.size());
		Assert.assertEquals("v", facts.get("base"));
		Assert.assertFalse(facts.containsKey("a"));
		Assert.assertFalse(facts.containsKey("b"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReplaceIncrementallyUndefinedVariableFailsByDefault() {
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("a", "${missing}")
		);
		StrSubstitutor.with(Map.of()).replaceIncrementally(kvs, Map.of());
	}

	@Test
	public void testReplaceIncrementallyInheritsFailOnUndefinedSetting() {
		// 인스턴스에 failOnUndefined(false)를 설정하면 incremental에도 적용되어 미정의는 그대로 남는다.
		List<KeyValue<String,String>> kvs = Arrays.asList(
				KeyValue.of("a", "${missing}"),
				KeyValue.of("b", "${a}-b")
		);
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of())
				.failOnUndefinedVariable(false)
				.replaceIncrementally(kvs, Map.of());

		Assert.assertEquals("${missing}", result.get("a"));
		// b는 a를 참조 — a는 이미 누적 맵에 들어갔으므로 ${missing}으로 풀림.
		Assert.assertEquals("${missing}-b", result.get("b"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReplaceIncrementallyNullKeyValuesRejected() {
		StrSubstitutor.with(Map.of()).replaceIncrementally(null, Map.of());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReplaceIncrementallyNullFactsRejected() {
		StrSubstitutor.with(Map.of()).replaceIncrementally(List.of(), null);
	}

	@Test
	public void testReplaceIncrementallyEmptyInputReturnsEmptyResult() {
		LinkedHashMap<String,String> result = StrSubstitutor.with(Map.of())
				.replaceIncrementally(List.of(), Map.of("a", "1"));

		Assert.assertTrue(result.isEmpty());
	}

	// ----- chaining 동작 -----

	@Test
	public void testFluentChainReturnsSameInstance() {
		StrSubstitutor subst = StrSubstitutor.with(Map.of("a", "1"));
		Assert.assertSame(subst, subst.failOnUndefinedVariable(false));
		Assert.assertSame(subst, subst.enableNestedSubstitution(false));
	}

	// ----- with(String, String) 단일 키-값 팩토리 -----

	@Test
	public void testWithSingleKeyValue() {
		StrSubstitutor subst = StrSubstitutor.with("greeting", "hello");
		Assert.assertEquals("hello, world", subst.replace("${greeting}, world"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testWithSingleKeyValueNullKeyRejected() {
		StrSubstitutor.with(null, "v");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testWithSingleKeyValueNullValueRejected() {
		StrSubstitutor.with("k", null);
	}

	// ----- replace(File, File) -----

	@Test
	public void testReplaceFileBasic() throws Exception {
		File template = tmpFolder.newFile("in.txt");
		File output = tmpFolder.newFile("out.txt");
		Files.writeString(template.toPath(), "hello, ${name}!");

		StrSubstitutor.with("name", "Alice").replace(template, output);

		Assert.assertEquals("hello, Alice!", Files.readString(output.toPath()));
	}

	@Test
	public void testReplaceFileInPlace() throws Exception {
		File file = tmpFolder.newFile("inplace.txt");
		Files.writeString(file.toPath(), "value=${v}");

		StrSubstitutor.with("v", "42").replace(file, file);

		Assert.assertEquals("value=42", Files.readString(file.toPath()));
	}

	@Test
	public void testReplaceFileRespectsInstanceFailOnUndefined() throws Exception {
		File template = tmpFolder.newFile("undef.txt");
		Files.writeString(template.toPath(), "${missing}");

		// lenient 모드 — 미정의 토큰이 그대로 남아야 함.
		File outLenient = tmpFolder.newFile("lenient.txt");
		StrSubstitutor.with(Map.of()).failOnUndefinedVariable(false).replace(template, outLenient);
		Assert.assertEquals("${missing}", Files.readString(outLenient.toPath()));

		// 엄격 모드 — IAE 발생.
		File outStrict = tmpFolder.newFile("strict.txt");
		try {
			StrSubstitutor.with(Map.of()).failOnUndefinedVariable(true).replace(template, outStrict);
			Assert.fail("엄격 모드에서 IAE를 던져야 한다");
		}
		catch ( IllegalArgumentException expected ) { }
	}

	@Test
	public void testReplaceFileFailureLeavesOutputUnchanged() throws Exception {
		File template = tmpFolder.newFile("bad.txt");
		Files.writeString(template.toPath(), "${missing}");

		File output = tmpFolder.newFile("preexisting.txt");
		Files.writeString(output.toPath(), "ORIGINAL");

		try {
			StrSubstitutor.with(Map.of()).failOnUndefinedVariable(true).replace(template, output);
			Assert.fail("IAE를 던져야 한다");
		}
		catch ( IllegalArgumentException expected ) { }

		// atomic-move 보장 — 실패 시 출력 파일은 원본 그대로.
		Assert.assertEquals("ORIGINAL", Files.readString(output.toPath()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReplaceFileNullTemplateRejected() throws Exception {
		File output = tmpFolder.newFile("out.txt");
		StrSubstitutor.with(Map.of()).replace(null, output);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testReplaceFileNullOutputRejected() throws Exception {
		File template = tmpFolder.newFile("in.txt");
		StrSubstitutor.with(Map.of()).replace(template, null);
	}
}
