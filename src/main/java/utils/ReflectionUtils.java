package utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ReflectionUtils {
	private ReflectionUtils() {
		throw new AssertionError("Should not be called: " + ReflectionUtils.class);
	}
	
	public static List<Class<?>> traverseClassHierarchy(final Class<?> cls) {
		List<Class<?>> classHierarchy = new ArrayList<>();
		Class<?> currentClass = cls;
		while ( currentClass != null ) {
			classHierarchy.add(currentClass);
			currentClass = currentClass.getSuperclass();
		}
		Collections.reverse(classHierarchy);
		return classHierarchy;
	}

    public static List<Field> getAllFieldsList(final Class<?> cls) {
    	return FStream.from(traverseClassHierarchy(cls))
		    	    	.flatMapArray(c -> c.getDeclaredFields())
		    	    	.toList();
    }

	/**
	 * 클래스에 선언된 모든 필드(private, protected, public)와 부모 클래스의 모든 필드를 가져온다.
	 * java.lang.Object 클래스의 필드는 포함하지 않는다.
	 * 
	 * @param cls 필드를 가져올 클래스
	 * @return 모든 필드 배열
	 */
	public static Field[] getAllFields(Class<?> cls) {
		List<Field> fields = new ArrayList<>();
		
		while (cls != null && cls != Object.class) {
			fields.addAll(Arrays.asList(cls.getDeclaredFields()));
			cls = cls.getSuperclass();
		}
		
		return fields.toArray(new Field[0]);
	}

	/**
	 * 클래스에 선언된 모든 필드(private, protected, public)와 부모 클래스의 모든 필드를 
	 * Stream으로 반환한다. java.lang.Object 클래스의 필드는 포함하지 않는다.
	 * 
	 * @param cls 필드를 가져올 클래스
	 * @return 모든 필드의 Stream
	 */
	public static Stream<Field> getAllFieldsStream(Class<?> cls) {
		List<Field> fields = new ArrayList<>();
		
		while (cls != null && cls != Object.class) {
			fields.addAll(Arrays.asList(cls.getDeclaredFields()));
			cls = cls.getSuperclass();
		}
		
		return fields.stream();
	}

	/**
	 * 객체에서 이름에 해당하는 필드 값을 리플렉션을 사용하여 가져온다.
	 * 해당 필드가 현재 클래스 또는 모든 부모 클래스에서 검색된다.
	 * 필드가 private이어도 접근 가능하도록 처리된다.
	 * 
	 * @param obj 필드 값을 가져올 객체
	 * @param fieldName 필드 이름
	 * @return 필드 값
	 * @throws NoSuchFieldException 필드를 찾을 수 없는 경우
	 * @throws SecurityException 접근 권한이 없는 경우
	 * @throws IllegalArgumentException 인자가 잘못된 경우
	 * @throws IllegalAccessException 접근할 수 없는 경우
	 */
	public static Object getFieldValue(Object obj, String fieldName) 
		throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		
		Class<?> cls = obj.getClass();
		while (cls != null && cls != Object.class) {
			try {
				Field field = cls.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.get(obj);
			} 
			catch (NoSuchFieldException e) {
				// 현재 클래스에 필드가 없으면 부모 클래스에서 찾음
				cls = cls.getSuperclass();
			}
		}
		
		throw new NoSuchFieldException("Field not found: " + fieldName);
	}
}
