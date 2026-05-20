package utils.stream;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Maps;

import utils.KeyValue;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class KeyValueFStreamTest {
	@Test
	public void testToMap1() throws Exception {
		List<Integer> keys = List.of(1, 2, 3, 4, 5);
		KeyValueFStream<Integer, String> stream
								= FStream.from(keys).toKeyValueStream(v -> KeyValue.of(v, ""+v));
		
		Map<Integer,String> map = Maps.newLinkedHashMap();
		stream.toMap(map);
		
		Assertions.assertEquals(5, map.size());
		Assertions.assertEquals("1", map.get(1));
		Assertions.assertEquals("2", map.get(2));
		Assertions.assertEquals("3", map.get(3));
		Assertions.assertEquals("4", map.get(4));
		Assertions.assertEquals("5", map.get(5));
	}
	
	@Test
	public void testToMap2() throws Exception {
		List<Integer> keys = List.of(1, 2, 3, 4, 5);
		KeyValueFStream<Integer, String> stream
								= FStream.from(keys).toKeyValueStream(v -> KeyValue.of(v, ""+v));
		
		var result = stream.toMap();
		
		Assertions.assertEquals(5, result.size());
		Assertions.assertEquals("1", result.get(1));
		Assertions.assertEquals("2", result.get(2));
		Assertions.assertEquals("3", result.get(3));
		Assertions.assertEquals("4", result.get(4));
		Assertions.assertEquals("5", result.get(5));
	}
	
	static KeyValue<Integer, String> of(int key) {
		return KeyValue.of(key, "" + key);
	}
}
