package utils.func;


import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import utils.exception.Throwables;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ResultTest {
	private static final Result<String> R1 = Result.of("a");
	private static final Result<String> R2 = Result.none();
	private static final Result<String> R3 = Result.failure(new IOException("IOException"));
	private static final Result<String> R4 = Result.failure(new IllegalArgumentException("IllegalArgumentException"));

	@Test
	public void testIs() throws Exception {
		Assert.assertEquals(true, R1.isSuccess());
		Assert.assertEquals(false, R1.isFailure());
		Assert.assertEquals(false, R1.isEmpty());
		Assert.assertEquals(false, R2.isSuccess());
		Assert.assertEquals(false, R2.isFailure());
		Assert.assertEquals(true, R2.isEmpty());
		Assert.assertEquals(false, R3.isSuccess());
		Assert.assertEquals(true, R3.isFailure());
		Assert.assertEquals(false, R3.isEmpty());
	}
	
	@Test
	public void testGet() throws Exception {
		Assert.assertEquals("a", R1.get());
		try {
			R2.get();
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( IllegalStateException expected ) { }
		try {
			R3.get();
			Assert.fail("Exception 'RuntimeException' is expected");
		}
		catch ( RuntimeException expected ) {
			Throwable cause = Throwables.unwrapThrowable(expected);
			Assert.assertEquals(IOException.class, cause.getClass());
		}
		try {
			R4.get();
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( IllegalArgumentException expected ) { }
	}
	
	@Test
	public void testGetOrElse() throws Exception {
		Assert.assertEquals("a", R1.getOrElse("b"));
		Assert.assertEquals("b", R2.getOrElse("b"));
		Assert.assertEquals("b", R3.getOrElse("b"));
		Assert.assertEquals("b", R4.getOrElse("b"));
	}
	@Test
	public void testGetOrElseThrow1() throws Exception {
		IllegalStateException ex = new IllegalStateException();
		Assert.assertEquals("a", R1.getOrElseThrow(() -> ex));
		try {
			R2.getOrElseThrow(() -> ex);
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( IllegalStateException expected ) { }
		try {
			R3.getOrElseThrow(() -> ex);
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( IllegalStateException expected ) {
		}
		try {
			R4.getOrElseThrow(() -> ex);
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( IllegalStateException expected ) { }
	}
	@Test
	public void testGetOrElseThrow2() throws Exception {
		IllegalStateException ex = new IllegalStateException();
		Function<Throwable,ExecutionException> prvd = (exc) -> new ExecutionException(exc);
		
		try {
			Assert.assertEquals("a", R1.getOrElseThrow(prvd));
		}
		catch ( ExecutionException e ) {
			Assert.fail("Should not throw an exception=" + e);
		}
		
		try {
			R2.getOrElseThrow(prvd);
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( ExecutionException expected ) {
			Throwable cause = Throwables.unwrapThrowable(expected);
			Assert.assertEquals(IllegalStateException.class, cause.getClass());
		}
		try {
			R3.getOrElseThrow(prvd);
			Assert.fail("Exception 'RuntimeException' is expected");
		}
		catch ( ExecutionException expected ) {
			Throwable cause = Throwables.unwrapThrowable(expected);
			Assert.assertEquals(IOException.class, cause.getClass());
		}
		try {
			R4.getOrElseThrow(prvd);
			Assert.fail("Exception 'IllegalStateException' is expected");
		}
		catch ( ExecutionException expected ) {
			Throwable cause = Throwables.unwrapThrowable(expected);
			Assert.assertEquals(IllegalArgumentException.class, cause.getClass());
		}
	}
	
	@Test
	public void testFilter() throws Exception {
		Assert.assertEquals("a", R1.filter(s -> s.length() == 1).get());
		Assert.assertEquals(true, R1.filter(s -> s.length() == 2).isEmpty());
		Assert.assertEquals(true, R2.filter(s -> s.length() == 1).isEmpty());
		Assert.assertEquals(true, R3.filter(s -> s.length() == 1).isFailure());
	}
	
	@Test
	public void testToJavaList() throws Exception {
		Assert.assertEquals(Lists.newArrayList("a"), R1.toJavaList());
		Assert.assertEquals(Lists.newArrayList(), R2.toJavaList());
		Assert.assertEquals(Lists.newArrayList(), R3.toJavaList());
		Assert.assertEquals(Lists.newArrayList(), R4.toJavaList());
	}
	
	@Test
	public void testExists() throws Exception {
		Assert.assertEquals(true, R1.exists(s -> s.length() == 1));
		Assert.assertEquals(false, R1.exists(s -> s.length() == 2));
		Assert.assertEquals(false, R2.exists(s -> s.length() == 1));
		Assert.assertEquals(false, R3.exists(s -> s.length() == 1));
		Assert.assertEquals(false, R4.exists(s -> s.length() == 1));
	}
	
	@Test
	public void testForAll() throws Exception {
		Assert.assertEquals(true, R1.forAll(s -> s.length() == 1));
		Assert.assertEquals(false, R1.forAll(s -> s.length() == 2));
		Assert.assertEquals(true, R2.forAll(s -> s.length() == 1));
		Assert.assertEquals(true, R3.forAll(s -> s.length() == 1));
		Assert.assertEquals(true, R4.forAll(s -> s.length() == 1));
	}
}
