package utils.stream;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

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
		
		Assert.assertEquals(5, map.size());
		Assert.assertEquals("1", map.get(1));
		Assert.assertEquals("2", map.get(2));
		Assert.assertEquals("3", map.get(3));
		Assert.assertEquals("4", map.get(4));
		Assert.assertEquals("5", map.get(5));
	}
	
	@Test
	public void testToMap2() throws Exception {
		List<Integer> keys = List.of(1, 2, 3, 4, 5);
		KeyValueFStream<Integer, String> stream
								= FStream.from(keys).toKeyValueStream(v -> KeyValue.of(v, ""+v));
		
		var result = stream.toMap();
		
		Assert.assertEquals(5, result.size());
		Assert.assertEquals("1", result.get(1));
		Assert.assertEquals("2", result.get(2));
		Assert.assertEquals("3", result.get(3));
		Assert.assertEquals("4", result.get(4));
		Assert.assertEquals("5", result.get(5));
	}
	
	static KeyValue<Integer, String> of(int key) {
		return KeyValue.of(key, "" + key);
	}
}
