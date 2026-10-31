package utils.statechart;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 상태 위치 경로를 다루는 정적 유틸리티.
 * <p>
 * 상태의 부모·자식 관계는 경로로 정해진다. 경로는 {@code '/'}로 구분한 구간(segment)들이며, 마지막
 * {@code '/'} 앞부분이 부모 경로다. 예를 들어 {@code "/Running/Idle"}의 부모는 {@code "/Running"}이다.
 * 앞의 {@code '/'}는 선택이다. {@code "/Running"}과 {@code "running"}은 모두 최상위 상태이며, 둘은 서로 다른 경로다.
 * <p>
 * 올바른 경로의 조건은 다음과 같다. {@link StateChart#addState(State)}가 이 조건을 검사한다.
 * <ul>
 *   <li>비어 있지 않다.</li>
 *   <li>{@code '/'}로 끝나지 않는다({@code "/"} 단독도 안 된다).</li>
 *   <li>빈 구간이 없다({@code "//"}가 없다).</li>
 * </ul>
 * 차트와 {@link AbstractState}는 경로를 해석할 때 모두 이 클래스를 사용한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class StatePath {
	private StatePath() {
		throw new AssertionError("This class should not be instantiated");
	}

	/**
	 * 주어진 경로가 올바른지 검사한다.
	 *
	 * @param path	검사할 경로.
	 * @throws IllegalArgumentException	{@code path}가 {@code null}이거나 올바른 경로가 아닌 경우.
	 * 									메시지에 이유가 담긴다.
	 */
	public static void checkValid(String path) {
		String violation = findViolation(path);
		if ( violation != null ) {
			throw new IllegalArgumentException(violation);
		}
	}

	/**
	 * 주어진 경로가 올바른지 여부를 반환한다.
	 *
	 * @param path	검사할 경로.
	 * @return	올바른 경로이면 {@code true}. {@code null}이면 {@code false}.
	 */
	public static boolean isValid(@Nullable String path) {
		return findViolation(path) == null;
	}

	/**
	 * 경로가 어긴 규칙을 찾는다. {@link #checkValid(String)}와 {@link #isValid(String)}가 같은 규칙을 쓰도록
	 * 규칙은 이곳에만 둔다.
	 *
	 * @return	어긴 규칙을 설명하는 메시지. 올바른 경로이면 {@code null}.
	 */
	private static @Nullable String findViolation(@Nullable String path) {
		if ( path == null ) {
			return "state path is null";
		}
		if ( path.isEmpty() ) {
			return "state path is empty";
		}
		if ( path.endsWith("/") ) {
			return String.format("state path should not end with '/': '%s'", path);
		}
		if ( path.contains("//") ) {
			return String.format("state path has an empty segment: '%s'", path);
		}
		return null;
	}

	/**
	 * 주어진 경로의 부모 경로를 반환한다. 마지막 {@code '/'} 앞부분이 부모 경로다.
	 *
	 * @param path	상태 경로 (non-null).
	 * @return	부모 경로. 최상위 상태({@code "/A"}, {@code "A"})이면 {@code null}.
	 */
	public static @Nullable String parentOf(String path) {
		Preconditions.checkNotNullArgument(path, "state path is null");

		int idx = path.lastIndexOf('/');
		return (idx > 0) ? path.substring(0, idx) : null;
	}

	/**
	 * 주어진 경로를 {@code '/'} 기준으로 나눈 구간 목록을 반환한다. 빈 구간(앞의 {@code '/'}로 생기는 것 등)은
	 * 포함하지 않는다. 예를 들어 {@code "/Running/Idle"}은 {@code ["Running", "Idle"]}이다.
	 *
	 * @param path	상태 경로 (non-null).
	 * @return	구간 목록 (수정 불가).
	 */
	public static List<String> segments(String path) {
		Preconditions.checkNotNullArgument(path, "state path is null");

		return Arrays.stream(path.split("/")).filter(seg -> !seg.isEmpty()).toList();
	}
}
