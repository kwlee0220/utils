package utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import io.vavr.control.Option;

/**
 *
 * @author Kang-Woo Lee
 */
public class Utilities {
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private Utilities() {
		throw new AssertionError("Should not be invoked!!: class=" + Utilities.class.getName());
	}
	
	public static String getLineSeparator() {
		return LINE_SEPARATOR;
	}

	public static void executeAsynchronously(Executor executor, Runnable task) {
		if ( executor != null ) {
			executor.execute(task);
		}
		else {
			new Thread(task).start();
		}
	}

//	public static CompletableFuture<Void> runAsync(Runnable task, Executor executor) {
//		if ( executor != null ) {
//			return CompletableFuture.runAsync(task, executor);
//		}
//		else {
//			return CompletableFuture.runAsync(task);
//		}
//	}
//
//	public static CompletableFuture<Void> runAsync(Runnable task) {
//		return CompletableFuture.runAsync(task);
//	}
//
//	public static CompletableFuture<Void> runCheckedAsync(CheckedRunnable task, Executor executor) {
//		return runAsync(Unchecked.toRunnableIE(task), executor);
//	}
//
//	public static CompletableFuture<Void> runCheckedAsync(CheckedRunnable task) {
//		return runAsync(Unchecked.toRunnableIE(task));
//	}
	
	public static boolean timedWaitMillis(Condition cond, Predicate<Void> exitPred, long maxMillis)
		throws InterruptedException {
		Date deadline = new Date(System.currentTimeMillis() + maxMillis);
		while ( cond.awaitUntil(deadline) ) {
			if ( exitPred.test(null) ) {
				return true;
			}
		}
		
		return false;
	}

	public static Set<Class<?>> getInterfaceAllRecusively(Class<?> cls) {
		Set<Class<?>> intfcSet = Sets.newHashSet();
		if ( cls.isInterface() ) {
			intfcSet.add(cls);
		}
		else {
			intfcSet.addAll(Arrays.asList(cls.getInterfaces()));
		}

		while ( (cls = cls.getSuperclass()) != Object.class && cls != null ) {
			intfcSet.addAll(Arrays.asList(cls.getInterfaces()));
		}

		return intfcSet;
	}
	
	public static <T> Stream<T> stream(Iterable<T> it) {
		return StreamSupport.stream(it.spliterator(), false);
	}
	
	public static <T> Stream<T> stream(Iterator<T> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
	}

	public static <T,U> U mapIfNotNull(T value, Function<T,U> func) {
		if ( value != null ) {
			return func.apply(value);
		}
		else {
			return null;
		}
	}

/*
	public static <T> boolean consumeIfNotNull(T value, Consumer<T> consumer) {
		if ( value != null ) {
			consumer.accept(value);
			return true;
		}
		else {
			return false;
		}
	}
	
	public static String[] toStringArray(Collection<String> coll) {
		return coll.toArray(new String[coll.size()]);
	}
	
	public static <T> T[] toArray(Collection<T> coll, Class<T> cls) {
		Object arr = Array.newInstance(cls, coll.size());
		return coll.toArray((T[])arr);
	}
	
	public static Collection<String> toStringCollection(Iterator<String> iter) {
		List<String> list = new ArrayList<String>();
		while ( iter.hasNext() ) {
			list.add(iter.next());
		}
		
		return list;
	}
	
	public static <T> T first(Collection<T> coll) {
		if ( coll.size() > 0 ) {
			return coll.iterator().next();
		}
		else {
			return null;
		}
	}
	
	public static <T> T first(Collection<T> coll, T defaultValue) {
		if ( coll.size() > 0 ) {
			return coll.iterator().next();
		}
		else {
			return defaultValue;
		}
	}
	
	public static <T> List<T> toList(Iterator<T> iter) {
		List<T> list = new ArrayList<T>();
		while ( iter.hasNext() ) {
			list.add(iter.next());
		}
		
		return list;
	}
	
	public static String getLastComponent(String str, char delim) {
		if ( str == null ) {
			return null;
		}
		
		int idx = str.lastIndexOf(delim);
		return (idx >= 0) ? str.substring(idx+1) : str;
	}

	public static Throwable unwrapThrowable(Throwable e) {
		while ( true ) {
			if ( e instanceof InvocationTargetException ) {
				e = ((InvocationTargetException)e).getTargetException();
			}
			else if ( e instanceof UndeclaredThrowableException ) {
				e = ((UndeclaredThrowableException)e).getUndeclaredThrowable();
			}
			else if ( e instanceof ExecutionException ) {
				e = ((ExecutionException)e).getCause();
			}
			else {
				return e;
			}
		}
	}
	
	public static Exception asException(Throwable e) {
		if ( e instanceof Exception ) {
			return (Exception)e;
		}
		else if ( e instanceof RuntimeException ) {
			throw (RuntimeException)e;
		}
		else {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] appendArray(T[] array, Object... elms) {
		int len = Array.getLength(array);

		Object expanded = Array.newInstance(array.getClass().getComponentType(), len+elms.length);
		System.arraycopy(array, 0, expanded, 0, len);
		for ( int i =0; i < elms.length; ++i ) {
			Array.set(expanded, len+i, elms[i]);
		}

		return (T[])expanded;
	}
	
	public static <T> int indexOf(T[] array, T key) {
		for ( int i =0; i < array.length; ++i ) {
			if ( key.equals(array[i]) ) {
				return i;
			}
		}
		
		return -1;
	}
	
	public static int indexOf(int[] array, int key) {
		for ( int i =0; i < array.length; ++i ) {
			if ( key == array[i] ) {
				return i;
			}
		}
		
		return -1;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] removeElement(T[] array, int index) {
		if ( array == null ) {
			throw new IllegalArgumentException("array was null");
		}
		if ( index < 0 || index >= array.length ) {
			throw new IllegalArgumentException("invalid index=" + index);
		}

		int newLength = array.length-1;
		if ( newLength == 0 ) {
			return (T[])Array.newInstance(array.getClass().getComponentType(), 0);
		}

		Object shrinked = Array.newInstance(array.getClass().getComponentType(), newLength);
		if ( index != 0 ) {
			System.arraycopy(array, 0, shrinked, 0, index);
		}
		if ( index < newLength ) {
			System.arraycopy(array, index+1, shrinked, index, newLength-index);
		}

		return (T[])shrinked;
	}

	public static Class<?>[] extendInterfaces(Class<?> base, Class<?>... addons) {
		Set<Class<?>> intfcSet = getInterfaceAllRecusively(base);
		intfcSet.addAll(Arrays.asList(addons));

		return intfcSet.toArray(new Class<?>[intfcSet.size()]);
	}
	
	public static Class<?>[] concatClasses(Class<?>[] left, Class<?>... right) {
		if ( left == null || right == null ) {
			throw new IllegalArgumentException("argument was null");
		}

		Set<Class<?>> typeSet = new HashSet<Class<?>>();
		typeSet.addAll(Arrays.asList(left));
		typeSet.addAll(Arrays.asList(right));

		return typeSet.toArray(new Class[typeSet.size()]);
	}

	public static Class<?>[] concatClassesLE(Class<?>[] left, Class<?>[] right) {
		Class<?>[] concatenated = new Class[left.length + right.length];
		System.arraycopy(left, 0, concatenated, 0, left.length);
		System.arraycopy(right, 0, concatenated, left.length, right.length);

		return concatenated;
	}

	public static Class<?>[] concatClasses(Class<?>[] left, Class<?> right) {
		for ( Class<?> cls: left ) {
			if ( cls.equals(right) ) {
				return left;
			}
		}

		Class<?>[] concatenated = new Class[left.length + 1];
		System.arraycopy(left, 0, concatenated, 0, left.length);
		concatenated[left.length] = right;

		return concatenated;
	}

	public static Class<?>[] concatClassesLE(Class<?>[] left, Class<?> right) {
		Class<?>[] concatenated = new Class[left.length + 1];
		System.arraycopy(left, 0, concatenated, 0, left.length);
		concatenated[left.length] = right;

		return concatenated;
	}

	public static Class<?>[] concatClassesLE(Collection<Class<?>> left, Class<?>... right) {
		Class<?>[] concatenated = new Class[left.size() + right.length];
		left.toArray(concatenated);
		System.arraycopy(right, 0, concatenated, left.size(), right.length);

		return concatenated;
	}

	public static <T> boolean appendIfNotContains(List<T> list, T data) {
		if ( !list.contains(data) ) {
			list.add(data);

			return true;
		}
		else {
			return false;
		}
	}

	public static <T> T[] appendIfNotContains(T[] array, T data) {
		for ( int i =0; i < array.length; ++i ) {
			if ( data.equals(array[i]) ) {
				return array;
			}
		}
		
		return appendArray(array, data);
	}
	
	public static String[] parseCsv(String csvStr) {
    	List<String> parts = CSV.get().parse(csvStr);
    	return parts.toArray(new String[parts.size()]);
    }
    
    public static String buildCsv(String[] strs) {
    	return CSV.get().toString(Arrays.asList(strs));
    }
*/
	
	public static Logger getAndAppendLoggerName(Object obj, String suffix) {
    	if ( obj instanceof LoggerSettable ) {
    		Logger prev = ((LoggerSettable)obj).getLogger();
    		Logger logger = LoggerFactory.getLogger(prev.getName() + suffix);
    		((LoggerSettable)obj).setLogger(logger);
    		
    		return prev;
    	}
    	else {
    		return null;
    	}
    }
    
    public static void setLogger(Object obj, Logger logger) {
    	if ( obj != null && logger != null && obj instanceof LoggerSettable ) {
    		((LoggerSettable)obj).setLogger(logger);
    	}
    }
	
	public static long toUTCEpocMillis(LocalDateTime ts) {
		return ts.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
	}
	
	public static ZonedDateTime fromUTCEpocMillis(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
	}
	
	public static LocalDateTime fromUTCEpocMillis(long millis, ZoneId zone) {
		return fromUTCEpocMillis(millis).toLocalDateTime();
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T callPrivateConstructor(Class<T> cls)
		throws NoSuchMethodException, SecurityException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<?> ctor = cls.getDeclaredConstructor(new Class[0]);
		ctor.setAccessible(true);
		return (T)ctor.newInstance(new Object[0]);
	}
	
	public static <T> Option<T> toOption(T value, T nullValue) {
		if ( value == null ) {
			return Option.none();
		}
		else {
			return (!value.equals(nullValue)) ? Option.some(value) : Option.none(); 
		}
	}
}
