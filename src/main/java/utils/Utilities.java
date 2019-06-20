package utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import net.sf.cglib.proxy.MethodProxy;
import utils.func.Try;
import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee
 */
public class Utilities {
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private Utilities() {
		throw new AssertionError("Should not be invoked!!: class=" + Utilities.class.getName());
	}

	public static <T> int hashCode(FStream<T> strm) {
		return strm.map(v -> v.hashCode())
					.foldLeft(1, (accum,code) -> 31 * accum + code);
	}
	
	public static String getLineSeparator() {
		return LINE_SEPARATOR;
	}

	public static void executeAsynchronously(Runnable task) {
		new Thread(task).start();
	}

	public static void executeAsynchronously(Executor executor, Runnable task) {
		if ( executor != null ) {
			executor.execute(task);
		}
		else {
			new Thread(task).start();
		}
	}
	
	public static void checkArgument(boolean pred, String msg) {
		if ( !pred ) {
			throw new IllegalArgumentException(msg);
		}
	}
	
	public static void checkArgument(boolean pred, Supplier<String> msg) {
		if ( !pred ) {
			throw new IllegalArgumentException(msg.get());
		}
	}
	
	public static void checkNotNullArgument(Object obj) {
		if ( obj == null ) {
			throw new IllegalArgumentException("null argument");
		}
	}
	
	public static void checkNotNullArgument(Object obj, String msg) {
		if ( obj == null ) {
			throw new IllegalArgumentException("null argument: " + msg);
		}
	}
	
	public static void checkNotNullArgument(Object obj, Supplier<String> msg) {
		if ( obj == null ) {
			throw new IllegalArgumentException(msg.get());
		}
	}
	
	public static <T> void checkNotNullArguments(Iterable<T> objs, String msg) {
		if ( objs == null ) {
			throw new IllegalArgumentException(msg);
		}
		for ( Object obj: objs ) {
			checkNotNullArgument(obj, msg);
		}
	}
	
	public static <T> void checkNotNullArguments(Iterable<T> objs, Supplier<String> msg) {
		if ( objs == null ) {
			throw new IllegalArgumentException(msg.get());
		}
		for ( Object obj: objs ) {
			checkNotNullArgument(obj, msg);
		}
	}
	
	public static <T> void checkNotNullArguments(T[] objs, String msg) {
		if ( objs == null ) {
			throw new IllegalArgumentException(msg);
		}
		for ( Object obj: objs ) {
			checkNotNullArgument(obj, msg);
		}
	}
	
	public static <T> void checkNotNullArguments(T[] objs, Supplier<String> msg) {
		if ( objs == null ) {
			throw new IllegalArgumentException(msg.get());
		}
		for ( Object obj: objs ) {
			checkNotNullArgument(obj, msg);
		}
	}
	
	public static void checkState(boolean pred, String msg) {
		if ( !pred ) {
			throw new IllegalStateException(msg);
		}
	}
	
	public static void checkState(boolean pred, Supplier<String> msg) {
		if ( !pred ) {
			throw new IllegalStateException(msg.get());
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

	public static <T,U> U mapIfNotNull(T value, Function<? super T,? extends U> func) {
		if ( value != null ) {
			return func.apply(value);
		}
		else {
			return null;
		}
	}

	public static <T> void ifNotNull(T value, Consumer<? super T> consumer) {
		if ( value != null ) {
			consumer.accept(value);
		}
	}

/*
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
	
	public static <T extends AutoCloseable> T attachCloser(T object, Consumer<T> closer) {
		return ProxyUtils.replaceAction(object, new CloseAttacher<T>(closer));
	}
	private static class CloseAttacher<T extends AutoCloseable> implements CallHandler<T> {
		private final Consumer<T> m_closer;
		
		CloseAttacher(Consumer<T> closer) {
			m_closer = closer;
		}
		
		@Override
		public boolean test(Method method) {
			return method.getName().equals("close") && method.getParameterTypes().length == 0;
		}

		@Override
		public Object intercept(T baseObject, Method method, Object[] args, MethodProxy proxy)
				throws Throwable {
			Try.run(() -> m_closer.accept(baseObject));
			return proxy.invokeSuper(baseObject, new Object[0]);
		}
		
	}
	
	public static <T> Iterable<T> toIterable(Iterator<T> iter) {
		return () -> iter;
	}
	
	public static <T> List<T> shuffle(List<T> list) {
		return selectRandomly(list, list.size());
	}
	
	public static <T> List<T> selectRandomly(List<T> list, int count) {
		Random rand = new Random(System.currentTimeMillis());
		
		List<T> selecteds = new ArrayList<>();
		for ( int i =0; i < count; ++i ) {
			while ( true ) {
				if ( list.isEmpty() ) {
					return selecteds;
				}
				
				T selected = selectOne(list, rand);
				if ( selected != null ) {
					selecteds.add(selected);
					break;
				}
				
				list = FStream.from(list)
								.filter(v -> v != null)
								.toList();
			}
		}
		
		return selecteds;
	}
	
	private static <T> T selectOne(List<T> list, Random rand) {
		for ( int i =0; i < 5; ++i ) {
			int idx = rand.nextInt(list.size());
			T selected = list.get(idx);
			if ( selected != null ) {
				list.set(idx, null);
				
				return selected;
			}
		}
		
		return null;
	}
}
