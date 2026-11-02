package utils.stream;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import utils.CSV;
import utils.KeyValue;


/**
 * {@link FStream#split}, {@link FStream#join(CSV)}, {@link KeyValueFStream#toMap(Map)}의 경계 동작.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class SplitJoinTest {
	@Test
	public void splitEmptyInputIsEmptyStream() throws Exception {
		// 2026-11-02 이전에는 '[[]]'를 냈다.
		Assertions.assertEquals(List.of(), FStream.<String>of().split("|"::equals).toList());
	}

	@Test
	public void splitKeepsEmptyGroupsAroundDelimiters() throws Exception {
		Assertions.assertEquals(List.of(List.of(), List.of("a"), List.of()),
								FStream.of("|", "a", "|").split("|"::equals).toList());
		Assertions.assertEquals(List.of(List.of("a"), List.of(), List.of("b")),
								FStream.of("a", "|", "|", "b").split("|"::equals).toList());
		Assertions.assertEquals(List.of(List.of("a", "b")), FStream.of("a", "b").split("|"::equals).toList());
	}

	@Test
	public void joinCsvWritesNullLikeJoinString() throws Exception {
		// 2026-11-02 이전에는 null 원소에서 NullPointerException이 났다.
		Assertions.assertEquals("a,null,1", FStream.of("a", null, 1).join(CSV.get()));
		Assertions.assertEquals("a,null,1", FStream.of("a", null, 1).join(","));
	}

	@Test
	public void toMapWithNullRejectingMap() throws Exception {
		KeyValueFStream<String,Integer> stream
					= KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", null));
		Map<String,Integer> map = new ConcurrentHashMap<>();
		Assertions.assertThrows(NullPointerException.class, () -> stream.toMap(map));
		Assertions.assertEquals(Map.of("a", 1), map);
	}
}
