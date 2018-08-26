package utils.stream;

import com.google.common.base.Preconditions;

import io.vavr.Tuple2;
import io.vavr.control.Option;
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
	public Option<Tuple2<T,U>> next() {
		Option<T> next1 = (Option<T>)m_src.next();
		Option<U> next2 = (Option<U>)m_src2.next();
		
		return ( next1.isDefined() && next2.isDefined() )
				? Option.some(new Tuple2<>(next1.get(), next2.get()))
				: Option.none();
	}
}
