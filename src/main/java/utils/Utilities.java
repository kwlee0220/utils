package utils;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executor;

import com.google.common.collect.Sets;

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
	
/*
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
*/
	
	/**
	 * 배열에서 주어진 키에 해당하는 첫번째 원소를 제거한 새로운 배열을 반환한다.
	 * <p>
	 * 원소는 배열을 첫번째 원소를 시작으로 순차적으로 index를 증가해가며 검색하고, 첫번째로 찾아진
	 * 원소가 삭제 대상이 된다. 즉, 키에 해당하는 복수개의 원소가 있더라도 가장 작은 index에 해당하는
	 * 원소만이 삭제된다.
	 * 만일 키에 해당하는 원소가 없는 경우는 인자로 주어진 배열을 그대로 반환한다.
	 * 키를 통한 대상 원소를 찾는 방법은 {@link Object#equals(Object)}를 사용한다.
	 *
	 * @param <T>	원소 타입	
	 * @param array	검색 대상 배열
	 * @param key	검색하고자하는 원소을 찾을 키
	 * @return	키에 해당하는 원소가 제거된 배열.
	 */
/*
	public static <T> T[] removeFirstElement(T[] array, T key) {
		if ( array == null ) {
			throw new IllegalArgumentException("array was null");
		}
		if ( key == null ) {
			throw new IllegalArgumentException("key was null");
		}
		
		int index = 0;
		for (; index < array.length; ++index ) {
			if ( key.equals(array[index]) ) {
				return removeElement(array, index);
			}
		}
		
		return array;
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
*/

	/**
	 * 주어진 두 클래스 배열을 하나의 클래스 배열로 통합한다. 이때 중복되는 클래스는 제거된다.
	 * <p>
	 * 통합 작업시 클래스간 상속 관계는 고려하지 않는다.
	 *
	 * @param left	통합할 첫번째 클래스 배열.
	 * @param right	통합할 두번째 클래스 배열.
	 * @return	통합된 클래스 배열.
	 * @throws	IllegalArgumentException	첫번째 또는 두번째 배열이 <code>null</code>인 경우.
	 */
/*
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
	
	public static Logger getAndAppendLoggerName(Object obj, String suffix) {
    	if ( obj instanceof LoggerSettable ) {
    		Logger prev = ((LoggerSettable)obj).getLogger();
    		Logger logger = Logger.getLogger(prev.getName() + suffix);
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

	public static String toElapsedTimeString(long millis) {
		long seconds = millis/1000;
		if ( seconds < 60 ) {
			return String.format("%02.3fs", millis/1000f);
		}
		
		long min = seconds / 60;
		seconds = seconds % 60;
		if ( min < 60 ) {
			return String.format("%02dm%02ds", min, seconds);
		}
		
		long hour = min / 60;
		min = min % 60;
		return String.format("%dh%02dm%02ds", hour, min, seconds);
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
	
	public static Object callPrivateConstructor(Class<?> cls)
		throws NoSuchMethodException, SecurityException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		Constructor<?> ctor = cls.getDeclaredConstructor(new Class[0]);
		ctor.setAccessible(true);
		return ctor.newInstance(new Object[0]);
	}
*/
}
