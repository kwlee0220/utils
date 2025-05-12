package utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
}
