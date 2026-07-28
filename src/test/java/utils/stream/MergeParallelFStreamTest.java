package utils.stream;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import utils.Suppliable;
import utils.func.Unchecked;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MergeParallelFStreamTest {
	@BeforeEach
	public void setup() {
	}
	
	@Test
	public void test0() throws Exception {
		FStream<FStream<String>> fact = FStream.empty();
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("", ret);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<FStream<String>> fact = FStream.of(FStream.generate(this::generateStreamA, 2));
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("a1a2a3", ret);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2)
										);
		String ret = FStream.mergeParallel(fact, 8, null).join("");
		Assertions.assertEquals("a1b1a2a3b3", ret);
	}
	
	@Test
	public void test3() throws Exception {
		// 세 스트림이 동시에 병합될 때 스트림 간 인터리빙 순서는 스케줄링에 따라 달라질 수
		// 있으므로, 전체 순서 대신 "원소 누락 없음 + 스트림별 상대 순서 보존"만 검증한다.
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		List<String> ret = FStream.mergeParallel(fact, 8, null).toList();

		Assertions.assertEquals(9, ret.size());
		assertPerStreamOrder(ret, "a", "a1", "a2", "a3");
		assertPerStreamOrder(ret, "b", "b1", "b3");
		assertPerStreamOrder(ret, "c", "c1", "c2", "c3", "c4");
	}
	
	@Test
	public void test4() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 2, null).join("");
		Assertions.assertEquals("a1b1a2a3b3c1c2c3c4", ret);
	}
	
	@Test
	public void test5() throws Exception {
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamA, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		String ret = FStream.mergeParallel(fact, 1, null).join("");
		Assertions.assertEquals("a1a2a3b1b3c1c2c3c4", ret);
	}
	
	@Test
	public void test10() throws Exception {
		// 스트림 A가 a2 공급 후 예외로 종료되어도 병합 스트림은 나머지 스트림들을 계속
		// 처리해야 한다. test3()과 같은 이유로 스트림별 상대 순서만 검증한다.
		FStream<FStream<String>> fact = FStream.of(
											FStream.generate(this::generateStreamAX, 2),
											FStream.generate(this::generateStreamB, 2),
											FStream.generate(this::generateStreamC, 2)
										);
		List<String> ret = FStream.mergeParallel(fact, 8, null).toList();

		Assertions.assertEquals(8, ret.size());
		assertPerStreamOrder(ret, "a", "a1", "a2");	// 예외 이전에 공급된 a1, a2만 방출.
		assertPerStreamOrder(ret, "b", "b1", "b3");
		assertPerStreamOrder(ret, "c", "c1", "c2", "c3", "c4");
	}

	/**
	 * 병합 결과에서 주어진 prefix를 갖는 원소들만 추렸을 때, 해당 스트림이 공급한
	 * 순서 그대로 나타나는지 검증한다.
	 */
	private static void assertPerStreamOrder(List<String> merged, String prefix, String... expecteds) {
		List<String> actuals = FStream.from(merged)
										.filter(s -> s.startsWith(prefix))
										.toList();
		Assertions.assertEquals(List.of(expecteds), actuals,
								() -> "스트림 '" + prefix + "'의 원소 누락 또는 순서 위반: merged=" + merged);
	}
	
	@Test
	public void test90() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream.mergeParallel(null, 8, null).join("");
			});
	}

	@Test
	public void test91() throws Exception {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			FStream<FStream<String>> fact = FStream.of(
												FStream.generate(this::generateStreamA, 2),
												FStream.generate(this::generateStreamB, 2),
												FStream.generate(this::generateStreamC, 2)
											);
			FStream.mergeParallel(fact, 0, null).join("");
			});
	}
	
	private void generateStreamA(Suppliable<String> channel) throws InterruptedException, ExecutionException {
		channel.supply("a1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a2");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a3");
		channel.endOfSupply();
	}
	private void generateStreamAX(Suppliable<String> channel) throws Exception {
		channel.supply("a1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("a2");
		throw new Exception();
	}
	
	private void generateStreamB(Suppliable<String> channel) throws InterruptedException, ExecutionException {
		Unchecked.runOrIgnore(() -> Thread.sleep(35));
		channel.supply("b1");
		Unchecked.runOrIgnore(() -> Thread.sleep(200));
		channel.supply("b3");
		channel.endOfSupply();
	}
	
	private void generateStreamC(Suppliable<String> channel) throws InterruptedException, ExecutionException {
		Unchecked.runOrIgnore(() -> Thread.sleep(70));
		channel.supply("c1");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c2");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c3");
		Unchecked.runOrIgnore(() -> Thread.sleep(100));
		channel.supply("c4");
		channel.endOfSupply();
	}
}
