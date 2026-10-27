package utils.async.command;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 자식 프로세스 종료 시 재시작 여부를 결정하는 정책.
 * <p>
 * 설정(JSON)에서는 상수 이름({@code "ON_COMPLETED"})과 소문자 이름({@code "on-completed"}) 모두 허용하며,
 * 대소문자는 무시한다. 직렬화와 {@link #toString()}은 상수 이름을 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public enum RestartPolicy {
	/** 종료 상태와 무관하게 항상 재시작. */
	ALWAYS("always"),
	/** 성공적으로 종료한 경우에만 재시작. 실패 시 서비스는 FAILED로 전이. */
	ON_COMPLETED("on-completed"),
	/** 실패한 경우에만 재시작. 성공 종료 시 서비스는 TERMINATED로 전이. */
	ON_FAILED("on-failed"),
	/** 어떤 경우에도 재시작하지 않음. */
	NO("no");

	private final String m_name;

	private RestartPolicy(String name) {
		m_name = name;
	}

	/**
	 * 문자열 이름을 해당하는 정책으로 변환한다. JSON 역직렬화에도 사용된다.
	 * <p>
	 * 상수 이름(예: "ON_COMPLETED")과 소문자 이름(예: "on-completed")을 모두 허용하며, 대소문자는 무시한다.
	 *
	 * @param name 정책 이름.
	 * @return 매칭되는 {@link RestartPolicy}.
	 * @throws IllegalArgumentException 매칭되는 정책이 없는 경우.
	 */
	@JsonCreator
	public static RestartPolicy fromString(String name) {
		for ( RestartPolicy policy: values() ) {
			if ( policy.m_name.equalsIgnoreCase(name) || policy.name().equalsIgnoreCase(name) ) {
				return policy;
			}
		}

		throw new IllegalArgumentException("unknown restart policy: " + name);
	}
}