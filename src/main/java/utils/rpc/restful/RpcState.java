package utils.rpc.restful;


/**
 * 원격 연산(operation)의 수행 상태.
 * <p>
 * {@link RpcResponseMessage}에 담겨 클라이언트에 전달되며, 비동기 수행 중에는 {@code RUNNING},
 * 종료 시에는 {@code COMPLETED}/{@code FAILED}/{@code CANCELLED} 중 하나가 된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum RpcState {
	/** 연산이 수행 중이다. (아직 종료되지 않음) */
	RUNNING,
	/** 연산이 성공적으로 완료되었다. (출력이 함께 제공됨) */
	COMPLETED,
	/** 연산이 실패로 종료되었다. (실패 정보가 함께 제공됨) */
	FAILED,
	/** 연산이 취소되어 종료되었다. */
	CANCELLED,
}
