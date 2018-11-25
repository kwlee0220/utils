package utils.stream;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import io.vavr.Tuple2;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KVFStreams {
	private KVFStreams() {
		throw new AssertionError("Should not be called: " + KVFStreams.class);
	}

	static class KeyValueKVFStream<K,V> implements KVFStream<K,V> {
		private final FStream<KeyValue<K,V>> m_base;
		
		KeyValueKVFStream(FStream<KeyValue<K,V>> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K, V> next() {
			return m_base.next();
		}	
	}

	static class TupleKVFStream<K,V> implements KVFStream<K,V> {
		private final FStream<Tuple2<K,V>> m_base;
		
		TupleKVFStream(FStream<Tuple2<K,V>> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K, V> next() {
			Tuple2<K,V> tup = m_base.next();
			return (tup != null) ? KeyValue.of(tup._1, tup._2) : null;
		}	
	}

	static class MapEntryIterKVFStream<K,V> implements KVFStream<K,V> {
		private final Iterator<? extends Entry<? extends K, ? extends V>> m_iter;
		
		MapEntryIterKVFStream(Iterator<? extends Entry<? extends K, ? extends V>> iter) {
			m_iter = iter;
		}

		@Override
		public void close() throws Exception {
		}

		@Override
		public KeyValue<K, V> next() {
			if ( m_iter.hasNext() ) {
				Map.Entry<? extends K,? extends V> entry = m_iter.next();
				return new KeyValue<>(entry.getKey(), entry.getValue());
			}
			else {
				return null;
			}
		}	
	}

	static class KeyFilteredKVFStream<K,V> implements KVFStream<K,V> {
		private final KVFStream<K,V> m_base;
		private final Predicate<? super K> m_pred;
		
		KeyFilteredKVFStream(KVFStream<K,V> base, Predicate<? super K> pred) {
			m_base = base;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,V> next() {
			KeyValue<K,V> next;
			while ( (next = m_base.next()) != null && !m_pred.test(next.key()) );
			
			return next;
		}	
	}

	static class ValueFilteredKVFStream<K,V> implements KVFStream<K,V> {
		private final KVFStream<K,V> m_base;
		private final Predicate<? super V> m_pred;
		
		ValueFilteredKVFStream(KVFStream<K,V> base, Predicate<? super V> pred) {
			m_base = base;
			m_pred = pred;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,V> next() {
			KeyValue<K,V> next;
			while ( (next = m_base.next()) != null && !m_pred.test(next.value()) );
			
			return next;
		}	
	}

	static class MappedKVFStream<K,V,S> implements FStream<S> {
		private final KVFStream<K,V> m_base;
		private final BiFunction<? super K,? super V,? extends S> m_map;
		
		MappedKVFStream(KVFStream<K,V> base, BiFunction<? super K,? super V,? extends S> map) {
			m_base = base;
			m_map = map;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public S next() {
			KeyValue<K,V> next = m_base.next();
			return ( next != null ) ? m_map.apply(next.key(), next.value()) : null;
		}	
	}

	static class KeyMappedKVFStream<K,V,S> implements KVFStream<S,V> {
		private final KVFStream<K,V> m_base;
		private final BiFunction<? super K,? super V,? extends S> m_map;
		
		KeyMappedKVFStream(KVFStream<K,V> base, BiFunction<? super K,? super V,? extends S> map) {
			m_base = base;
			m_map = map;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<S,V> next() {
			KeyValue<K,V> next = m_base.next();
			return ( next != null )
					? KeyValue.of(m_map.apply(next.key(), next.value()), next.value()) : null;
		}	
	}

	static class ValueMappedKVFStream<K,V,U> implements KVFStream<K,U> {
		private final KVFStream<K,V> m_base;
		private final BiFunction<? super K,? super V,? extends U> m_map;
		
		ValueMappedKVFStream(KVFStream<K,V> base, BiFunction<? super K,? super V,? extends U> map) {
			m_base = base;
			m_map = map;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,U> next() {
			KeyValue<K,V> next = m_base.next();
			return ( next != null )
					? KeyValue.of(next.key(), m_map.apply(next.key(), next.value())) : null;
		}	
	}

	static class ValueMappedKVFStream2<K,V,U> implements KVFStream<K,U> {
		private final KVFStream<K,V> m_base;
		private final Function<? super V,? extends U> m_map;
		
		ValueMappedKVFStream2(KVFStream<K,V> base, Function<? super V,? extends U> map) {
			m_base = base;
			m_map = map;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,U> next() {
			KeyValue<K,V> next = m_base.next();
			return ( next != null ) ? KeyValue.of(next.key(), m_map.apply(next.value())) : null;
		}	
	}

	static class KeyGeneratedKVFStream<K,T> implements KVFStream<K,T> {
		private final FStream<T> m_base;
		private final Function<? super T,? extends K> m_keyGen;
		
		KeyGeneratedKVFStream(FStream<T> base, Function<? super T,? extends K> keyGen) {
			m_base = base;
			m_keyGen = keyGen;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,T> next() {
			T next = m_base.next();
			return ( next != null ) ? KeyValue.of(m_keyGen.apply(next), next) : null;
		}	
	}

	static class KeyValueGeneratedKVFStream<T,K,V> implements KVFStream<K,V> {
		private final FStream<T> m_base;
		private final Function<? super T,? extends K> m_keyGen;
		private final Function<? super T,? extends V> m_valueGen;
		
		 KeyValueGeneratedKVFStream(FStream<T> base, Function<? super T,? extends K> keyGen,
								Function<? super T,? extends V> valueGen) {
			m_base = base;
			m_keyGen = keyGen;
			m_valueGen = valueGen;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public KeyValue<K,V> next() {
			T next = m_base.next();
			return ( next != null )
					? KeyValue.of(m_keyGen.apply(next), m_valueGen.apply(next)) : null;
		}	
	}

	static class ToKeyStream<K,V> implements FStream<K> {
		private final KVFStream<K,V> m_base;
		
		ToKeyStream(KVFStream<K,V> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public K next() {
			KeyValue<K,V> next = m_base.next();
			return (next != null) ? next.key() : null;
		}	
	}

	static class ToValueStream<K,V> implements FStream<V> {
		private final KVFStream<K,V> m_base;
		
		ToValueStream(KVFStream<K,V> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public V next() {
			KeyValue<K,V> next = m_base.next();
			return (next != null) ? next.value() : null;
		}	
	}
}
