package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * {@link FStream#mapToKeyValue(Function, Function)}를 검증한다(review #19).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MapToKeyValueTest {
	@Test
	public void mapsKeyAndValue() throws Exception {
		List<KeyValue<String,Integer>> list = FStream.of("a", "bb", "ccc")
													.mapToKeyValue(s -> s, String::length)
													.toList();
		assertEquals(List.of(KeyValue.of("a", 1), KeyValue.of("bb", 2), KeyValue.of("ccc", 3)), list);
	}

	@Test
	public void acceptsWildcardFunctions() throws Exception {
		Function<Object,String> keyer = Object::toString;
		Function<CharSequence,Integer> valuer = CharSequence::length;
		KeyValueFStream<CharSequence,Number> kvs = FStream.of("ab").mapToKeyValue(keyer, valuer);
		assertEquals(List.of(KeyValue.of("ab", 2)), kvs.toList());
	}

	@Test
	public void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> FStream.of("a").mapToKeyValue(null, s -> s));
		assertThrows(IllegalArgumentException.class, () -> FStream.of("a").mapToKeyValue(s -> s, null));
	}
}
