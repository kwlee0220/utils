package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import utils.stream.FStream;


/**
 * 넓힌 제네릭 시그니처(`? super`/`? extends`)와 {@code try*} 반환 타입을 확인한다.
 * <p>
 * 이 파일이 컴파일된다는 것이 검증의 핵심이다. 결과 값도 함께 확인한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FuncVarianceTest {
	@Test
	public void tryMethodsReturnUsableTypes() throws Exception {
		CheckedSupplierX<String,Exception> supplier = () -> "a";
		String v = supplier.tryGet().get();
		assertEquals("a", v);

		CheckedPredicateX<String,Exception> pred = s -> s.isEmpty();
		Boolean b = pred.tryTest("").get();
		assertTrue(b);

		CheckedFunction<String,Integer> len = String::length;
		Try<Integer> t = len.tryApply("abc");
		assertEquals(3, t.get());
	}

	@Test
	public void fOptionAcceptsSubtypeResults() {
		Function<Object,FOption<Integer>> toInt = o -> FOption.of(o.hashCode() % 2);
		FOption<Number> n = FOption.<String>of("a").flatMap(toInt);
		assertTrue(n.isPresent());

		Supplier<FOption<Integer>> fallback = () -> FOption.of(1);
		FOption<Number> m = FOption.<Number>empty().or(fallback);
		assertEquals(1, m.get());
		FOption<Number> m2 = FOption.<Number>empty().or(FOption.of(2));
		assertEquals(2, m2.get());

		Function<Object,FStream<Integer>> toStream = o -> FStream.of(1, 2);
		FStream<Number> s = FOption.of("a").flatMapFStream(toStream);
		assertEquals(List.of(1, 2), s.toList());

		Function<Object,String> str = Object::toString;
		CharSequence cs = Nullables.map(1, str);
		assertEquals("1", cs);
	}

	@Test
	public void tryAndEitherAcceptWiderFunctions() {
		Function<Object,Try<Integer>> f = o -> Try.success(1);
		Try<Number> t = Try.success("a").flatMap(f);
		assertEquals(1, t.get());

		List<Object> errors = new ArrayList<>();
		Consumer<Object> sink = errors::add;
		Try.failure(new IllegalStateException()).ifFailed(sink);
		assertEquals(1, errors.size());

		Function<Object,Integer> zero = e -> 0;
		Try<Number> r = Try.<Number>failure(new IllegalStateException()).recover(zero);
		assertEquals(0, r.get());

		Function<Object,Either<Integer,String>> toLeft = o -> Either.left(1);
		Either<Number,CharSequence> e = Either.<String,CharSequence>left("a").flatMapLeft(toLeft);
		assertEquals(1, e.getLeft());

		Consumer<Object> any = o -> { };
		Either.<String,Integer>right(1).match(any, any);
	}

	@Test
	public void multipleCasesAcceptsSupertypeConsumers() {
		List<Object> seen = new ArrayList<>();
		Consumer<Object> c = seen::add;
		MultipleCases.of(Map.of("a", 1)).when("a").then(c);
		assertEquals(List.of(1), seen);
	}

	/**
	 * {@code Either.left()}·{@code right()}는 {@link FOption}을 돌려준다.
	 */
	@Test
	public void eitherSidesAreFOptions() {
		Either<String,Integer> l = Either.left("a");
		FOption<String> left = l.left();
		FOption<Integer> right = l.right();
		assertEquals("a", left.get());
		assertTrue(right.isAbsent());
		assertEquals(Either.left("a"), l);
		assertEquals(Either.left("a").hashCode(), l.hashCode());
		org.junit.jupiter.api.Assertions.assertNotEquals(Either.right("a"), l);
	}
}
