package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import utils.KeyValue;
import utils.func.CheckedBiFunctionX;


/**
 * {@link KeyValueFStream#mapOrThrow(CheckedBiFunctionX)}를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueMapOrThrowTest {
	private static String join(String key, Integer value) throws IOException {
		if ( value < 0 ) {
			throw new IOException("negative: " + key);
		}
		return key + "=" + value;
	}

	@Test
	public void mapsKeyAndValue() throws Exception {
		List<String> list = KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", 2))
											.mapOrThrow((k,v) -> join(k, v))
											.toList();
		assertEquals(List.of("a=1", "b=2"), list);
	}

	@Test
	public void throwsOriginalExceptionUndeclared() throws Exception {
		IOException e = assertThrows(IOException.class,
								() -> KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", -1))
													.mapOrThrow(KeyValueMapOrThrowTest::join)
													.toList());
		assertEquals("negative: b", e.getMessage());
	}

	@Test
	public void inheritedSingleArgumentOverloadStillWorks() throws Exception {
		KeyValue<String,Integer> kv = KeyValue.of("a", 1);
		assertSame(kv, KeyValueFStream.of(kv).mapOrThrow(x -> x).findFirst().get());
	}

	@Test
	public void rejectsNullMapper() {
		CheckedBiFunctionX<String,Integer,String,IOException> mapper = null;
		assertThrows(IllegalArgumentException.class,
					() -> KeyValueFStream.of(KeyValue.of("a", 1)).mapOrThrow(mapper));
	}
}
