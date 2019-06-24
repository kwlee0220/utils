package utils.stream;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import utils.Utilities;
import utils.func.FOption;
import utils.func.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ZippedFStream<T,S> implements FStream<Tuple2<T,S>> {
	private final FStream<? extends T> m_src1;
	private final FStream<? extends S> m_src2;
	
	ZippedFStream(FStream<? extends T> src, FStream<? extends S> src2) {
		Utilities.checkNotNullArgument(src);
		Utilities.checkNotNullArgument(src2);
		
		m_src1 = src;
		m_src2 = src2;
	}
	
	public void close() throws Exception {
		Try<Void> tried1 = m_src1.closeQuietly(); 
		Try<Void> tried2 = m_src2.closeQuietly();
		
		tried1.get();
		tried2.get();
	}

	@Override
	public FOption<Tuple2<T,S>> next() {
		FOption<? extends T> next1 = m_src1.next();
		FOption<? extends S> next2 = m_src2.next();
		
		return ( next1.isPresent() && next2.isPresent() )
				? FOption.of(Tuple.of((T)next1.get(), (S)next2.get()))
				: FOption.empty();
	}
}
