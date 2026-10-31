package utils.statechart;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link StatePath}의 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class StatePathTest {
	@Test
	public void valid_paths_accepted() {
		for ( String path: List.of("a", "/A", "/A/B", "A/B", "/Running/Idle/X") ) {
			Assertions.assertTrue(StatePath.isValid(path), path);
			StatePath.checkValid(path);
		}
	}

	@Test
	public void invalid_paths_rejected() {
		for ( String path: List.of("", "/", "/A/", "A/", "//A", "/A//B") ) {
			Assertions.assertFalse(StatePath.isValid(path), path);
			Assertions.assertThrows(IllegalArgumentException.class, () -> StatePath.checkValid(path), path);
		}
		Assertions.assertFalse(StatePath.isValid(null));
		Assertions.assertThrows(IllegalArgumentException.class, () -> StatePath.checkValid(null));
	}

	@Test
	public void parentOf_returns_text_before_last_slash() {
		Assertions.assertEquals("/A", StatePath.parentOf("/A/B"));
		Assertions.assertEquals("/A/B", StatePath.parentOf("/A/B/C"));
		Assertions.assertEquals("A", StatePath.parentOf("A/B"));
		Assertions.assertNull(StatePath.parentOf("/A"));
		Assertions.assertNull(StatePath.parentOf("A"));
	}

	@Test
	public void segments_drop_empty_head() {
		Assertions.assertEquals(List.of("Running", "Idle"), StatePath.segments("/Running/Idle"));
		Assertions.assertEquals(List.of("a"), StatePath.segments("a"));
	}
}
