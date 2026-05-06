package utils;

import java.util.AbstractMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueTest {

	@Test
	public void testOfAndGetters() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);

		Assert.assertEquals("a", kv.key());
		Assert.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test
	public void testOfAllowsNullValue() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", null);

		Assert.assertEquals("a", kv.key());
		Assert.assertNull(kv.value());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testOfRejectsNullKey() throws Exception {
		KeyValue.of(null, 1);
	}

	@Test
	public void testFromMapEntry() throws Exception {
		Map.Entry<String,Integer> entry = new AbstractMap.SimpleEntry<>("a", 1);
		KeyValue<String,Integer> kv = KeyValue.from(entry);

		Assert.assertEquals("a", kv.key());
		Assert.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFromNullMapEntryRejected() throws Exception {
		KeyValue.from((Map.Entry<String,Integer>)null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFromMapEntryWithNullKeyRejected() throws Exception {
		Map.Entry<String,Integer> entry = new AbstractMap.SimpleEntry<>(null, 1);
		KeyValue.from(entry);
	}

	@Test
	public void testFromTuple() throws Exception {
		Tuple<String,Integer> tupl = Tuple.of("a", 1);
		KeyValue<String,Integer> kv = KeyValue.from(tupl);

		Assert.assertEquals("a", kv.key());
		Assert.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFromNullTupleRejected() throws Exception {
		KeyValue.from((Tuple<String,Integer>)null);
	}

	@Test
	public void testToTuple() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);
		Tuple<String,Integer> tupl = kv.toTuple();

		Assert.assertEquals("a", tupl._1);
		Assert.assertEquals(Integer.valueOf(1), tupl._2);
	}

	@Test
	public void testEqualsAndHashCode() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", 1);
		KeyValue<String,Integer> b = KeyValue.of("a", 1);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualWhenKeyDiffers() throws Exception {
		Assert.assertNotEquals(KeyValue.of("a", 1), KeyValue.of("b", 1));
	}

	@Test
	public void testNotEqualWhenValueDiffers() throws Exception {
		Assert.assertNotEquals(KeyValue.of("a", 1), KeyValue.of("a", 2));
	}

	@Test
	public void testEqualsHandlesNullValue() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", null);
		KeyValue<String,Integer> b = KeyValue.of("a", null);
		KeyValue<String,Integer> c = KeyValue.of("a", 1);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertNotEquals(a, c);
		Assert.assertNotEquals(c, a);
	}

	@Test
	public void testEqualsRejectsNullAndOtherTypes() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", 1);

		Assert.assertNotEquals(a, null);
		Assert.assertNotEquals(a, "a=1");
	}

	@Test
	public void testEqualsAsymmetricBetweenKeyValueAndComparableKeyValue() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);
		ComparableKeyValue<String,Integer> ckv = ComparableKeyValue.of("a", 1);

		// 같은 (key, value)라도 런타임 클래스가 다르므로 equals는 양쪽 다 false
		Assert.assertNotEquals(kv, ckv);
		Assert.assertNotEquals(ckv, kv);
	}

	@Test
	public void testToString() throws Exception {
		Assert.assertEquals("a=1", KeyValue.of("a", 1).toString());
		Assert.assertEquals("a=null", KeyValue.of("a", null).toString());
	}

	@Test
	public void testMap() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		String result = kv.map((k, v) -> k + ":" + v);

		Assert.assertEquals("a:3", result);
	}

	@Test
	public void testMapKey() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<Integer,Integer> mapped = kv.mapKey((k, v) -> k.length() + v);

		Assert.assertEquals(Integer.valueOf(4), mapped.key());
		Assert.assertEquals(Integer.valueOf(3), mapped.value());
	}

	@Test
	public void testMapValueWithFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<String,String> mapped = kv.mapValue(v -> "v" + v);

		Assert.assertEquals("a", mapped.key());
		Assert.assertEquals("v3", mapped.value());
	}

	@Test
	public void testMapValueWithBiFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("ab", 3);

		KeyValue<String,Integer> mapped = kv.mapValue((k, v) -> k.length() * v);

		Assert.assertEquals("ab", mapped.key());
		Assert.assertEquals(Integer.valueOf(6), mapped.value());
	}

	@Test
	public void testParseSimple() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("foo=bar");

		Assert.assertEquals("foo", kv.key());
		Assert.assertEquals("bar", kv.value());
	}

	@Test
	public void testParseTrimsWhitespace() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("  foo  =  bar  ");

		Assert.assertEquals("foo", kv.key());
		Assert.assertEquals("bar", kv.value());
	}

	@Test
	public void testParseWithEscape() throws Exception {
		// 두 번째 인자는 escape 문자: 'a\=b=c' 에서 첫 번째 '='가 이스케이프되어
		// 키는 "a=b", 값은 "c"로 파싱된다.
		KeyValue<String,String> kv = KeyValue.parse("a\\=b=c", '\\');

		Assert.assertEquals("a=b", kv.key());
		Assert.assertEquals("c", kv.value());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsMissingDelimiter() throws Exception {
		KeyValue.parse("nokey");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsMultipleDelimiters() throws Exception {
		KeyValue.parse("a=b=c");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsNullExpr() throws Exception {
		KeyValue.parse(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseWithEscapeRejectsNullExpr() throws Exception {
		KeyValue.parse(null, '\\');
	}

	@Test
	public void testImmutability() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);

		// mapValue는 새 객체를 반환해야 하며, 원본은 그대로 유지
		KeyValue<String,Integer> mapped = kv.mapValue(v -> v + 100);

		Assert.assertEquals(Integer.valueOf(1), kv.value());
		Assert.assertEquals(Integer.valueOf(101), mapped.value());
		Assert.assertNotSame(kv, mapped);
	}
}
