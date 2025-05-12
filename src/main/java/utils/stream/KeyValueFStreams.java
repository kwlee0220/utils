package utils.stream;

import java.util.Collections;
import java.util.List;

import utils.KeyValue;
import utils.Tuple;
import utils.Tuple3;
import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueFStreams {
	public static <K,TL,TR>
	KeyValueFStream<K,Tuple<TL,TR>> innerJoin(Iterable<KeyValue<K,TL>> left, Iterable<KeyValue<K,TR>> right) {
		return KeyValueFStream.from(left)
								.innerJoin(KeyValueFStream.from(right));
	}
	public static <K,V1,V2,V3>
	KeyValueFStream<K,Tuple3<V1,V2,V3>> innerJoin(Iterable<KeyValue<K,V1>> src1, Iterable<KeyValue<K,V2>> src2,
													Iterable<KeyValue<K,V3>> src3) {
		var base = innerJoin(src1, src2)
						.innerJoin(KeyValueFStream.from(src3))
						.map(kkv -> {
							K key = kkv.key();
							var left = kkv.value()._1();
							var right = kkv.value()._2();
							return KeyValue.of(key, Tuple.of(left._1(), left._2(), right));
						});
		return KeyValueFStream.from(base);
	}
	
	public static <K,V1,V2>
	KeyValueFStream<K,Tuple<List<V1>,List<V2>>> outerJoin(Iterable<KeyValue<K,V1>> left, Iterable<KeyValue<K,V2>> right) {
		return KeyValueFStream.from(left)
								.outerJoin(KeyValueFStream.from(right));
	}
	public static <K,V1,V2,V3>
	KeyValueFStream<K,Tuple3<List<V1>,List<V2>,List<V3>>>
	outerJoin(Iterable<KeyValue<K,V1>> src1, Iterable<KeyValue<K,V2>> src2, Iterable<KeyValue<K,V3>> src3) {
		var base = outerJoin(src1, src2)
						.outerJoin(KeyValueFStream.from(src3))
						.map(kkv -> {
							K key = kkv.key();
							List<V1> matches1;
							List<V2> matches2;
							
							var t12List = kkv.value()._1;
							if ( t12List.size() >= 0 ) {
								var tup12 = t12List.get(0);
								matches1 = tup12._1();
								matches2 = tup12._2();
							}
							else {
								matches1 = Collections.emptyList();
								matches2 = Collections.emptyList();
							}
							List<V3> matches3 = kkv.value()._2();
							
							return KeyValue.of(key, Tuple.of(matches1, matches2, matches3));
						});
		return KeyValueFStream.from(base);
	}
	
	static abstract class AbstractKeyValueFStream<K,V> implements KeyValueFStream<K,V> {
		private boolean m_closed = false;
		private boolean m_eos = false;
		private boolean m_initialized = false;
		
		abstract protected void closeInGuard() throws Exception;
		abstract protected FOption<KeyValue<K,V>> nextInGuard();
		protected void initialize() { }

		@Override
		public final void close() throws Exception {
			if ( !m_closed ) {
				m_closed = true;
				m_eos = true;
				closeInGuard();
			}
		}

		@Override
		public FOption<KeyValue<K,V>> next() {
			checkNotClosed();

			if ( m_eos ) {
				return FOption.empty();
			}
			if ( !m_initialized ) {
				initialize();
				m_initialized = true;
			}
			
			return nextInGuard().ifAbsent(() -> m_eos = true);
		}
		
		public boolean isClosed() {
			return m_closed;
		}
		
		public void checkNotClosed() {
			if ( m_closed ) {
				throw new IllegalStateException("already closed: " + this);
			}
		}
		
		protected void markEndOfStream() {
			m_eos = true;
		}
		
		public boolean isEndOfStream() {
			return m_eos;
		}
	}

	static class FStreamAdaptor<K,V> implements KeyValueFStream<K,V> {
		private final FStream<KeyValue<K,V>> m_base;
		
		FStreamAdaptor(FStream<KeyValue<K,V>> base) {
			m_base = base;
		}

		@Override
		public void close() throws Exception {
			m_base.close();
		}

		@Override
		public FOption<KeyValue<K,V>> next() {
			return m_base.next();
		}	
	}
}
