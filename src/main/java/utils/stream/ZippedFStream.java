package utils.stream;

import utils.Utilities;
import utils.func.FOption;
import utils.func.Try;
import utils.func.Tuple;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class ZippedFStream<T,S> implements FStream<Tuple<T,S>> {
	private final FStream<? extends T> m_src1;
	private final FStream<? extends S> m_src2;
	private final boolean m_longest;
	
	ZippedFStream(FStream<? extends T> src, FStream<? extends S> src2, boolean longest) {
		Utilities.checkNotNullArgument(src);
		Utilities.checkNotNullArgument(src2);
		
		m_src1 = src;
		m_src2 = src2;
		m_longest = longest;
	}
	
	public void close() throws Exception {
		Try<Void> tried1 = m_src1.closeQuietly(); 
		Try<Void> tried2 = m_src2.closeQuietly();
		
		tried1.get();
		tried2.get();
	}

	@Override
	public FOption<Tuple<T,S>> next() {
		FOption<? extends T> next1 = m_src1.next();
		FOption<? extends S> next2 = m_src2.next();
		
		if ( next1.isPresent() && next2.isPresent() ) {
			return FOption.of(Tuple.of((T)next1.get(), (S)next2.get()));
		}
		else if ( !m_longest || (next1.isAbsent() && next2.isAbsent()) ) {
			return FOption.empty();
		}
		else {
			T left = next1.getOrNull();
			S right = next2.getOrNull();
			return FOption.of(Tuple.of(left, right));
		}
	}
}
