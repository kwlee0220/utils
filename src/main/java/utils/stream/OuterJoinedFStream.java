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
import utils.func.Funcs;
import utils.stream.KeyValueFStreams.AbstractKeyValueFStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
class OuterJoinedFStream<K,TL,TR> extends AbstractKeyValueFStream<K,Tuple<List<TL>,List<TR>>> {
	private final KeyValueFStream<K,TL> m_left;
	private final KeyValueFStream<K,TR> m_right;
	private Map<K,List<TL>> m_leftDict;
	private Map<K,List<TR>> m_rightDict;
	private Iterator<K> m_keyIter;
	
	OuterJoinedFStream(KeyValueFStream<K,TL> left, KeyValueFStream<K,TR> right) {
		m_left = left;
		m_right = right;
	}

	@Override
	protected void initialize() {
		m_leftDict = Maps.newHashMap();
		for ( KeyValue<K,TL> left: m_left ) {
			K key = left.key();
			m_leftDict.computeIfAbsent(key, k -> Lists.newArrayList()).add(left.value());
		}
		
		m_rightDict = Maps.newHashMap();
		for ( KeyValue<K,TR> right: m_right ) {
			K key = right.key();
			m_rightDict.computeIfAbsent(key, k -> Lists.newArrayList()).add(right.value());
		}
		
		m_keyIter = m_leftDict.keySet().iterator();
	}

	@Override
	protected void closeInGuard() throws Exception {
	}

	@Override
	protected FOption<KeyValue<K,Tuple<List<TL>,List<TR>>>> nextInGuard() {
		if ( m_keyIter.hasNext() ) {
			K key = m_keyIter.next();
			List<TL> leftBucket = m_leftDict.get(key);
			List<TR> rightBucket = m_rightDict.remove(key);
			if ( rightBucket == null ) {
				rightBucket = Collections.emptyList();
			}
			
			return FOption.of(KeyValue.of(key, Tuple.of(leftBucket, rightBucket)));
		}
		else {
			K unmatchedKey = Funcs.getFirstOrNull(m_rightDict.keySet());
			if ( unmatchedKey != null ) {
				List<TR> rightBucket = m_rightDict.remove(unmatchedKey);
				return FOption.of(KeyValue.of(unmatchedKey, Tuple.of(Collections.emptyList(), rightBucket)));
			}
			else {
				return FOption.empty();
			}
		}
	}
}
