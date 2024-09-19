package utils.stream;


import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import utils.KeyValue;
import utils.func.Tuple;

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
		FStream<KeyValue<Integer,String>> stream1 = FStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"),
																KeyValue.of(3, "C"));
		FStream<KeyValue<Integer,String>> stream2 = FStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"),
																KeyValue.of(3, "c"));
		List<Tuple<String,String>> result = stream1.innerJoin(stream2, KeyValue::key,  KeyValue::key)
													.map(t -> Tuple.of(t._1.value(), t._2.value()))
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test1() throws Exception {
		FStream<KeyValue<Integer,String>> stream1 = FStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"),
																KeyValue.of(3, "C"));
		FStream<KeyValue<Integer,String>> stream2 = FStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"),
																KeyValue.of(3, "c"), KeyValue.of(1, "a2"));
		List<Tuple<String,String>> result = stream1.innerJoin(stream2, KeyValue::key,  KeyValue::key)
													.map(t -> Tuple.of(t._1.value(), t._2.value()))
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("C", "c"), Tuple.of("A", "a2"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test2() throws Exception {
		FStream<KeyValue<Integer,String>> stream1 = FStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"),
																KeyValue.of(3, "C"), KeyValue.of(1, "A2"));
		FStream<KeyValue<Integer,String>> stream2 = FStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"),
																KeyValue.of(3, "c"));
		List<Tuple<String,String>> result = stream1.innerJoin(stream2, KeyValue::key,  KeyValue::key)
													.map(t -> Tuple.of(t._1.value(), t._2.value()))
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A2", "a"), Tuple.of("C", "c"));
		Assert.assertEquals(answer, result);
	}
	
	@Test
	public void test3() throws Exception {
		FStream<KeyValue<Integer,String>> stream1 = FStream.of(KeyValue.of(1, "A"), KeyValue.of(2, "B"),
																KeyValue.of(3, "C"), KeyValue.of(1, "A2"));
		FStream<KeyValue<Integer,String>> stream2 = FStream.of(KeyValue.of(1, "a"), KeyValue.of(4, "d"),
																KeyValue.of(3, "c"), KeyValue.of(1, "a2"));
		List<Tuple<String,String>> result = stream1.innerJoin(stream2, KeyValue::key,  KeyValue::key)
													.map(t -> Tuple.of(t._1.value(), t._2.value()))
													.toList();
		var answer = List.of(Tuple.of("A", "a"), Tuple.of("A2", "a"), Tuple.of("C", "c"),
							Tuple.of("A", "a2"), Tuple.of("A2", "a2"));
		Assert.assertEquals(answer, result);
	}
}
