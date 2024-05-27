package utils.stream;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.func.FOption;
import utils.func.Tuple;
import utils.stream.FStreams.AbstractFStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class InnerJoinedFStream<TL,TR, K> extends AbstractFStream<Tuple<TL,TR>> {
	private final FStream<TL> m_left;
	private final Function<TL,K> m_leftKeyer;
	private final FStream<TR> m_rightStream;
	private final Function<TR,K> m_rightKeyer;
	private Map<K,List<TL>> m_leftDict;
	private Iterator<TL> m_leftIter;
	private TR m_right;
	
	InnerJoinedFStream(FStream<TL> left, FStream<TR> right,
						Function<TL,K> leftKeyer, Function<TR,K> rightKeyer) {
		m_left = left;
		m_rightStream = right;
		m_leftKeyer = leftKeyer;
		m_rightKeyer = rightKeyer;
	}

	@Override
	protected void initialize() {
		m_leftDict = Maps.newHashMap();
		for ( TL left: m_left ) {
			K key = m_leftKeyer.apply(left);
			m_leftDict.computeIfAbsent(key, k -> Lists.newArrayList()).add(left);
		}
		
		m_leftIter = Collections.emptyIterator();
	}

	@Override
	protected void closeInGuard() throws Exception { }

	@Override
	protected FOption<Tuple<TL, TR>> nextInGuard() {
		while ( !m_leftIter.hasNext() ) {
			FOption<TR> onext = m_rightStream.next();
			if ( onext.isAbsent() ) {
				return FOption.empty();
			}
			m_right = onext.getUnchecked();
			
			List<TL> leftMatches = m_leftDict.get(m_rightKeyer.apply(onext.get()));
			m_leftIter = (leftMatches != null) ? leftMatches.iterator() : Collections.emptyIterator();
		}
		
		TL left = m_leftIter.next();
		return FOption.of(Tuple.of(left, m_right));
	}
}
