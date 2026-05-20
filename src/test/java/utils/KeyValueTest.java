package utils;

import java.util.AbstractMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueTest {

	@Test
	public void testOfAndGetters() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);

		Assertions.assertEquals("a", kv.key());
		Assertions.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test
	public void testOfAllowsNullValue() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", null);

		Assertions.assertEquals("a", kv.key());
		Assertions.assertNull(kv.value());
	}

	@Test
	public void testOfRejectsNullKey() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.of(null, 1);
		});
	}

	@Test
	public void testFromMapEntry() throws Exception {
		Map.Entry<String,Integer> entry = new AbstractMap.SimpleEntry<>("a", 1);
		KeyValue<String,Integer> kv = KeyValue.from(entry);

		Assertions.assertEquals("a", kv.key());
		Assertions.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test
	public void testFromNullMapEntryRejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.from((Map.Entry<String,Integer>)null);
		});
	}

	@Test
	public void testFromMapEntryWithNullKeyRejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			Map.Entry<String,Integer> entry = new AbstractMap.SimpleEntry<>(null, 1);
			KeyValue.from(entry);
		});
	}

	@Test
	public void testFromTuple() throws Exception {
		Tuple<String,Integer> tupl = Tuple.of("a", 1);
		KeyValue<String,Integer> kv = KeyValue.from(tupl);

		Assertions.assertEquals("a", kv.key());
		Assertions.assertEquals(Integer.valueOf(1), kv.value());
	}

	@Test
	public void testFromNullTupleRejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.from((Tuple<String,Integer>)null);
		});
	}

	@Test
	public void testFromTupleWithNullKeyRejected() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.from(Tuple.of(null, 1));
		});
	}

	@Test
	public void testToTuple() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);
		Tuple<String,Integer> tupl = kv.toTuple();

		Assertions.assertEquals("a", tupl._1);
		Assertions.assertEquals(Integer.valueOf(1), tupl._2);
	}

	@Test
	public void testEqualsAndHashCode() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", 1);
		KeyValue<String,Integer> b = KeyValue.of("a", 1);

		Assertions.assertEquals(a, b);
		Assertions.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualWhenKeyDiffers() throws Exception {
		Assertions.assertNotEquals(KeyValue.of("a", 1), KeyValue.of("b", 1));
	}

	@Test
	public void testNotEqualWhenValueDiffers() throws Exception {
		Assertions.assertNotEquals(KeyValue.of("a", 1), KeyValue.of("a", 2));
	}

	@Test
	public void testEqualsHandlesNullValue() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", null);
		KeyValue<String,Integer> b = KeyValue.of("a", null);
		KeyValue<String,Integer> c = KeyValue.of("a", 1);

		Assertions.assertEquals(a, b);
		Assertions.assertEquals(a.hashCode(), b.hashCode());
		Assertions.assertNotEquals(a, c);
		Assertions.assertNotEquals(c, a);
	}

	@Test
	public void testEqualsRejectsNullAndOtherTypes() throws Exception {
		KeyValue<String,Integer> a = KeyValue.of("a", 1);

		Assertions.assertNotEquals(a, null);
		Assertions.assertNotEquals(a, "a=1");
	}

	@Test
	public void testEqualsAsymmetricBetweenKeyValueAndComparableKeyValue() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);
		ComparableKeyValue<String,Integer> ckv = ComparableKeyValue.of("a", 1);

		// 같은 (key, value)라도 런타임 클래스가 다르므로 equals는 양쪽 다 false
		Assertions.assertNotEquals(kv, ckv);
		Assertions.assertNotEquals(ckv, kv);
	}

	@Test
	public void testToString() throws Exception {
		Assertions.assertEquals("a=1", KeyValue.of("a", 1).toString());
		Assertions.assertEquals("a=null", KeyValue.of("a", null).toString());
	}

	@Test
	public void testMap() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		String result = kv.map((k, v) -> k + ":" + v);

		Assertions.assertEquals("a:3", result);
	}

	@Test
	public void testMapKeyWithFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<String,Integer> mapped = kv.mapKey(k -> k.toUpperCase());

		Assertions.assertEquals("A", mapped.key());
		Assertions.assertEquals(Integer.valueOf(3), mapped.value());
	}

	@Test
	public void testMapKeyWithBiFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<Integer,Integer> mapped = kv.mapKey((k, v) -> k.length() + v);

		Assertions.assertEquals(Integer.valueOf(4), mapped.key());
		Assertions.assertEquals(Integer.valueOf(3), mapped.value());
	}

	@Test
	public void testMapKeyFunctionRejectsNullResult() throws Exception {
		Assertions.assertThrows(NullPointerException.class, () -> {
			KeyValue.of("a", 1).mapKey(k -> null);
		});
	}

	@Test
	public void testMapKeyBiFunctionRejectsNullResult() throws Exception {
		Assertions.assertThrows(NullPointerException.class, () -> {
			KeyValue.of("a", 1).mapKey((k, v) -> null);
		});
	}

	@Test
	public void testMapValueWithFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 3);

		KeyValue<String,String> mapped = kv.mapValue(v -> "v" + v);

		Assertions.assertEquals("a", mapped.key());
		Assertions.assertEquals("v3", mapped.value());
	}

	@Test
	public void testMapValueWithBiFunction() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("ab", 3);

		KeyValue<String,Integer> mapped = kv.mapValue((k, v) -> k.length() * v);

		Assertions.assertEquals("ab", mapped.key());
		Assertions.assertEquals(Integer.valueOf(6), mapped.value());
	}

	@Test
	public void testParseSimple() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("foo=bar");

		Assertions.assertEquals("foo", kv.key());
		Assertions.assertEquals("bar", kv.value());
	}

	@Test
	public void testParseTrimsWhitespace() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("  foo  =  bar  ");

		Assertions.assertEquals("foo", kv.key());
		Assertions.assertEquals("bar", kv.value());
	}

	@Test
	public void testParseStripsUnicodeWhitespace() throws Exception {
		// IDS (U+3000, ideographic space) 는 String#trim 으로는 제거되지 않지만
		// String#strip 으로는 제거된다 (Character.isWhitespace 기준).
		KeyValue<String,String> kv = KeyValue.parse("　foo　=　bar　");

		Assertions.assertEquals("foo", kv.key());
		Assertions.assertEquals("bar", kv.value());
	}

	@Test
	public void testParsePreservesEqualsInValue() throws Exception {
		// 첫번째 '='로 분리하므로 값에 포함된 '='는 보존된다.
		KeyValue<String,String> kv = KeyValue.parse("a=b=c");

		Assertions.assertEquals("a", kv.key());
		Assertions.assertEquals("b=c", kv.value());
	}

	@Test
	public void testParseAllowsEmptyValue() throws Exception {
		KeyValue<String,String> kv = KeyValue.parse("k=");

		Assertions.assertEquals("k", kv.key());
		Assertions.assertEquals("", kv.value());
	}

	@Test
	public void testParseAllowsBlankValue() throws Exception {
		// 값이 공백뿐이면 strip 후 빈 문자열로 정규화된다.
		KeyValue<String,String> kv = KeyValue.parse("k=   ");

		Assertions.assertEquals("k", kv.key());
		Assertions.assertEquals("", kv.value());
	}

	@Test
	public void testParseRejectsMissingDelimiter() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.parse("nokey");
		});
	}

	@Test
	public void testParseRejectsEmptyKey() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.parse("=v");
		});
	}

	@Test
	public void testParseRejectsBlankKey() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.parse("   =v");
		});
	}

	@Test
	public void testParseRejectsNullExpr() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			KeyValue.parse(null);
		});
	}

	@Test
	public void testImmutability() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);

		// mapValue는 새 객체를 반환해야 하며, 원본은 그대로 유지
		KeyValue<String,Integer> mapped = kv.mapValue(v -> v + 100);

		Assertions.assertEquals(Integer.valueOf(1), kv.value());
		Assertions.assertEquals(Integer.valueOf(101), mapped.value());
		Assertions.assertNotSame(kv, mapped);
	}
}
