package utils.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 * {@link KeyValueFStream#mapKey}·{@link KeyValueFStream#mapValue}가 오버로드가 있는 메소드 참조를 받는지,
 * 키와 값을 함께 쓰는 변형({@code mapKeyWithValue}·{@code mapValueWithKey})이 동작하는지 검증한다.
 * <p>
 * 2026-11-02 이전에는 {@code BiFunction} 버전도 이름이 {@code mapKey}/{@code mapValue}여서
 * {@code mapKey(Objects::toString)}이 모호성 컴파일 오류였다. 이 파일이 컴파일되는 것이 검증의 일부다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueMapOverloadTest {
	private static KeyValueFStream<Integer,Integer> kvs() {
		return KeyValueFStream.of(KeyValue.of(1, 10), KeyValue.of(2, 20));
	}

	@Test
	public void overloaded_method_references_resolve() {
		assertEquals(List.of(KeyValue.of("1", 10), KeyValue.of("2", 20)),
					kvs().mapKey(Objects::toString).toList());
		assertEquals(List.of(KeyValue.of(1, "10"), KeyValue.of(2, "20")),
					kvs().mapValue(Objects::toString).toList());
	}

	@Test
	public void with_value_and_with_key_variants_see_both() {
		assertEquals(List.of(KeyValue.of(11, 10), KeyValue.of(22, 20)),
					kvs().mapKeyWithValue((k, v) -> k + v).toList());
		assertEquals(List.of(KeyValue.of(1, "1=10"), KeyValue.of(2, "2=20")),
					kvs().mapValueWithKey((k, v) -> k + "=" + v).toList());
	}
}
