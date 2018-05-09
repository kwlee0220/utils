package utils.stream;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import utils.func.FOptional;

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
			FOptional<T> odata;
			while ( (odata = m_fstrm.next()).isPresent() ) {
				if ( emitter.isDisposed() ) {
					return;
				}
				emitter.onNext(odata.get());
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
