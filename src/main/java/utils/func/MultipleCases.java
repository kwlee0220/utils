package utils.func;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import utils.KeyValue;
import utils.Preconditions;
import utils.stream.TriConsumer;

/**
 * Map을 switch-like fluent DSL로 다루는 빌더.
 * <p>
 * {@link #switching(Map)}으로 시작해 {@link #ifCase(Object)}/{@link #ifCase(Object, Object)}로 키별 처리,
 * {@link #otherwise()}/{@link #otherwise(Object)}로 남은 키-값을 일괄 처리한다.
 * <pre>{@code
 * MultipleCases.switching(map)
 *     .ifCase("k1").consume(v1Handler)
 *     .ifCase("k2").consume(v2Handler)
 *     .otherwise().forEach((k, v) -> defaultHandler(k, v));
 * }</pre>
 * <p>
 * 키 부재와 {@code key → null}을 구분한다. 키가 없으면 {@code consume}이 호출되지 않고,
 * 키가 존재하지만 값이 {@code null}이면 {@code consumer.accept(null)}이 호출된다.
 * <p>
 * Context-bearing 변형({@link #ifCase(Object, Object)} / {@link #otherwise(Object)})은 매번 호출에서
 * 추가 인자를 함께 전달한다.
 * <p>
 * 본 클래스는 thread-safe하지 않다. {@link #switching(Map)} 시점에 입력 map을 방어적으로 복사하므로
 * 호출 이후 외부 변경은 영향을 주지 않는다.
 *
 * @param <K>	key 타입.
 * @param <V>	value 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MultipleCases<K,V> {
	private final Map<K,V> m_cases;
	
	/**
	 * 주어진 map에 대한 switch-like 빌더를 생성한다.
	 * <p>
	 * 입력 map은 방어적으로 복사되므로, 빌더 생성 후 외부에서 map을 변경해도 빌더에는 영향이 없다.
	 *
	 * @param <K>	key 타입.
	 * @param <V>	value 타입.
	 * @param cases	switch 대상 map. {@code null}이면 {@link IllegalArgumentException}이 발생한다.
	 * @return		새 {@link MultipleCases} 인스턴스.
	 */
	public static <K,V> MultipleCases<K,V> switching(Map<K,V> cases) {
		Preconditions.checkNotNullArgument(cases, "cases is null");
		return new MultipleCases<>(new HashMap<>(cases));
	}

	private MultipleCases(Map<K,V> cases) {
		m_cases = cases;
	}

	/**
	 * 지정된 키에 대응하는 case를 시작한다.
	 *
	 * @param key	조회할 key.
	 * @return		key의 존재 여부와 값을 담은 {@link Case}. 후속 {@code consume(...)}을 통해 처리한다.
	 */
	public Case<K,V> ifCase(K key) {
		Map<K,V> remains = new HashMap<>(m_cases);
		boolean present = remains.containsKey(key);

		return new Case<>(key, remains.remove(key), present, remains);
	}

	/**
	 * 처리되지 않은 나머지 case들을 일괄 처리할 {@link Otherwise}를 반환한다.
	 *
	 * @return	남은 key-value 쌍을 보유한 {@link Otherwise}.
	 */
	public Otherwise<K,V> otherwise() {
		return new Otherwise<>(toKeyValueList(m_cases));
	}

	/**
	 * 지정된 키에 대응하는 case를 context와 함께 시작한다.
	 *
	 * @param <X>		context 타입.
	 * @param key		조회할 key.
	 * @param context	consumer에 함께 전달될 context 객체.
	 * @return			context-bearing {@link ContextedCase}.
	 */
	public <X> ContextedCase<K,V,X> ifCase(K key, X context) {
		Map<K,V> remains = new HashMap<>(m_cases);
		boolean present = remains.containsKey(key);

		return new ContextedCase<>(context, key, remains.remove(key), present, remains);
	}

	/**
	 * 처리되지 않은 나머지 case들을 context와 함께 일괄 처리할 {@link ContextedOtherwise}를 반환한다.
	 *
	 * @param <X>		context 타입.
	 * @param context	consumer에 함께 전달될 context 객체.
	 * @return			남은 key-value 쌍과 context를 보유한 {@link ContextedOtherwise}.
	 */
	public <X> ContextedOtherwise<K,V,X> otherwise(X context) {
		return new ContextedOtherwise<>(context, toKeyValueList(m_cases));
	}

	private static <K,V> List<KeyValue<K,V>> toKeyValueList(Map<K,V> map) {
		return map.entrySet().stream()
				.<KeyValue<K,V>>map(KeyValue::from)
				.collect(Collectors.toList());
	}
	
	public static final class Case<K,V> {
		private final K m_key;
		@Nullable private final V m_value;
		private final boolean m_present;
		private final Map<K,V> m_others;

		private Case(K key, V value, boolean present, Map<K,V> others) {
			m_key = key;
			m_value = value;
			m_present = present;
			m_others = others;
		}

		/**
		 * Case에 해당하는 키가 존재하면 그 값을 consumer에 전달한다.
		 * <p>
		 * map이 {@code key → null}을 합법적으로 가질 수 있으므로, consumer는 value가 {@code null}일 수
		 * 있음을 처리해야 한다("부재"는 consumer 미호출, "값이 null"은 {@code consumer.accept(null)}로 구분).
		 *
		 * @param consumer	value를 처리하는 consumer.
		 * @return			남은 case들을 이용한 새 {@link MultipleCases} 인스턴스.
		 */
		public MultipleCases<K,V> consume(Consumer<V> consumer) {
			if ( m_present ) {
				consumer.accept(m_value);
			}

			// 남은 case들을 이용하여 'MultipleCases' 만들어서 반환한다.
			return new MultipleCases<>(m_others);
		}
	}

	public static class ContextedCase<K,V,X> {
		private final X m_context;
		private final K m_key;
		@Nullable private final V m_value;
		private final boolean m_present;
		private final Map<K,V> m_remains;

		private ContextedCase(X context, K key, V value, boolean present, Map<K,V> remains) {
			m_context = context;
			m_key = key;
			m_value = value;
			m_present = present;
			m_remains = remains;
		}

		/**
		 * Case에 해당하는 키가 존재하면 그 값과 context를 consumer에 전달한다.
		 * <p>
		 * map이 {@code key → null}을 합법적으로 가질 수 있으므로, consumer는 value가 {@code null}일 수
		 * 있음을 처리해야 한다("부재"는 consumer 미호출, "값이 null"은 {@code consumer.accept(null, ctx)}로 구분).
		 *
		 * @param consumer	value와 context를 처리하는 consumer.
		 * @return			남은 case들을 이용한 새 {@link MultipleCases} 인스턴스.
		 */
		public MultipleCases<K,V> consume(BiConsumer<V,X> consumer) {
			if ( m_present ) {
				consumer.accept(m_value, m_context);
			}
			return new MultipleCases<>(m_remains);
		}
	}
	
	public static class Otherwise<K,V> {
		private final List<KeyValue<K,V>> m_remaining;

		private Otherwise(List<KeyValue<K,V>> remaining) {
			m_remaining = remaining;
		}

		/**
		 * 처리되지 않고 남은 모든 key-value 쌍에 대해 consumer를 호출한다.
		 *
		 * @param consumer	각 key-value 쌍을 처리하는 consumer.
		 */
		public void forEach(BiConsumer<K,V> consumer) {
			m_remaining.forEach(kv -> consumer.accept(kv.key(), kv.value()));
		}
	}

	public static class ContextedOtherwise<K,V,X> {
		private final X m_context;
		private final List<KeyValue<K,V>> m_remaining;

		private ContextedOtherwise(X context, List<KeyValue<K,V>> remaining) {
			m_context = context;
			m_remaining = remaining;
		}

		/**
		 * 처리되지 않고 남은 모든 key-value 쌍과 context를 함께 consumer에 전달한다.
		 *
		 * @param consumer	각 key-value-context 조합을 처리하는 consumer.
		 */
		public void forEach(TriConsumer<K,V,X> consumer) {
			m_remaining.forEach(kv -> consumer.accept(kv.key(), kv.value(), m_context));
		}
	}
}