package utils.async;


/**
 * 연산 수행 인터페이스를 정의한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public enum AsyncState {
	/** 연산 시작 이전 상태 */
	NOT_STARTED(0),
	/** 연산이 동작을 위해 초기화 중인 상태 */
	STARTING(1),
	/** 연산이 동작 중인 상태 */
	RUNNING(2),
	/** 연산 수행이 성공적으로 종료된 상태. */
	COMPLETED(3),
	/** 연산 수행 중 오류 발생으로 종료된 상태. */
	FAILED(4),
	/** 연산 수행 중단이 요청되어 중단 중인 상태. */
	CANCELLING(5),
	/** 연산 수행 중간에 강제로 중단된 상태. */
	CANCELLED(6);
	
	int m_code;
	
	AsyncState(int code) {
		m_code = code;
	}
	
	int getCode() {
		return m_code;
	}
}