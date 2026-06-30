package utils.rpc.restful;

import utils.async.StartableExecution;
import utils.io.IOUtils;


/**
 * {@link RESTfulAsyncRpcServer}가 진행 중인 비동기 연산 하나를 식별·추적하기 위해 사용하는 세션.
 * <p>
 * 연산이 {@code RUNNING}에 도달하는 시점에 {@link RESTfulAsyncRpcServer#allocateSession}이 발급하며,
 * 세션 엔드포인트({@link #getSessionEndpoint()})를 키로 서버 내부 맵에 보관된다. 이후 상태 조회·취소 요청은
 * 이 엔드포인트로 세션을 찾아 {@link #getExecution() 연산}에 위임된다.
 * <p>
 * {@link AutoCloseable}을 구현하며, {@link #close()} 호출 시 추적 중인 연산을 조용히
 * close({@link utils.io.IOUtils#closeQuietly})한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class AsyncRpcSession implements AutoCloseable {
	private final String m_sessionEndpoint;
	private final StartableExecution<?> m_execution;

	/**
	 * 세션을 생성한다.
	 *
	 * @param sessionEndpoint	세션을 식별하는 엔드포인트.
	 * @param execution			이 세션이 추적할 연산.
	 */
	public AsyncRpcSession(String sessionEndpoint, StartableExecution<?> execution) {
		m_execution = execution;
		m_sessionEndpoint = sessionEndpoint;
	}

	/**
	 * 이 세션이 추적하는 연산을 반환한다.
	 *
	 * @return	연산 실행 객체.
	 */
	public StartableExecution<?> getExecution() {
		return m_execution;
	}

	/**
	 * 이 세션을 식별하는 엔드포인트를 반환한다.
	 *
	 * @return	세션 엔드포인트.
	 */
	public String getSessionEndpoint() {
		return m_sessionEndpoint;
	}

	/**
	 * 세션을 닫으며 추적 중인 연산을 조용히 close한다.
	 */
	@Override
	public void close() {
		IOUtils.closeQuietly(m_execution);
	}
}