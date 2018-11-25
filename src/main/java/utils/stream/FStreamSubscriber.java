package utils.stream;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

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
			T data;
			while ( (data = m_fstrm.next()) != null ) {
				if ( emitter.isDisposed() ) {
					return;
				}
				emitter.onNext(data);
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
