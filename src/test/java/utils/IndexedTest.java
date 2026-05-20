package utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class IndexedTest {

	@Test
	public void testFactoryAndGetters() throws Exception {
		Indexed<String> idx = Indexed.with("foo", 3);

		Assertions.assertEquals(3, idx.index());
		Assertions.assertEquals("foo", idx.value());
	}

	@Test
	public void testEqualsAndHashCode() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("foo", 3);

		Assertions.assertEquals(a, b);
		Assertions.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualWhenIndexDiffers() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("foo", 4);

		Assertions.assertNotEquals(a, b);
	}

	@Test
	public void testNotEqualWhenValueDiffers() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("bar", 3);

		Assertions.assertNotEquals(a, b);
	}

	@Test
	public void testNullValueIsNullSafe() throws Exception {
		Indexed<String> a = Indexed.with(null, 3);
		Indexed<String> b = Indexed.with(null, 3);
		Indexed<String> c = Indexed.with("foo", 3);

		Assertions.assertEquals(a, b);
		Assertions.assertEquals(a.hashCode(), b.hashCode());
		Assertions.assertNotEquals(a, c);
		Assertions.assertNotEquals(c, a);

		// null 값에 대해 toString이 NPE 없이 동작해야 함
		Assertions.assertEquals("null(3)", a.toString());
	}

	@Test
	public void testEqualsRejectsNullAndOtherTypes() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);

		Assertions.assertNotEquals(a, null);
		Assertions.assertNotEquals(a, "foo");
	}

	@Test
	public void testEqualsReflexive() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);

		Assertions.assertEquals(a, a);
	}

	@Test
	public void testToString() throws Exception {
		Indexed<String> idx = Indexed.with("foo", 3);

		Assertions.assertEquals("foo(3)", idx.toString());
	}
}
