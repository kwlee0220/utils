package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;


/**
 * {@link Nullables} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class NullablesTest {
	@Test
	public void orElseAndOrGet() {
		assertEquals("a", Nullables.orElse("a", "b"));
		assertEquals("b", Nullables.orElse((String)null, "b"));
		assertNull(Nullables.orElse((String)null, (String)null));

		assertEquals("a", Nullables.orGet("a", () -> { throw new AssertionError("not called"); }));
		assertEquals("b", Nullables.orGet(null, () -> "b"));
		assertThrows(IllegalArgumentException.class, () -> Nullables.orGet(null, null));
	}

	@Test
	public void conditionalActions() {
		List<String> seen = new ArrayList<>();
		Nullables.accept("a", seen::add);
		Nullables.accept((String)null, seen::add);
		Nullables.run("x", () -> seen.add("run"));
		Nullables.run(null, () -> seen.add("never"));
		Nullables.runIfNull(null, () -> seen.add("null"));
		Nullables.runIfNull("x", () -> seen.add("never"));
		assertEquals(List.of("a", "run", "null"), seen);

		// 항상 쓰는 인자는 값과 상관없이 검사한다.
		assertThrows(IllegalArgumentException.class, () -> Nullables.accept(null, (Consumer<Object>)null));
	}

	@Test
	public void map() {
		assertEquals(1, Nullables.map("a", String::length));
		assertNull(Nullables.map((String)null, String::length));
		assertEquals(0, Nullables.mapOrElse((String)null, String::length, 0));
		assertEquals(0, Nullables.mapOrGet((String)null, String::length, () -> 0));
	}
}
