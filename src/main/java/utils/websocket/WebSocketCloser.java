package utils.websocket;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;


/**
 * WebSocket 연결을 닫는 공통 절차.
 * <p>
 * {@link WebSocketStateChart}, {@link States.WebSocketConnectingState}, {@link WebSocketObservable}이 함께 쓴다.
 * 어느 경로로 닫든 다음을 보장한다.
 * <ol>
 *   <li>close 프레임을 보낸다. 보내지 못하면(출력이 이미 닫힌 경우는 제외) 바로 {@link WebSocket#abort()}한다.</li>
 *   <li>상대의 close 응답({@code peerClosed})을 기다린다. 기다릴 응답이 없으면({@code null}) 전송 직후 {@code abort()}한다.</li>
 *   <li>전송과 응답 대기 전체가 {@code timeout} 안에 끝나지 않으면 {@code abort()}한다.</li>
 * </ol>
 * {@link WebSocket#sendClose(int, String)}는 출력만 닫고 입력은 상대가 close로 응답할 때까지 열어 둔다. 상대가
 * 응답하지 않으면 연결이 남으므로, 마지막에는 항상 {@code abort()}로 정리되게 한다. 어떤 실패도 예외로 던지지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
final class WebSocketCloser {
	private WebSocketCloser() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}

	/**
	 * close 진행 상황.
	 *
	 * @param frameSent	close 프레임 전송이 끝나면(실패, 시간 초과, abort 포함) 정상 완료된다.
	 * @param closed	close 절차 전체가 끝나면(상대의 응답 수신 또는 abort) 정상 완료된다.
	 */
	record Closing(CompletableFuture<Void> frameSent, CompletableFuture<Void> closed) {
		/** 닫을 연결이 없을 때의 이미 끝난 진행 상황. */
		static Closing done() {
			return new Closing(CompletableFuture.completedFuture(null), CompletableFuture.completedFuture(null));
		}
	}

	/**
	 * 연결을 닫는다. 호출은 기다리지 않고 바로 반환한다.
	 *
	 * @param ws			닫을 연결
	 * @param statusCode	close 상태 코드
	 * @param reason		close 사유
	 * @param timeout		전송과 응답 대기 전체의 시간 제한
	 * @param peerClosed	상대의 close 응답(또는 연결 오류)을 받으면 완료되는 것. {@code null}이면 응답을 기다리지 않고
	 * 						전송 직후 {@code abort()}한다(수신을 요청하지 않은 연결처럼 응답을 받을 수 없는 경우).
	 * @param logger		로거
	 * @return	진행 상황
	 */
	static Closing close(WebSocket ws, int statusCode, String reason, Duration timeout,
						@Nullable CompletionStage<?> peerClosed, Logger logger) {
		CompletableFuture<?> send;
		try {
			send = ws.sendClose(statusCode, reason);
		}
		catch ( Throwable e ) {
			send = CompletableFuture.failedFuture(e);
		}

		CompletableFuture<Void> frameSent = new CompletableFuture<>();
		CompletableFuture<Void> closed = new CompletableFuture<>();
		send.whenComplete((r, e) -> {
			frameSent.complete(null);
			// 출력이 이미 닫혔으면(먼저 보낸 close가 있음) 실패가 아니라 이미 보낸 것으로 보고 응답을 기다린다.
			if ( e != null && !ws.isOutputClosed() ) {
				logger.debug("failed to send WebSocket close frame; aborting: {}", ""+e);
				abortQuietly(ws, logger);
				closed.complete(null);
			}
			else if ( peerClosed == null ) {
				abortQuietly(ws, logger);
				closed.complete(null);
			}
			else {
				peerClosed.whenComplete((r2, e2) -> closed.complete(null));
			}
		});

		// 전송과 응답 대기 전체의 시간 제한. 넘으면 abort하고 두 future를 모두 끝낸다.
		closed.copy()
				.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
				.whenComplete((r, e) -> {
					if ( e instanceof TimeoutException ) {
						logger.warn("closing WebSocket did not finish within {}; aborting the connection", timeout);
						abortQuietly(ws, logger);
						frameSent.complete(null);
						closed.complete(null);
					}
				});
		return new Closing(frameSent, closed);
	}

	private static void abortQuietly(WebSocket ws, Logger logger) {
		try {
			ws.abort();
		}
		catch ( Throwable e ) {
			logger.debug("ignored error while aborting WebSocket: {}", ""+e);
		}
	}
}
