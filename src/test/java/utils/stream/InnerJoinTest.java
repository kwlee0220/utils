package utils.stream;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.KeyValue;
import utils.Tuple;
import utils.Tuple3;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class InnerJoinTest {
	@Before
	public void setup() {
	}
	
	@Test
	public void test0() throws Exception {
		KeyValueFStream<Integer,String> stream1
							= KeyValueFStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"));
		KeyValueFStream<Integer,String> stream2
							= KeyValueFStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"), KeyValue.of(3, "c"));
		List<Tuple<String,String>> result = stream1.innerJoin(stream2)
													.values()
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test1() throws Exception {
		KeyValueFStream<Integer,String> stream1
				= KeyValueFStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"));
		KeyValueFStream<Integer,String> stream2
				= KeyValueFStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"), KeyValue.of(3, "c"), KeyValue.of(1, "a2"));
		
		List<Tuple<String,String>> result = stream1.innerJoin(stream2)
													.values()
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"), Tuple.of("A", "a2"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test2() throws Exception {
		KeyValueFStream<Integer,String> stream1
				= KeyValueFStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"), KeyValue.of(1, "A2"));
		KeyValueFStream<Integer,String> stream2
								= KeyValueFStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"), KeyValue.of(3, "c"));
		
		List<Tuple<String,String>> result = stream1.innerJoin(stream2)
													.values()
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A2", "a"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test3() throws Exception {
		KeyValueFStream<Integer,String> stream1
				= KeyValueFStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"), KeyValue.of(1, "A2"));
		KeyValueFStream<Integer,String> stream2
				= KeyValueFStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"), KeyValue.of(3, "c"), KeyValue.of(1, "a2"));
		
		List<Tuple<String,String>> result = stream1.innerJoin(stream2)
													.values()
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A2", "a"), Tuple.of("C", "c"),
							Tuple.of("A", "a2"), Tuple.of("A2", "a2"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test4() throws Exception {
		List<KeyValue<Integer,String>> list1 = List.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"),
														KeyValue.of(3, "C"), KeyValue.of(1, "A2"));
		List<KeyValue<Integer,String>> list2 = List.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"),
														KeyValue.of(3, "c"), KeyValue.of(1, "a2"));
		
		List<Tuple<String,String>> result = KeyValueFStreams.innerJoin(list1, list2)
														.values()
														.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A2", "a"), Tuple.of("C", "c"),
							Tuple.of("A", "a2"), Tuple.of("A2", "a2"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test5() throws Exception {
		List<KeyValue<Integer,String>> list1 = List.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"), KeyValue.of(3, "C"));
		List<KeyValue<Integer,String>> list2 = List.of(KeyValue.of(1, "a"), KeyValue.of(2, "b"), KeyValue.of(3, "c"));
		List<KeyValue<Integer,String>> list3 = List.of(KeyValue.of(1, "1"), KeyValue.of(2, "2"), KeyValue.of(3, "3"));

		List<Tuple3<String,String,String>> result
					= KeyValueFStreams.innerJoin(list1, list2, list3)
									.values()
									.toList();
		var answer = List.of(Tuple.of("A", "a", "1"), Tuple.of("B", "b", "2"), Tuple.of("C", "c", "3"));
		Assert.assertEquals(answer, result);
	}
}
