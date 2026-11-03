package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;


/**
 * {@link Funcs} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class FuncsTest {
	@Test
	public void replaceOrInsertFirstReplacesMatchingNullElement() {
		List<String> list = new ArrayList<>(Arrays.asList("a", null));
		assertNull(Funcs.replaceOrInsertFirst(list, v -> v == null, v -> "X"));
		assertEquals(Arrays.asList("a", "X"), list);

		assertEquals("a", Funcs.replaceOrInsertFirst(list, "a"::equals, v -> v + "!"));
		assertEquals(Arrays.asList("a!", "X"), list);

		assertNull(Funcs.replaceOrInsertFirst(list, "z"::equals, v -> "new:" + v));
		assertEquals(Arrays.asList("a!", "X", "new:null"), list);
	}
}
