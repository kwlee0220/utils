package utils;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitTest {

	// ----- split(String, String) -----

	@Test
	public void testSplitDelimInMiddle() {
		Split s = Split.split("a/b", "/");
		Assert.assertEquals("a", s.head());
		Assert.assertEquals(Optional.of("b"), s.tail());
	}

	@Test
	public void testSplitDelimNotFound() {
		Split s = Split.split("abc", "/");
		Assert.assertEquals("abc", s.head());
		Assert.assertEquals(Optional.empty(), s.tail());
	}

	@Test
	public void testSplitDelimAtStart() {
		Split s = Split.split("/abc", "/");
		Assert.assertEquals("", s.head());
		Assert.assertEquals(Optional.of("abc"), s.tail());
	}

	@Test
	public void testSplitDelimAtEnd() {
		// 구분자가 끝에 있으면 tail은 Optional.of("") — empty와 구분된다.
		Split s = Split.split("abc/", "/");
		Assert.assertEquals("abc", s.head());
		Assert.assertEquals(Optional.of(""), s.tail());
	}

	@Test
	public void testSplitOnlyFirstOccurrence() {
		// 첫 등장 구분자만 분할. 이후 구분자는 tail에 그대로 남는다.
		Split s = Split.split("a/b/c", "/");
		Assert.assertEquals("a", s.head());
		Assert.assertEquals(Optional.of("b/c"), s.tail());
	}

	@Test
	public void testSplitMultiCharDelim() {
		Split s = Split.split("abXYcdXYef", "XY");
		Assert.assertEquals("ab", s.head());
		Assert.assertEquals(Optional.of("cdXYef"), s.tail());
	}

	@Test
	public void testSplitEmptyStringInput() {
		// 빈 문자열에서는 어떤 구분자도 찾을 수 없으므로 head="" + tail=empty.
		Split s = Split.split("", "/");
		Assert.assertEquals("", s.head());
		Assert.assertEquals(Optional.empty(), s.tail());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitNullStrRejected() {
		Split.split(null, "/");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitNullDelimRejected() {
		Split.split("abc", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitEmptyDelimRejected() {
		Split.split("abc", "");
	}

	// ----- splitTail(String) -----

	@Test
	public void testSplitTailDelimFound() {
		Split first = Split.split("a/b/c", "/");
		Split second = first.splitTail("/");
		Assert.assertEquals("b", second.head());
		Assert.assertEquals(Optional.of("c"), second.tail());
	}

	@Test
	public void testSplitTailDelimNotFound() {
		// tail이 "b" 인 상태에서 다시 분할 — 구분자 없으니 head=b, tail=empty.
		Split first = Split.split("a/b", "/");
		Split second = first.splitTail("/");
		Assert.assertEquals("b", second.head());
		Assert.assertEquals(Optional.empty(), second.tail());
	}

	@Test(expected = NoSuchElementException.class)
	public void testSplitTailWhenTailAbsent() {
		// 원래 분할에서 구분자 없었으니 tail이 absent → NoSuchElementException.
		Split first = Split.split("abc", "/");
		first.splitTail("/");
	}

	@Test
	public void testSplitTailDifferentDelim() {
		// 첫 분할은 ":", 두 번째 분할은 "=".
		Split first = Split.split("k:v=42", ":");
		Split second = first.splitTail("=");
		Assert.assertEquals("v", second.head());
		Assert.assertEquals(Optional.of("42"), second.tail());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitTailNullDelimRejected() {
		// tail 상태와 무관하게 진입 시점에 검증되어야 한다.
		Split first = Split.split("a/b", "/");
		first.splitTail(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitTailEmptyDelimRejected() {
		Split first = Split.split("a/b", "/");
		first.splitTail("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitTailNullDelimRejectedEvenWhenTailAbsent() {
		// tail이 absent여도 NoSuchElementException 이전에 IAE가 발생해야 한다 (입구 검증).
		Split first = Split.split("abc", "/");
		first.splitTail(null);
	}

	// ----- splitLast(String, String) -----

	@Test
	public void testSplitLastDelimFound() {
		// 마지막 '/'를 기준으로 분할.
		Split s = Split.splitLast("a/b/c", "/");
		Assert.assertEquals("a/b", s.head());
		Assert.assertEquals(Optional.of("c"), s.tail());
	}

	@Test
	public void testSplitLastDelimNotFound() {
		Split s = Split.splitLast("abc", "/");
		Assert.assertEquals("abc", s.head());
		Assert.assertEquals(Optional.empty(), s.tail());
	}

	@Test
	public void testSplitLastDelimAtStart() {
		Split s = Split.splitLast("/abc", "/");
		Assert.assertEquals("", s.head());
		Assert.assertEquals(Optional.of("abc"), s.tail());
	}

	@Test
	public void testSplitLastDelimAtEnd() {
		Split s = Split.splitLast("abc/", "/");
		Assert.assertEquals("abc", s.head());
		Assert.assertEquals(Optional.of(""), s.tail());
	}

	@Test
	public void testSplitLastSingleOccurrence() {
		// 구분자가 한 번만 등장하면 split / splitLast 결과가 동일해야 한다.
		Split first = Split.split("a/b", "/");
		Split last = Split.splitLast("a/b", "/");
		Assert.assertEquals(first, last);
	}

	@Test
	public void testSplitLastMultiCharDelim() {
		// 다중 문자 구분자에서도 마지막 등장 위치 기준으로 정확히 분할되어야 한다.
		Split s = Split.splitLast("abXYcdXYef", "XY");
		Assert.assertEquals("abXYcd", s.head());
		Assert.assertEquals(Optional.of("ef"), s.tail());
	}

	@Test
	public void testSplitLastEmptyStringInput() {
		Split s = Split.splitLast("", "/");
		Assert.assertEquals("", s.head());
		Assert.assertEquals(Optional.empty(), s.tail());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitLastNullStrRejected() {
		Split.splitLast(null, "/");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitLastNullDelimRejected() {
		Split.splitLast("abc", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSplitLastEmptyDelimRejected() {
		Split.splitLast("abc", "");
	}

	// ----- record auto-generated equals / hashCode / toString -----

	@Test
	public void testEqualsSameContent() {
		Split a = Split.split("k:v", ":");
		Split b = Split.split("k:v", ":");
		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualsDifferentHead() {
		Split a = Split.split("a:v", ":");
		Split b = Split.split("b:v", ":");
		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNotEqualsDifferentTail() {
		Split a = Split.split("k:v1", ":");
		Split b = Split.split("k:v2", ":");
		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNotEqualsTailAbsentVsEmptyString() {
		// "abc/" → tail=Optional.of("")
		// "abc"  → tail=Optional.empty()
		// 같은 head이지만 tail Optional 상태가 다르므로 equals=false.
		Split a = Split.split("abc/", "/");
		Split b = Split.split("abc", "/");
		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testToStringContainsHeadAndTail() {
		Split s = Split.split("k:v", ":");
		String str = s.toString();
		Assert.assertTrue("toString contains head", str.contains("k"));
		Assert.assertTrue("toString contains tail", str.contains("v"));
	}
}
