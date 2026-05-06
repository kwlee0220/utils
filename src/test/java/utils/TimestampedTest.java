package utils;

import org.junit.Assert;
import org.junit.Test;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class TimestampedTest {

	@Test
	public void testFactoryWithExplicitTs() throws Exception {
		Timestamped<String> ts = Timestamped.of("foo", 1234L);

		Assert.assertEquals(1234L, ts.timestamp());
		Assert.assertEquals("foo", ts.value());
	}

	@Test
	public void testFactoryUsesCurrentTimeMillis() throws Exception {
		long before = System.currentTimeMillis();
		Timestamped<String> ts = Timestamped.of("foo");
		long after = System.currentTimeMillis();

		Assert.assertTrue(ts.timestamp() >= before);
		Assert.assertTrue(ts.timestamp() <= after);
		Assert.assertEquals("foo", ts.value());
	}

	@Test
	public void testNullValueAllowed() throws Exception {
		Timestamped<String> ts = Timestamped.of(null, 1234L);

		Assert.assertEquals(1234L, ts.timestamp());
		Assert.assertNull(ts.value());
	}

	@Test
	public void testEqualsAndHashCode() throws Exception {
		Timestamped<String> a = Timestamped.of("foo", 1234L);
		Timestamped<String> b = Timestamped.of("foo", 1234L);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	public void testNotEqualWhenTsDiffers() throws Exception {
		Timestamped<String> a = Timestamped.of("foo", 1234L);
		Timestamped<String> b = Timestamped.of("foo", 5678L);

		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNotEqualWhenValueDiffers() throws Exception {
		Timestamped<String> a = Timestamped.of("foo", 1234L);
		Timestamped<String> b = Timestamped.of("bar", 1234L);

		Assert.assertNotEquals(a, b);
	}

	@Test
	public void testNullValueIsNullSafe() throws Exception {
		Timestamped<String> a = Timestamped.of(null, 1234L);
		Timestamped<String> b = Timestamped.of(null, 1234L);
		Timestamped<String> c = Timestamped.of("foo", 1234L);

		Assert.assertEquals(a, b);
		Assert.assertEquals(a.hashCode(), b.hashCode());
		Assert.assertNotEquals(a, c);
		Assert.assertNotEquals(c, a);

		// null 값에 대해 toString이 NPE 없이 동작해야 함
		Assert.assertEquals("null(1234)", a.toString());
	}

	@Test
	public void testEqualsRejectsNullAndOtherTypes() throws Exception {
		Timestamped<String> a = Timestamped.of("foo", 1234L);

		Assert.assertNotEquals(a, null);
		Assert.assertNotEquals(a, "foo");
	}

	@Test
	public void testEqualsReflexive() throws Exception {
		Timestamped<String> a = Timestamped.of("foo", 1234L);

		Assert.assertEquals(a, a);
	}

	@Test
	public void testToString() throws Exception {
		Timestamped<String> ts = Timestamped.of("foo", 1234L);

		Assert.assertEquals("foo(1234)", ts.toString());
	}
}
