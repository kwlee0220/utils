package utils.stream;

import java.util.Objects;

import io.vavr.Tuple2;
import io.vavr.control.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ZippedFStream<T,U> implements FStream<Tuple2<T,U>> {
	private final FStream<? extends T> m_src;
	private final FStream<? extends U> m_src2;;
	
	ZippedFStream(FStream<? extends T> src, FStream<? extends U> src2) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(src2);
		
		m_src = src;
		m_src2 = src2;
	}
	
	public void close() throws Exception {
		Try<Void> tried1 = Try.run(() -> m_src.close());
		Try<Void> tried2 = Try.run(() -> m_src2.close());
		
		tried1.get();
		tried2.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Tuple2<T,U> next() {
		T next1 = m_src.next();
		U next2 = m_src2.next();
		
		return ( next1 != null && next2 != null )
				? new Tuple2<>(next1, next2)
				: null;
	}
}
