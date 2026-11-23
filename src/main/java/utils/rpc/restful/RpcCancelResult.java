package utils.rpc.restful;

import utils.Preconditions;


/**
 * {@link RESTfulAsyncRpcServer#cancel(String)}의 결과.
 * <p>
 * 응답 메시지의 상태만으로는 "취소를 받아들였지만 아직 멈추는 중"과 "취소를 거부해 계속 실행 중"을 구분할 수 없으므로,
 * 취소 접수 여부를 따로 담는다. HTTP 계층은 다음과 같이 상태 코드를 정한다.
 * <ul>
 *   <li>{@code accepted}이고 {@code CANCELLED}: {@code 200}</li>
 *   <li>{@code accepted}이고 {@code RUNNING}(멈추는 중): {@code 202}</li>
 *   <li>그 밖의 경우(취소 거부, 이미 끝남, 취소를 받았지만 그 전에 완료·실패로 끝남): {@code 409}</li>
 * </ul>
 *
 * @param response	취소 처리 후의 세션 상태를 담은 응답 메시지.
 * @param accepted	이번 요청이나 앞선 요청의 취소가 받아들여졌는지 여부.
 * @author Kang-Woo Lee (ETRI)
 */
public record RpcCancelResult(RpcResponseMessage response, boolean accepted) {
	public RpcCancelResult {
		Preconditions.checkNotNullArgument(response, "response is null");
	}

	/**
	 * 취소가 받아들여졌고 연산이 취소로 끝났는지 여부.
	 *
	 * @return	취소로 끝났으면 {@code true}.
	 */
	public boolean isCancelled() {
		return accepted && response.getState() == RpcState.CANCELLED;
	}

	/**
	 * 취소가 받아들여졌고 연산이 아직 멈추는 중인지 여부.
	 *
	 * @return	멈추는 중이면 {@code true}.
	 */
	public boolean isStopping() {
		return accepted && response.getState() == RpcState.RUNNING;
	}
}
