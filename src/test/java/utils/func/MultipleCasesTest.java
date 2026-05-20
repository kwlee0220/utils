package utils.func;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import utils.KeyValue;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MultipleCasesTest {

	private static Map<String,Integer> sample() {
		Map<String,Integer> m = new LinkedHashMap<>();
		m.put("a", 1);
		m.put("b", 2);
		m.put("c", 3);
		return m;
	}

	// ----- switching -----

	@Test
	public void testSwitchingNullRejected() {
		assertThrows(IllegalArgumentException.class, () -> MultipleCases.switching(null));
	}

	@Test
	public void testSwitchingDefensiveCopy() {
		// switching 이후 입력 map을 수정해도 빌더에는 영향이 없어야 한다.
		Map<String,Integer> input = new HashMap<>();
		input.put("a", 1);

		MultipleCases<String,Integer> mc = MultipleCases.switching(input);
		input.clear();
		input.put("z", 99);

		List<Integer> consumed = new ArrayList<>();
		mc.ifCase("a").consume(consumed::add)
		  .ifCase("z").consume(consumed::add)
		  .otherwise().forEach((k, v) -> consumed.add(v));

		assertEquals(Arrays.asList(1), consumed);
	}

	// ----- ifCase + consume (기본) -----

	@Test
	public void testIfCaseConsumeWithExistingKey() {
		List<Integer> consumed = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("b").consume(consumed::add)
				.otherwise().forEach((k, v) -> { /* discard */ });

		assertEquals(Arrays.asList(2), consumed);
	}

	@Test
	public void testIfCaseConsumeWithAbsentKeyDoesNotCall() {
		AtomicInteger callCount = new AtomicInteger(0);
		MultipleCases.switching(sample())
				.ifCase("missing").consume(v -> callCount.incrementAndGet())
				.otherwise().forEach((k, v) -> { });

		assertEquals(0, callCount.get());
	}

	@Test
	public void testIfCaseDistinguishesAbsentFromNullValue() {
		// 핵심: 키가 없는 경우와 키는 있지만 값이 null인 경우를 구분한다.
		Map<String,Integer> m = new HashMap<>();
		m.put("present-but-null", null);
		// "absent" 키는 추가하지 않음

		List<String> log = new ArrayList<>();
		MultipleCases.switching(m)
				.ifCase("present-but-null").consume(v -> log.add("called: " + v))
				.ifCase("absent").consume(v -> log.add("called: " + v))
				.otherwise().forEach((k, v) -> { });

		// present-but-null 은 consumer 호출됨 (null 인자), absent 는 미호출
		assertEquals(Arrays.asList("called: null"), log);
	}

	// ----- 체인 동작 -----

	@Test
	public void testChainConsumesSequentiallyAndRemovesKeys() {
		List<String> log = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a").consume(v -> log.add("a=" + v))
				.ifCase("c").consume(v -> log.add("c=" + v))
				.otherwise().forEach((k, v) -> log.add("other: " + k + "=" + v));

		// a 와 c 가 consume되고, 남은 b는 otherwise로 흘러간다.
		assertEquals(3, log.size());
		assertTrue(log.contains("a=1"));
		assertTrue(log.contains("c=3"));
		assertTrue(log.contains("other: b=2"));
	}

	@Test
	public void testSameKeyTwiceInChainSecondIsAbsent() {
		AtomicInteger firstCalls = new AtomicInteger(0);
		AtomicInteger secondCalls = new AtomicInteger(0);

		MultipleCases.switching(sample())
				.ifCase("a").consume(v -> firstCalls.incrementAndGet())
				.ifCase("a").consume(v -> secondCalls.incrementAndGet())
				.otherwise().forEach((k, v) -> { });

		assertEquals(1, firstCalls.get());
		// 첫 번째 ifCase로 a가 제거되었으므로 두 번째는 부재 처리.
		assertEquals(0, secondCalls.get());
	}

	// ----- otherwise -----

	@Test
	public void testOtherwiseIteratesRemaining() {
		List<KeyValue<String,Integer>> remaining = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a").consume(v -> { })
				.otherwise().forEach((k, v) -> remaining.add(KeyValue.of(k, v)));

		assertEquals(2, remaining.size());
		List<String> keys = remaining.stream().map(KeyValue::key).sorted().collect(Collectors.toList());
		assertEquals(Arrays.asList("b", "c"), keys);
	}

	@Test
	public void testOtherwiseOnEmptyMap() {
		AtomicInteger callCount = new AtomicInteger(0);
		MultipleCases.switching(new HashMap<String,Integer>())
				.otherwise().forEach((k, v) -> callCount.incrementAndGet());

		assertEquals(0, callCount.get());
	}

	@Test
	public void testOtherwiseAfterAllConsumed() {
		AtomicInteger callCount = new AtomicInteger(0);
		MultipleCases.switching(sample())
				.ifCase("a").consume(v -> { })
				.ifCase("b").consume(v -> { })
				.ifCase("c").consume(v -> { })
				.otherwise().forEach((k, v) -> callCount.incrementAndGet());

		assertEquals(0, callCount.get());
	}

	// ----- ifCase + context -----

	@Test
	public void testIfCaseWithContextPassesValueAndContext() {
		List<String> log = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a", "ctx").consume((v, ctx) -> log.add(ctx + ":" + v))
				.otherwise().forEach((k, v) -> { });

		assertEquals(Arrays.asList("ctx:1"), log);
	}

	@Test
	public void testIfCaseWithContextAbsentKeyNotCalled() {
		AtomicInteger callCount = new AtomicInteger(0);
		MultipleCases.switching(sample())
				.ifCase("missing", "ctx").consume((v, ctx) -> callCount.incrementAndGet())
				.otherwise().forEach((k, v) -> { });

		assertEquals(0, callCount.get());
	}

	@Test
	public void testIfCaseWithContextDistinguishesAbsentFromNullValue() {
		Map<String,Integer> m = new HashMap<>();
		m.put("present-but-null", null);

		List<String> log = new ArrayList<>();
		MultipleCases.switching(m)
				.ifCase("present-but-null", "C").consume((v, ctx) -> log.add(ctx + "=" + v))
				.ifCase("absent", "C").consume((v, ctx) -> log.add(ctx + "=" + v))
				.otherwise().forEach((k, v) -> { });

		assertEquals(Arrays.asList("C=null"), log);
	}

	// ----- otherwise + context -----

	@Test
	public void testOtherwiseWithContextPassesContext() {
		List<String> log = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a", "X").consume((v, ctx) -> log.add(ctx + ":" + v))
				.otherwise("X").forEach((k, v, ctx) -> log.add(ctx + "/" + k + "=" + v));

		assertEquals(3, log.size());
		assertTrue(log.contains("X:1"));
		assertTrue(log.contains("X/b=2"));
		assertTrue(log.contains("X/c=3"));
	}

	@Test
	public void testOtherwiseWithContextOnEmpty() {
		AtomicInteger callCount = new AtomicInteger(0);
		MultipleCases.switching(new HashMap<String,Integer>())
				.otherwise("ctx").forEach((k, v, ctx) -> callCount.incrementAndGet());

		assertEquals(0, callCount.get());
	}

	// ----- 혼합 (with/without context 섞기) -----

	@Test
	public void testMixedContextAndNonContextChainsCompile() {
		// without-context와 with-context 분기는 서로 다른 Case 타입을 반환하므로
		// 한 체인 안에서 자유롭게 섞을 수는 없다(타입 시스템상). 각자 독립 체인은 동작한다.
		List<String> log1 = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a").consume(v -> log1.add("a=" + v))
				.otherwise().forEach((k, v) -> log1.add("o:" + k));

		List<String> log2 = new ArrayList<>();
		MultipleCases.switching(sample())
				.ifCase("a", "ctx").consume((v, ctx) -> log2.add(ctx + "/a=" + v))
				.otherwise("ctx").forEach((k, v, ctx) -> log2.add(ctx + "/o:" + k));

		assertTrue(log1.contains("a=1"));
		assertTrue(log2.contains("ctx/a=1"));
	}
}
