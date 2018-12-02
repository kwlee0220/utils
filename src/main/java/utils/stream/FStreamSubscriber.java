package utils.stream;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamSubscriber<T> implements ObservableOnSubscribe<T> {
	private final FStream<T> m_fstrm;
	
	FStreamSubscriber(FStream<T> fstrm) {
		m_fstrm = fstrm;
	}

	@Override
	public void subscribe(ObservableEmitter<T> emitter) throws Exception {
		try {
			Option<T> data;
			while ( (data = m_fstrm.next()).isDefined() ) {
				if ( emitter.isDisposed() ) {
					return;
				}
				emitter.onNext(data.get());
			}
			
			if ( emitter.isDisposed() ) {
				return;
			}
			emitter.onComplete();
		}
		catch ( Throwable e ) {
			emitter.onError(e);
		}
	}

}
