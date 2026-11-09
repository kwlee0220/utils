package utils.rpc.restful;

/**
 * 연산 호출의 응답 상태가 호출 방식과 맞지 않을 때 발생하는 예외.
 * <p>
 * 동기 호출({@link RESTfulRpcClient#call()})이 {@code RUNNING} 응답을 받은 경우가 대표적이다. 이때 원격 연산은
 * 이미 시작되었으므로, 호출자는 {@link #getResponse()}의 {@code session_endpoint}로 세션에 붙어 결과를 기다리거나
 * ({@link RESTfulAsyncRpcClient.Builder#setSessionEndpoint(String)}) 취소할 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UnexpectedRpcStateException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	private final transient RpcResponseMessage m_response;

	public UnexpectedRpcStateException(String message, RpcResponseMessage response) {
		super(message);
		m_response = response;
	}

	/**
	 * 받은 응답 메시지를 반환한다.
	 *
	 * @return	응답 메시지.
	 */
	public RpcResponseMessage getResponse() {
		return m_response;
	}
}
