package utils;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 두 맵을 겹쳐 하나로 보이게 하는 맵 뷰.
 * <p>
 * 키 조회는 {@code primary} 맵을 먼저 보고, {@code primary}에 키가 없을 때만 {@code fallback} 맵을 본다.
 * 두 맵의 참조를 그대로 보관하므로 원래 맵의 변경이 이후 조회에 반영된다.
 * {@link #entrySet()}은 호출할 때마다 두 맵을 합친 새 {@link HashSet}을 만들므로 순서가 보장되지 않으며,
 * {@code size()}, {@code keySet()}, {@code values()} 등도 이 복사본을 기준으로 계산된다.
 * <p>
 * <b>주의:</b> {@code put}은 {@link UnsupportedOperationException}을 던진다. {@code remove}와 {@code clear}는
 * 예외 없이 끝나지만 {@link #entrySet()}의 복사본만 바꾸므로 원래 두 맵은 바뀌지 않는다.
 *
 * @param <K>	키의 타입.
 * @param <V>	값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class ChainedMap<K, V> extends AbstractMap<K, V> {
    private final Map<K, V> m_primary; 
    private final Map<K, V> m_fallback;

    /**
     * 두 맵을 겹친 {@link ChainedMap}을 만든다.
     * <p>
     * 인자의 {@code null} 여부를 검사하지 않으므로, {@code null}을 넘기면 맵을 사용할 때
     * {@link NullPointerException}이 발생한다.
     *
     * @param primary	우선 조회되는 맵.
     * @param fallback	{@code primary}에 키가 없을 때 조회되는 맵.
     */
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
