package utils.funcwrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import utils.func.Lazy;


/**
 * {@link Lazy#wrap(java.util.function.Supplier, Class)}가 utils.func가 아닌 다른 패키지의 public이 아닌 인터페이스도
 * 감싸는지 확인한다(같은 패키지의 인터페이스로는 접근 문제가 드러나지 않는다).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LazyWrapOtherPackageTest {
	interface HiddenGreet {
		String hello();
	}

	interface HiddenLoudGreet extends HiddenGreet {
		String shout();
	}

	@Test
	public void wrapsNonPublicInterfaceOfAnotherPackage() {
		HiddenLoudGreet g = Lazy.wrap(() -> new HiddenLoudGreet() {
			@Override public String hello() { return "hi"; }
			@Override public String shout() { return "HI"; }
		}, HiddenLoudGreet.class);

		assertEquals("hi", g.hello());
		assertEquals("HI", g.shout());
	}
}
