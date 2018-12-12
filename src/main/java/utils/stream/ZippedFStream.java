package utils.stream;

import java.util.Objects;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import utils.func.FOption;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ZippedFStream<T,U> implements FStream<Tuple2<T,U>> {
	private final FStream<? extends T> m_src1;
	private final FStream<? extends U> m_src2;
	
	ZippedFStream(FStream<? extends T> src, FStream<? extends U> src2) {
		Objects.requireNonNull(src);
		Objects.requireNonNull(src2);
		
		m_src1 = src;
		m_src2 = src2;
	}
	
	public void close() throws Exception {
		Try<Void> tried1 = Try.run(() -> m_src1.close());
		Try<Void> tried2 = Try.run(() -> m_src2.close());
		
		tried1.get();
		tried2.get();
	}

	@Override
	public FOption<Tuple2<T,U>> next() {
		FOption<? extends T> next1 = m_src1.next();
		FOption<? extends U> next2 = m_src2.next();
		
		return ( next1.isPresent() && next2.isPresent() )
				? FOption.of(Tuple.of((T)next1.get(), (U)next2.get()))
				: FOption.empty();
	}
}
