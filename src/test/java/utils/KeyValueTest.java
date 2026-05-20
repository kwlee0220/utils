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

	@Test(expected = IllegalArgumentException.class)
	public void testFromTupleWithNullKeyRejected() throws Exception {
		KeyValue.from(Tuple.of(null, 1));
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
	public void testMapKeyWithFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<String,Integer> mapped = kv.mapKey(k -> k.toUpperCase());

		Assert.assertEquals("A", mapped.key());
		Assert.assertEquals(Integer.valueOf(3), mapped.value());
	}

	@Test
	public void testMapKeyWithBiFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<Integer,Integer> mapped = kv.mapKey((k, v) -> k.length() + v);

		Assert.assertEquals(Integer.valueOf(4), mapped.key());
		Assert.assertEquals(Integer.valueOf(3), mapped.value());
	}

	@Test(expected = NullPointerException.class)
	public void testMapKeyFunctionRejectsNullResult() throws Exception {
		KeyValue.of("a", 1).mapKey(k -> null);
	}

	@Test(expected = NullPointerException.class)
	public void testMapKeyBiFunctionRejectsNullResult() throws Exception {
		KeyValue.of("a", 1).mapKey((k, v) -> null);
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
	public void testParseStripsUnicodeWhitespace() throws Exception {
		// IDS (U+3000, ideographic space) 는 String#trim 으로는 제거되지 않지만
		// String#strip 으로는 제거된다 (Character.isWhitespace 기준).
		KeyValue<String,String> kv = KeyValue.parse("　foo　=　bar　");

		Assert.assertEquals("foo", kv.key());
		Assert.assertEquals("bar", kv.value());
	}

	@Test
	public void testParsePreservesEqualsInValue() throws Exception {
		// 첫번째 '='로 분리하므로 값에 포함된 '='는 보존된다.
		KeyValue<String,String> kv = KeyValue.parse("a=b=c");

		Assert.assertEquals("a", kv.key());
		Assert.assertEquals("b=c", kv.value());
	}

	@Test
	public void testParseAllowsEmptyValue() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("k=");

		Assert.assertEquals("k", kv.key());
		Assert.assertEquals("", kv.value());
	}

	@Test
	public void testParseAllowsBlankValue() throws Exception {
		// 값이 공백뿐이면 strip 후 빈 문자열로 정규화된다.
		KeyValue<String,String> kv = KeyValue.parse("k=   ");

		Assert.assertEquals("k", kv.key());
		Assert.assertEquals("", kv.value());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsMissingDelimiter() throws Exception {
		KeyValue.parse("nokey");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsEmptyKey() throws Exception {
		KeyValue.parse("=v");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsBlankKey() throws Exception {
		KeyValue.parse("   =v");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testParseRejectsNullExpr() throws Exception {
		KeyValue.parse(null);
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
