package utils.func;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;

import org.junit.jupiter.api.Test;


/**
 * {@link Nullables} 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class NullablesTest {
	@Test
	public void whenTrueWithNullValueIsEmpty() {
		assertFalse(Nullables.whenTrue(true, (Object)null).isPresent());
		assertEquals(Optional.of("a"), Nullables.whenTrue(true, "a"));
		assertFalse(Nullables.whenTrue(false, "a").isPresent());
	}
}
