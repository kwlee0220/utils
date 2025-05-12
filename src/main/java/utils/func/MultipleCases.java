package utils.func;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.stream.KeyValueFStream;
import utils.stream.TriConsumer;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class MultipleCases<K,V> {
	private final Map<K,V> m_cases;
	
	public static <K,V>  MultipleCases<K,V> switching(Map<K,V> cases) {
		return new MultipleCases<>(cases);
	}
	
	private MultipleCases(Map<K,V> cases) {
		m_cases = cases;
	}
	
	public Case<K,V> ifCase(K key) {
		Map<K,V> remains = Maps.newHashMap();
		remains.putAll(m_cases);
		
		return new Case<>(key, remains.remove(key), remains);
	}
	
	public Otherwise<K,V> otherwise() {
		return new Otherwise<>(KeyValueFStream.from(m_cases).toList());
	}
	
	public <X> ContextedCase<K,V,X> ifCase(K key, X context) {
		Map<K,V> remains = Maps.newHashMap();
		remains.putAll(m_cases);
		
		return new ContextedCase<>(context, key, remains.remove(key), remains);
	}
	
	public <X> ContextedOtherwise<K,V,X> otherwise(X context) {
		return new ContextedOtherwise<>(context, KeyValueFStream.from(m_cases).toList());
	}
	
	public static class Case<K,V> {
		private K m_key;
		private V m_value;
		private Map<K,V> m_others;
		
		private Case(K key, V value, Map<K,V> others) {
			m_key = key;
			m_value = value;
			m_others = others;
		}
		
		public MultipleCases<K,V> consume(Consumer<V> consumer) {
			if ( m_value != null ) {
				consumer.accept(m_value);
			}
			
			// 남은 case들을 이용하여 'MultipleCases' 만들어서 반환한다.
			return new MultipleCases<>(m_others);
		}
		
		public <T> MultipleCases<K,V> apply(Function<V,T> func, Map<K,T> output) {
			output.put(m_key, func.apply(m_value));
			return new MultipleCases<>(m_others);
		}
	}
	
	public static class ContextedCase<K,V,X> {
		private final X m_context;
		private K m_key;
		private V m_value;
		private Map<K,V> m_remains;
		
		private ContextedCase(X context, K key, V value, Map<K,V> remains) {
			m_context = context;
			m_key = key;
			m_value = value;
			m_remains = remains;
		}
		
		public MultipleCases<K,V> consume(BiConsumer<V,X> consumer) {
			if ( m_value != null ) {
				consumer.accept(m_value, m_context);
			}
			return new MultipleCases<>(m_remains);
		}
		
		public <T> MultipleCases<K,V> apply(Function<V,T> func, Map<K,T> output) {
			output.put(m_key, func.apply(m_value));
			return new MultipleCases<>(m_remains);
		}
	}
	
	public static class Otherwise<K,V> {
		private List<KeyValue<K,V>> m_remaining;
		
		private Otherwise(List<KeyValue<K,V>> remaining) {
			m_remaining = remaining;
		}
		
		public void forEach(BiConsumer<K,V> consumer) {
			m_remaining.forEach(kv -> consumer.accept(kv.key(), kv.value()));
		}
	}
	
	public static class ContextedOtherwise<K,V,X> {
		private final X m_context;
		private List<KeyValue<K,V>> m_remaining;
		
		private ContextedOtherwise(X context, List<KeyValue<K,V>> remaining) {
			m_context = context;
			m_remaining = remaining;
		}
		
		public void forEach(TriConsumer<K,V,X> consumer) {
			m_remaining.forEach(kv -> consumer.accept(kv.key(), kv.value(), m_context));
		}
	}
}