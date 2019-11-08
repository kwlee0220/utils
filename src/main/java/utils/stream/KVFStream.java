package utils.stream;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Utilities;
import utils.func.FOption;
import utils.func.Tuple;
import utils.stream.KVFStreams.FStreamAdaptor;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface KVFStream<K,V> extends FStream<KeyValue<K,V>> {
	public static <K,V> KVFStream<K,V> downcast(FStream<KeyValue<K,V>> stream) {
		Utilities.checkNotNullArgument(stream, "stream is null");
		
		return new FStreamAdaptor<>(stream);
	}
	
	public static <K,V> KVFStream<K,V> from(List<KeyValue<K,V>> kvList) {
		Utilities.checkNotNullArgument(kvList, "KeyValue list is null");
		
		return new FStreamAdaptor<>(FStream.from(kvList));
	}
	
	public static <K,V> KVFStream<K,V> fromTupleFStream(FStream<Tuple<K,V>> stream) {
		Utilities.checkNotNullArgument(stream, "stream is null");
		
		return downcast(stream.map(t -> KeyValue.of(t._1, t._2)));
	}
	
	public static <K,V> KVFStream<K,V> from(Map<? extends K, ? extends V> map) {
		Utilities.checkNotNullArgument(map, "map is null");
		
		return downcast(FStream.from(map.entrySet().iterator())
								.map(e -> KeyValue.of(e.getKey(), e.getValue())));
	}
	
	public default KVFStream<K,V> filterKey(Predicate<? super K> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return downcast(filter(kv -> pred.test(kv.key())));
	}
	
	public default KVFStream<K,V> filterValue(Predicate<? super V> pred) {
		Utilities.checkNotNullArgument(pred, "predicate is null");
		
		return downcast(filter(kv -> pred.test(kv.value())));
	}
	
	public default <S> FStream<S> map(BiFunction<? super K,? super V,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return map(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default <S> KVFStream<S,V> mapKey(BiFunction<? super K,? super V,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return downcast(map(kv -> KeyValue.of(mapper.apply(kv.key(), kv.value()), kv.value())));
	}
	
	public default <S> KVFStream<S,V> mapKey(Function<? super K,? extends S> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return downcast(map(kv -> KeyValue.of(mapper.apply(kv.key()), kv.value())));
	}
	
	public default <U> KVFStream<K,U> mapValue(BiFunction<? super K,? super V,? extends U> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return downcast(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.key(), kv.value()))));
	}
	
	public default <U> KVFStream<K,U> mapValue(Function<? super V,? extends U> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");

		return downcast(map(kv -> KeyValue.of(kv.key(), mapper.apply(kv.value()))));
	}
	
	public default <T> FStream<T> flatMap(BiFunction<? super K,? super V,FStream<T>> mapper) {
		Utilities.checkNotNullArgument(mapper, "mapper is null");
		
		return flatMap(kv -> mapper.apply(kv.key(), kv.value()));
	}
	
	public default <U> KVFStream<K,U> castValue(Class<? extends U> cls) {
		Utilities.checkNotNullArgument(cls, "target class is null");
		
		return mapValue(cls::cast);
	}
	
	public default void forEach(BiConsumer<? super K,? super V> effect) {
		Utilities.checkNotNullArgument(effect, "effect is null");
		
		forEach(kv -> effect.accept(kv.key(), kv.value()));
	}
	
	public default <C> C collectLeft(C collector, TriConsumer<? super C,? super K,? super V> collect) {
		Utilities.checkNotNullArgument(collector, "collector is null");
		Utilities.checkNotNullArgument(collect, "collect is null");
		
		FOption<KeyValue<K,V>> next;
		while ( (next = next()).isPresent() ) {
			collect.accept(collector, next.get().key(), next.get().value());
		}
		
		return collector;
	}
	
	public default KeyedGroups<K,V> groupBy() {
		return foldLeft(KeyedGroups.create(), (groups,kv) -> groups.add(kv.key(), kv.value()));
	}
	
	public default KVFStream<K,V> sortByKey() {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> ((Comparable)kv1.key()).compareTo(kv2.key()));
		return downcast(sorted);
	}
	
	public default KVFStream<K,V> sortByKey(Comparator<? super K> cmp) {
		FStream<KeyValue<K,V>> sorted = sort((kv1,kv2) -> cmp.compare(kv1.key(), kv2.key()));
		return downcast(sorted);
		
	}
	
	public default FStream<KeyValue<K,V>> toKeyValueStream() {
		return map(KeyValue::of);
	}
	
	public default FStream<K> toKeyStream() {
		return map(kv -> kv.key());
	}
	
	public default FStream<V> toValueStream() {
		return map(kv -> kv.value());
	}
	
	public default <T extends Map<K,V>> T toMap(T map) {
		return collectLeft(map, (accum,kv) -> accum.put(kv.key(), kv.value()));
	}
	
	public default HashMap<K,V> toMap() {
		return toMap(Maps.newHashMap());
	}
}
