package utils.react;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Flowable;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Flowables {
	static final Logger s_logger = LoggerFactory.getLogger(Flowables.class);
	
	private Flowables() {
		throw new AssertionError("Should not be called: class=" + Flowables.class);
	}
	
	public static <T> Flowable<T> from(FStream<T> stream) {
		return Flowable.generate(
					()->stream,
					(s,emitter)->{
						try {
							s.next()
								.ifPresent(emitter::onNext)
								.ifAbsent(emitter::onComplete);
						}
						catch ( Exception e ) {
							emitter.onError(e);
						}
					},
					s -> s.closeQuietly());
	}
}
