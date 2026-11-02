package utils.stream;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Tuple;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@ExtendWith(MockitoExtension.class)
public class KVMatchTest {
	private Map<Integer, String> m_data;
	private List<KeyValue<Integer,String>> m_data2;
	private Map<Integer,String> m_lut1;
	private Map<Integer,List<String>> m_lut2;
	
	@BeforeEach
	public void setup() {
		m_data = Maps.newLinkedHashMap();
		m_data.put(1, "A");
		m_data.put(2, "B");
		m_data.put(3, "C");

		m_data2 = List.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"), KeyValue.of(1, "A2"));

		m_lut1 = Map.of(1, "a", 4, "d", 3, "c");
		m_lut2 = Map.of(1, List.of("a", "a2"), 4, List.of("d"), 3, List.of("c"));
	}
	
	@Test
	public void test0() throws Exception {
		Map<Integer,String> lut = Map.of(1, "a", 4, "d", 3, "c");
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data)
								.match(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"));
		Assertions.assertEquals(answer, result);

		result = KeyValueFStream.from(m_data)
								.match(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("B", null), Tuple.of("C", "c"));
		Assertions.assertEquals(answer2, result);
	}
	
	@Test
	public void test1() throws Exception {
		Map<Integer,List<String>> lut = Map.of(1, List.of("a", "a2"), 4, List.of("d"), 3, List.of("c"));
		
		List<Tuple<String,String>> result;
		result = KeyValueFStream.from(m_data)
								.flatMatch(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("C", "c"));
		Assertions.assertEquals(answer, result);

		result = KeyValueFStream.from(m_data)
								.flatMatch(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("B", null), Tuple.of("C", "c"));
		Assertions.assertEquals(answer2, result);
	}
	
	@Test
	public void test2() throws Exception {
		Map<Integer,String> lut = Map.of(1, "a", 4, "d", 3, "c");
		
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data2)
								.match(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"), Tuple.of("A2", "a"));
		Assertions.assertEquals(answer, result);
		
		result = KeyValueFStream.from(m_data2)
								.match(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("B", null), Tuple.of("C", "c"), Tuple.of("A2", "a"));
		Assertions.assertEquals(answer2, result);
	}
	
	@Test
	public void test3() throws Exception {
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data2)
								.flatMatch(m_lut2)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("C", "c"), Tuple.of("A2", "a"),
							Tuple.of("A2", "a2"));
		Assertions.assertEquals(answer, result);
		
		result = KeyValueFStream.from(m_data2)
								.flatMatch(m_lut2, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("B", null),
								Tuple.of("C", "c"), Tuple.of("A2", "a"), Tuple.of("A2", "a2"));
		Assertions.assertEquals(answer2, result);
	}

	/**
	 * {@code flatMatch}는 참조 값이 하나도 없는 원소(빈 목록, {@code null} 목록, 없는 키)를 모두 "일치하지 않음"으로
	 * 다룬다. inner join({@code keepUnmatched=false})은 0행, left outer join({@code true})은 {@code (v, null)} 1행.
	 * 2026-11-02 이전에는 {@code null} 목록이 inner join에서도 {@code (v, null)}을 냈고, 빈 목록은 outer join에서도
	 * 사라졌다.
	 */
	@Test
	public void flatMatchTreatsEmptyAndNullListsAsUnmatched() {
		Map<String,List<String>> lut = new java.util.HashMap<>();
		lut.put("a", null);
		lut.put("b", List.of());
		lut.put("d", List.of("d1", "d2"));
		KeyValueFStream<String,Integer> src = KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", 2),
																KeyValue.of("c", 3), KeyValue.of("d", 4));

		Assertions.assertEquals(List.of(KeyValue.of("d", Tuple.of(4, "d1")), KeyValue.of("d", Tuple.of(4, "d2"))),
								src.flatMatch(lut).toList());

		src = KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("b", 2), KeyValue.of("c", 3), KeyValue.of("d", 4));
		Assertions.assertEquals(List.of(KeyValue.of("a", Tuple.of(1, null)), KeyValue.of("b", Tuple.of(2, null)),
										KeyValue.of("c", Tuple.of(3, null)),
										KeyValue.of("d", Tuple.of(4, "d1")), KeyValue.of("d", Tuple.of(4, "d2"))),
								src.flatMatch(lut, true).toList());
	}

	@Test
	public void flatMatchIteratesAOneShotIterableOncePerElement() {
		// 비었는지 확인하려고 iterator()를 따로 호출하지 않는다(한 번만 순회할 수 있는 Iterable도 동작해야 한다).
		java.util.concurrent.atomic.AtomicInteger iterations = new java.util.concurrent.atomic.AtomicInteger();
		Iterable<String> oneShot = () -> {
			if ( iterations.incrementAndGet() > 1 ) {
				throw new IllegalStateException("iterated twice");
			}
			return List.of("x", "y").iterator();
		};
		Map<String,Iterable<String>> lut = Map.of("a", oneShot);

		Assertions.assertEquals(List.of(KeyValue.of("a", Tuple.of(1, "x")), KeyValue.of("a", Tuple.of(1, "y"))),
								KeyValueFStream.of(KeyValue.of("a", 1)).flatMatch(lut).toList());
	}

	/**
	 * {@code Map}을 받는 {@code lookup}은 {@code match}와 같이 키의 존재({@code containsKey})로 일치를 판단한다.
	 * 조회 함수를 받는 {@code lookup}은 {@code null}을 반환하면 일치하지 않은 것으로 본다.
	 */
	@Test
	public void lookupByMapMatchesKeysWithNullValues() {
		// Map 버전은 match와 같이 키의 존재로 판단한다. 값이 null인 키 a도 일치한다.
		Map<String,String> lut = new java.util.HashMap<>();
		lut.put("a", null);
		lut.put("b", "B");

		Assertions.assertEquals(List.of(KeyValue.of("a", null), KeyValue.of("b", "B")),
								FStream.of("a", "b", "c").lookup(lut).toList());
		Assertions.assertEquals(List.of(KeyValue.of("a", null), KeyValue.of("b", "B"), KeyValue.of("c", null)),
								FStream.of("a", "b", "c").lookup(lut, true).toList());
		Assertions.assertEquals(List.of(KeyValue.of("a", Tuple.of(1, null))),
								KeyValueFStream.of(KeyValue.of("a", 1), KeyValue.of("c", 3)).match(lut).toList());

		// 조회 함수 버전은 키의 존재를 알 수 없으므로 null을 반환하면 일치하지 않은 것으로 본다.
		Assertions.assertEquals(List.of(KeyValue.of("b", "B")),
								FStream.of("a", "b", "c").lookup(lut::get, false).toList());
	}

	/**
	 * {@code match}·{@code flatMatch}·{@code lookup}의 결과 스트림은 끝까지 읽거나 닫으면 원본도 닫아야 한다.
	 * 2026-11-02 이전에는 원본이 닫히지 않았다.
	 */
	@Test
	public void matchClosesSource() throws Exception {
		boolean[] closed = new boolean[1];

		FStream<KeyValue<Integer,String>> src = closeTracked(m_data, closed);
		KeyValueFStream.from(src).match(m_lut1).toList();
		Assertions.assertTrue(closed[0], "match: source not closed");

		closed[0] = false;
		src = closeTracked(m_data, closed);
		KeyValueFStream.from(src).flatMatch(m_lut2, true).toList();
		Assertions.assertTrue(closed[0], "flatMatch: source not closed");

		closed[0] = false;
		FStream<Integer> keys = closeTracked(m_data, closed).map(KeyValue::key);
		keys.lookup(m_lut1).toList();
		Assertions.assertTrue(closed[0], "lookup: source not closed");

		// 끝까지 읽지 않고 닫아도 원본이 닫힌다.
		closed[0] = false;
		src = closeTracked(m_data, closed);
		KeyValueFStream<Integer,Tuple<String,String>> matched = KeyValueFStream.from(src).match(m_lut1);
		matched.next();
		matched.close();
		Assertions.assertTrue(closed[0], "match: source not closed on early close");
	}

	// 닫히면 closed[0]을 true로 바꾸는 원본 스트림.
	private static FStream<KeyValue<Integer,String>> closeTracked(Map<Integer,String> data, boolean[] closed) {
		java.util.Iterator<KeyValue<Integer,String>> iter = KeyValueFStream.from(data).toList().iterator();
		return new FStreams.AbstractFStream<KeyValue<Integer,String>>() {
			@Override
			protected void closeInGuard() {
				closed[0] = true;
			}

			@Override
			protected FOption<KeyValue<Integer,String>> nextInGuard() {
				return iter.hasNext() ? FOption.of(iter.next()) : FOption.empty();
			}
		};
	}
}
