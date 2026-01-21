package utils;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ChainedMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> m_primary; 
    private final Map<K, V> m_fallback;

    public ChainedMap(Map<K, V> primary, Map<K, V> fallback) {
        this.m_primary = primary;
        this.m_fallback = fallback;
    }

    @Override
    public V get(Object key) {
        if (m_primary.containsKey(key)) {
            return m_primary.get(key);
        }
        return m_fallback.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return m_primary.containsKey(key) || m_fallback.containsKey(key);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        Set<Entry<K, V>> entries = new HashSet<>();

        // fallback 먼저
        for (Entry<K, V> e : m_fallback.entrySet()) {
            if (!m_primary.containsKey(e.getKey())) {
                entries.add(e);
            }
        }

        // primary가 우선
        entries.addAll(m_primary.entrySet());
        return entries;
    }
}
