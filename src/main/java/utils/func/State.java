package utils.func;

import java.util.function.Function;

import io.vavr.Tuple;
import io.vavr.Tuple2;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class State<S,T> {
	private final Function<S,Tuple2<T,S>> m_run;
	
	public State(Function<S,Tuple2<T,S>> run) {
		m_run = run;
	}
	
	public static <S,T> State<S,T> unit(T t) {
		return new State<>(s -> Tuple.of(t,s));
	}
	
	public Tuple2<T,S> apply(S s) {
		return m_run.apply(s);
	}
	
	public <T2> State<S,T2> map(Function<T,T2> valueMapper) {
		return flatMap(t -> unit(valueMapper.apply(t)));
	}
	
	public <T2> State<S,T2> flatMap(Function<T,State<S,T2>> valueMapper) {
		return new State<>(s -> {
			Tuple2<T,S> tuple = m_run.apply(s);
			return valueMapper.apply(tuple._1).m_run.apply(tuple._2);
		});
	}
}
