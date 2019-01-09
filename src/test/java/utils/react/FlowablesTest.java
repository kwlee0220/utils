package utils.react;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import utils.stream.FStream;
import utils.stream.IntFStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
@RunWith(MockitoJUnitRunner.class)
public class FlowablesTest {
	private final Exception m_cause = new Exception();

	@Mock io.reactivex.functions.Consumer<Integer> m_onNextHandler;
	@Mock io.reactivex.functions.Consumer<Throwable> m_onErrorHandler;
	@Mock Action m_onCompleteHandler;
	
	@Before
	public void setup() {
	}
	
	@Test
	public void test01() throws Exception {
		IntFStream strm = FStream.range(0, 10);
		
		InOrder order = Mockito.inOrder(m_onNextHandler);
		Flowables.from(strm)
				.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);

		for ( int i =0; i < 10; ++i ) {
			order.verify(m_onNextHandler).accept(i);
		}
		verify(m_onCompleteHandler, times(1)).run();
	}
	
	@Test
	public void test02() throws Exception {
		FStream<Integer> strm = FStream.empty();
		
		Flowables.from(strm)
				.subscribe(m_onNextHandler, m_onErrorHandler, m_onCompleteHandler);
		verify(m_onNextHandler, never()).accept(any());
		verify(m_onCompleteHandler, times(1)).run();
	}
	
	@Test
	public void test03() throws Exception {
		FStream<Integer> strm = FStream.range(0, 10_000_000);
		
		Flowables.from(strm)
				.subscribeOn(Schedulers.io())
				.subscribe(v -> {
					TimeUnit.MILLISECONDS.sleep(100);
					m_onNextHandler.accept(v);
				}, m_onErrorHandler, m_onCompleteHandler);
		TimeUnit.MILLISECONDS.sleep(1000 + 50);
		strm.close();
		TimeUnit.MILLISECONDS.sleep(150);
		
		verify(m_onNextHandler, times(11)).accept(any());
		verify(m_onCompleteHandler, times(1)).run();
	}
}
