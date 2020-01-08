package utils.stream;

import java.util.concurrent.TimeUnit;

import utils.func.FOption;
import utils.func.Try;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface TimedFStream<T> extends FStream<T> {
	public FOption<Try<T>> next(long timeout, TimeUnit tu);
}
