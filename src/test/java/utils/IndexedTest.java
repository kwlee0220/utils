package utils;

import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class IndexedTest {

	@Test
	public void testFactoryAndGetters() throws Exception {
		Indexed<String> idx = Indexed.with("foo", 3);

		Assert.assertEquals(3, idx.index());
		Assert.assertEquals("foo", idx.value());
	}

	@Test
	public void testEqualsAndHashCode() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("foo", 3);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualWhenIndexDiffers() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("foo", 4);

		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNotEqualWhenValueDiffers() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);
		Indexed<String> b = Indexed.with("bar", 3);

		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNullValueIsNullSafe() throws Exception {
		Indexed<String> a = Indexed.with(null, 3);
		Indexed<String> b = Indexed.with(null, 3);
		Indexed<String> c = Indexed.with("foo", 3);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertNotEquals(a, c);
		Assert.assertNotEquals(c, a);

		// null 값에 대해 toString이 NPE 없이 동작해야 함
		Assert.assertEquals("null(3)", a.toString());
	}

	@Test
	public void testEqualsRejectsNullAndOtherTypes() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);

		Assert.assertNotEquals(a, null);
		Assert.assertNotEquals(a, "foo");
	}

	@Test
	public void testEqualsReflexive() throws Exception {
		Indexed<String> a = Indexed.with("foo", 3);

		Assert.assertEquals(a, a);
	}

	@Test
	public void testToString() throws Exception {
		Indexed<String> idx = Indexed.with("foo", 3);

		Assert.assertEquals("foo(3)", idx.toString());
	}
}
