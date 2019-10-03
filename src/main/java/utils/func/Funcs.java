package utils.func;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Funcs {
	private Funcs() {
		throw new AssertionError("Should not be called: class=" + Funcs.class);
	}
	
	public static <T> void when(boolean flag, Runnable work) {
		if ( flag ) {
			work.run();
		}
	}
	
	public static <T> void acceptIfNotNull(T obj, Consumer<T> consumer) {
		if ( obj != null ) {
			consumer.accept(obj);
		}
	}
	
	public static <T> T getIf(boolean flag, T trueCase, T falseCase) {
		return (flag) ? trueCase : falseCase;
	}
	
	public static <T> T getIfNotNull(T obj, T falseCase) {
		return (obj != null) ? obj : falseCase;
	}
	
	public static <T> T getIfNotNull(Object obj, T trueCase, T falseCase) {
		return (obj != null) ? trueCase : falseCase;
	}
	
	public static <K,V> void acceptIfPresent(Map<K,V> map, K key, BiConsumer<K, V> consumer) {
		V value = map.get(key);
		if ( value != null ) {
			consumer.accept(key, value);
		}
	}
}
