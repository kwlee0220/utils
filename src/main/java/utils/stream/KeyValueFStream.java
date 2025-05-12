package utils.stream;

import static utils.Utilities.checkNotNullArgument;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Keyed;
import utils.Tuple;
import utils.stream.FStreams.PeekedStream;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface KeyValueFStream<K,V> extends FStream<KeyValue<K,V>> {
	@SafeVarargs
	public static <K,V> KeyValueFStream<K,V> of(KeyValue<K,V>... values) {
		Preconditions.checkArgument(values != null, "null values");
		
		return from(Arrays.asList(values));
	}
	
	public static <K,V> KeyValueFStream<K,V> from(FStream<KeyValue<K,V>> stream) {
		Preconditions.checkArgument(stream != null, "stream is null");
		
		return new DefaultKeyValueFStream<>(stream);
	}

	public static <K,V> KeyValueFStream<K,V> from(Iterable<KeyValue<K,V>> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");

		return new DefaultKeyValueFStream<>(FStream.from(iter));
	}

	public static <K,V> KeyValueFStream<K,V> from(Iterator<KeyValue<K,V>> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");

		return new DefaultKeyValueFStream<>(FStream.from(iter));
	}
	
	/**
	 * 주어진 {@link Map}객체에 포함된 모든 key-value pair를 반환하는 {@link FStream}를 생성한다.
	 * <p>
	 * 생성된 스트림은 입력 Map객체에 포함된 key-value pair에 해당하는 {@link KeyValue} 객체들을 반환한다.
	 * 
	 * @param <K> {@link Map}객체의 key type.
	 * @param <V> {@link Map}객체의 value type.
	 * @param kvMap	입력 {@link Map} 객체.
	 * @return FStream 객체
	 */
	public static <K,V> KeyValueFStream<K,V> from(Map<K,V> kvMap) {
		Preconditions.checkArgument(kvMap != null, "kvMap is null");
		
		return FStream.from(kvMap.entrySet())
						.toKeyValueStream(KeyValue::from);
	}

	public static <K,V extends Keyed<K>> KeyValueFStream<K,V> fromKeyed(Iterable<V> iter) {
		Preconditions.checkArgument(iter != null, "null iterable");
		
		Function<V,KeyValue<K,V>> mapper = kv -> KeyValue.of(kv.key(), kv);
		return FStream.from(iter).toKeyValueStream(mapper);
	}
	
	public default FStream<K> keys() {
		return map(kv -> kv.key());
	}
	
	public default FStream<V> values() {
		return map(kv -> kv.value());
	}
	
	public default KeyValueFStream<K,V> filterKey(Predicate<? super K> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		
		return from(filter(kv -> pred.test(kv.key())));
	}
	
	public default KeyValueFStream<K,V> filterValue(Predicate<? super V> pred) {
		Preconditions.checkArgument(pred != null, "predicate is null");
		
		return from(filter(kv -> pred.test(kv.value())));
	}
	
	public default <S> FStream<S> map(BiFunction<? super K, ? super V, ? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return map(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default <S> KeyValueFStream<S,V> mapKey(Function<? super K,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(mapper.apply(kv.key()), kv.value())));
	}
	
	public default <S> KeyValueFStream<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(mapper.apply(kv.key(), kv.value()), kv.value())));
	}
	
	public default <U> KeyValueFStream<K,U> mapValue(Function<? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.value()))));
	}
	
	public default <U> KeyValueFStream<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");

		return from(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.key(), kv.value()))));
	}
	
	public default <K2,V2>
	KeyValueFStream<K2, V2> mapKeyValue(BiFunction<? super K, ? super V, KeyValue<K2,V2>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return from(map(kv -> mapper.apply(kv.key(), kv.value())));
	}
	
	public default <T> FStream<T> flatMap(BiFunction<? super K,? super V,FStream<T>> mapper) {
		Preconditions.checkArgument(mapper != null, "mapper is null");
		
		return flatMap(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default void forEach(BiConsumer<? super K,? super V> effect) {
		Preconditions.checkArgument(effect != null, "effect is null");
		
		forEach(kv -> effect.accept(kv.key(), kv.value()));
	}
	
	public default KeyValueFStream<K,V> peek(Consumer<? super KeyValue<K,V>> effect) {
		checkNotNullArgument(effect, "effect is null");
		
		return from(new PeekedStream<>(this, effect));
	}
	
	public default KeyedGroups<K,V> groupByKey() {
		return collect(KeyedGroups.create(), (g,kv) -> g.add(kv.key(), kv.value()));
	}
	
	public default KeyValueFStream<K,V> sortByKey() {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> ((Comparable)kv1.key()).compareTo(kv2.key()));
		return from(sorted);
	}
	
	public default <R> KeyValueFStream<K,Tuple<V,R>> innerJoin(KeyValueFStream<K,R> right) {
		return new InnerJoinedFStream<>(this, right);
	}
	
	public default <R> KeyValueFStream<K,Tuple<List<V>,List<R>>> outerJoin(KeyValueFStream<K,R> right) {
		return new OuterJoinedFStream<>(this, right);
	}
	
	public default <M extends Map<K,V>> M toMap(M map) {
		return collect(map, (accum,kv) -> accum.put(kv.key(), kv.value()));
	}
	
	public default HashMap<K,V> toMap() {
		return toMap(Maps.newHashMap());
	}
	
	public default <K2,V2>
	KeyValueFStream<K2,V2> liftKeyValues(Function<KeyValueFStream<K,V>, KeyValueFStream<K2,V2>> streamFunc) {
        return streamFunc.apply(this);
    }
}
