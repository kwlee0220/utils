package utils.websocket;

import java.net.http.WebSocket;

import org.jetbrains.annotations.Nullable;


/**
 * 상대가 비정상 상태 코드로 WebSocket 연결을 닫았음을 나타내는 예외.
 * <p>
 * 처리되지 않은 {@link Signals.ConnectionClosed}의 상태 코드가 정상 종료로 보는 코드({@link #isNormalClosure(int)})가
 * 아니면, {@link WebSocketStateChart}는 이 예외를 원인으로 차트를 실패시키고, {@link WebSocketObservable}은 이 예외로
 * {@code onError}를 호출한다. 서버 장애(예: 1011)로 끊긴 것과 정상 종료·취소를 호출자가 구분할 수 있게 하기 위해서다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class WebSocketClosedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/** 상대가 상태 코드 없이 close 프레임을 보냈을 때 JDK가 전달하는 코드(RFC 6455의 1005). */
	public static final int NO_STATUS_CODE = 1005;
	/** 상대가 떠나는 중(서버 종료, 페이지 이동 등)이라 닫은 경우의 코드(RFC 6455의 1001). */
	public static final int GOING_AWAY = 1001;

	private final int m_statusCode;
	private final @Nullable String m_reason;

	/**
	 * 예외를 생성한다.
	 *
	 * @param statusCode	상대가 보낸 close 상태 코드.
	 * @param reason		상대가 보낸 close 사유. 없으면 {@code null}이나 빈 문자열.
	 */
	public WebSocketClosedException(int statusCode, @Nullable String reason) {
		super(String.format("WebSocket closed abnormally: status=%d, reason=%s", statusCode, reason));

		m_statusCode = statusCode;
		m_reason = reason;
	}

	/**
	 * 상대가 보낸 close 상태 코드를 반환한다.
	 *
	 * @return	상태 코드.
	 */
	public int getStatusCode() {
		return m_statusCode;
	}

	/**
	 * 상대가 보낸 close 사유를 반환한다.
	 *
	 * @return	close 사유. 없으면 {@code null}이나 빈 문자열.
	 */
	public @Nullable String getReason() {
		return m_reason;
	}

	/**
	 * 주어진 close 상태 코드를 정상 종료로 볼지 여부를 반환한다.
	 * <p>
	 * {@link WebSocket#NORMAL_CLOSURE}(1000), {@link #GOING_AWAY}(1001), {@link #NO_STATUS_CODE}(1005)를 정상 종료로 본다.
	 * 1005는 상대가 상태 코드 없이 close 프레임을 보낸 경우라 오류 신호로 보지 않는다. 그 밖의 코드(예: 1006 비정상 종료,
	 * 1011 서버 오류)는 비정상 종료다.
	 *
	 * @param statusCode	close 상태 코드.
	 * @return	정상 종료로 보면 {@code true}.
	 */
	public static boolean isNormalClosure(int statusCode) {
		return statusCode == WebSocket.NORMAL_CLOSURE || statusCode == GOING_AWAY || statusCode == NO_STATUS_CODE;
	}
}
