package utils.stream;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Tuple;
import utils.func.FOption;
import utils.stream.KeyValueFStreams.AbstractKeyValueFStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class InnerJoinedFStream<K,TL,TR> extends AbstractKeyValueFStream<K,Tuple<TL,TR>> {
	private final KeyValueFStream<K,TL> m_leftStream;
	private final KeyValueFStream<K,TR> m_rightStream;
	private Map<K,List<TL>> m_leftDict;
	private Iterator<TL> m_leftIter;
	private K m_key;
	private TR m_right;
	
	InnerJoinedFStream(KeyValueFStream<K,TL> left, KeyValueFStream<K,TR> right) {
		m_leftStream = left;
		m_rightStream = right;
	}

	@Override
	protected void initialize() {
		m_leftDict = Maps.newHashMap();
		for ( KeyValue<K,TL> left: m_leftStream ) {
			m_key = left.key();
			m_leftDict.computeIfAbsent(m_key, k -> Lists.newArrayList()).add(left.value());
		}
		
		m_leftIter = Collections.emptyIterator();
	}

	@Override
	protected void closeInGuard() throws Exception { }

	@Override
	protected FOption<KeyValue<K,Tuple<TL,TR>>> nextInGuard() {
		while ( !m_leftIter.hasNext() ) {
			FOption<KeyValue<K,TR>> onext = m_rightStream.next();
			if ( onext.isAbsent() ) {
				return FOption.empty();
			}

			m_key = onext.get().key();
			m_right = onext.get().value();
			
			List<TL> leftMatches = m_leftDict.get(m_key);
			m_leftIter = (leftMatches != null) ? leftMatches.iterator() : Collections.emptyIterator();
		}
		
		TL left = m_leftIter.next();
		return FOption.of(KeyValue.of(m_key, Tuple.of(left, m_right)));
	}
}
