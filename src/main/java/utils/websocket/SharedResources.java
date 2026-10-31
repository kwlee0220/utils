package utils.websocket;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 패키지의 차트·Observable이 함께 쓰는 자원. 각 자원은 처음 쓸 때 만든다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
final class SharedResources {
	private SharedResources() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}

	/** 클라이언트를 따로 지정하지 않은 차트·Observable이 함께 쓰는 {@link HttpClient}. */
	private static final class SharedHttpClient {
		static final HttpClient INSTANCE = HttpClient.newHttpClient();
	}

	/**
	 * 접속 결과 콜백을 실행하는 스레드 풀. JDK는 접속 future를 공용 풀({@code ForkJoinPool.commonPool()})에서
	 * 완료시키는데, 콜백이 차트의 신호 처리(사용자 상태 코드)까지 실행하므로 공용 풀을 막지 않도록 따로 둔다.
	 * 쉬는 스레드는 60초 뒤 사라진다(cached thread pool). 데몬 스레드라 JVM 종료를 막지 않는다.
	 */
	private static final class CallbackExecutor {
		private static final AtomicInteger SEQNO = new AtomicInteger();
		static final ExecutorService INSTANCE = Executors.newCachedThreadPool(task -> {
			Thread thread = new Thread(task, "websocket-callback-" + SEQNO.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * keep-alive의 ping 송신과 pong 감시를 예약하는 스케줄러(데몬 스레드 하나, {@code websocket-scheduler}).
	 * 예약 작업은 짧게 끝나야 한다. 오래 걸릴 수 있는 일(차트의 신호 처리)은 {@link #callbackExecutor()}로 넘긴다.
	 * 취소한 작업은 바로 큐에서 지운다(차트가 많이 끝나도 취소된 감시 작업이 쌓이지 않게).
	 */
	private static final class Scheduler {
		static final ScheduledExecutorService INSTANCE;
		static {
			ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
				Thread thread = new Thread(task, "websocket-scheduler");
				thread.setDaemon(true);
				return thread;
			});
			executor.setRemoveOnCancelPolicy(true);
			INSTANCE = executor;
		}
	}

	static HttpClient httpClient() {
		return SharedHttpClient.INSTANCE;
	}

	static Executor callbackExecutor() {
		return CallbackExecutor.INSTANCE;
	}

	static ScheduledExecutorService scheduler() {
		return Scheduler.INSTANCE;
	}
}
