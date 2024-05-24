package utils.stream;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.func.FOption;
import utils.func.Funcs;
import utils.func.Tuple;
import utils.stream.FStreams.AbstractFStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class OuterJoinedFStream<TL,TR, K> extends AbstractFStream<Tuple<List<TL>,List<TR>>> {
	private final FStream<TL> m_left;
	private final Function<TL,K> m_leftKeyer;
	private final FStream<TR> m_right;
	private final Function<TR,K> m_rightKeyer;
	private Map<K,List<TL>> m_leftDict;
	private Map<K,List<TR>> m_rightDict;
	private Iterator<K> m_keyIter;
	
	OuterJoinedFStream(FStream<TL> left, FStream<TR> right,
						Function<TL,K> leftKeyer, Function<TR,K> rightKeyer) {
		m_left = left;
		m_right = right;
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
		
		m_rightDict = Maps.newHashMap();
		for ( TR right: m_right ) {
			K key = m_rightKeyer.apply(right);
			m_rightDict.computeIfAbsent(key, k -> Lists.newArrayList()).add(right);
		}
		
		m_keyIter = m_leftDict.keySet().iterator();
	}

	@Override
	protected void closeInGuard() throws Exception {
	}

	@Override
	protected FOption<Tuple<List<TL>, List<TR>>> nextInGuard() {
		if ( m_keyIter.hasNext() ) {
			K key = m_keyIter.next();
			List<TL> leftBucket = m_leftDict.get(key);
			List<TR> rightBucket = m_rightDict.remove(key);
			if ( rightBucket == null ) {
				rightBucket = Collections.emptyList();
			}
			return FOption.of(Tuple.of(leftBucket, rightBucket));
		}
		else {
			K unmatchedKey = Funcs.getFirstOrNull(m_rightDict.keySet());
			if ( unmatchedKey != null ) {
				List<TR> rightBucket = m_rightDict.remove(unmatchedKey);
				return FOption.of(Tuple.of(Collections.emptyList(), rightBucket));
			}
			else {
				return FOption.empty();
			}
		}
	}
}
