package utils.stream;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.Tuple;
import utils.Tuple3;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class KVMatchTest {
	private Map<Integer, String> m_data;
	private List<KeyValue<Integer,String>> m_data2;
	private Map<Integer,String> m_lut1;
	private Map<Integer,List<String>> m_lut2;
	
	@Before
	public void setup() {
		m_data = Maps.newLinkedHashMap();
		m_data.put(1, "A");
		m_data.put(2, "B");
		m_data.put(3, "C");

		m_data2 = List.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"), KeyValue.of(1, "A2"));

		m_lut1 = Map.of(1, "a", 4, "d", 3, "c");
		m_lut2 = Map.of(1, List.of("a", "a2"), 4, List.of("d"), 3, List.of("c"));
	}
	
	@Test
	public void test0() throws Exception {
		Map<Integer,String> lut = Map.of(1, "a", 4, "d", 3, "c");
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data)
								.match(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);

		result = KeyValueFStream.from(m_data)
								.match(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("B", null), Tuple.of("C", "c"));
		Assert.assertEquals(answer2, result);
	}
	
	@Test
	public void test1() throws Exception {
		Map<Integer,List<String>> lut = Map.of(1, List.of("a", "a2"), 4, List.of("d"), 3, List.of("c"));
		
		List<Tuple<String,String>> result;
		result = KeyValueFStream.from(m_data)
								.flatMatch(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);

		result = KeyValueFStream.from(m_data)
								.flatMatch(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("B", null), Tuple.of("C", "c"));
		Assert.assertEquals(answer2, result);
	}
	
	@Test
	public void test2() throws Exception {
		Map<Integer,String> lut = Map.of(1, "a", 4, "d", 3, "c");
		
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data2)
								.match(lut)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"), Tuple.of("A2", "a"));
		Assert.assertEquals(answer, result);
		
		result = KeyValueFStream.from(m_data2)
								.match(lut, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("B", null), Tuple.of("C", "c"), Tuple.of("A2", "a"));
		Assert.assertEquals(answer2, result);
	}
	
	@Test
	public void test3() throws Exception {
		List<Tuple<String,String>> result;
		
		result = KeyValueFStream.from(m_data2)
								.flatMatch(m_lut2)
								.values()
								.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("C", "c"), Tuple.of("A2", "a"),
							Tuple.of("A2", "a2"));
		Assert.assertEquals(answer, result);
		
		result = KeyValueFStream.from(m_data2)
								.flatMatch(m_lut2, true)
								.values()
								.toList();
		var answer2 = List.of(Tuple.of("A", "a"), Tuple.of("A", "a2"), Tuple.of("B", null),
								Tuple.of("C", "c"), Tuple.of("A2", "a"), Tuple.of("A2", "a2"));
		Assert.assertEquals(answer2, result);
	}
}
