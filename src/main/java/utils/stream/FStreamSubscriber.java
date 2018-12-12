package utils.stream;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import utils.func.FOption;

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
			FOption<T> data;
			while ( (data = m_fstrm.next()).isPresent() ) {
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
