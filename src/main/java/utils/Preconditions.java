package utils;

import com.google.common.base.Strings;


/**
 * 메소드 인자/객체 상태 검증을 위한 정적 유틸리티 모음.
 * <p>
 * Guava의 {@code com.google.common.base.Preconditions} 대신 본 라이브러리 안에서 일관된 검증
 * 진입점으로 사용된다. 메소드는 검증 실패 시 던지는 예외 종류와 검증 대상에 따라 다음과 같이 구분된다.
 *
 * <table>
 *   <caption>메소드 분류</caption>
 *   <tr><th>메소드</th><th>실패 시 예외</th><th>의미</th></tr>
 *   <tr><td>{@link #checkArgument}</td>             <td>{@link IllegalArgumentException}</td><td>일반 인자 조건 검증</td></tr>
 *   <tr><td>{@link #checkNotNullArgument}</td>      <td>{@link IllegalArgumentException}</td><td>인자 null 검증</td></tr>
 *   <tr><td>{@link #checkNotNull}</td>              <td>{@link NullPointerException}</td>    <td>객체/필드 null 검증</td></tr>
 *   <tr><td>{@link #checkState}</td>                <td>{@link IllegalStateException}</td>   <td>객체 상태(필드/내부 상태) 검증</td></tr>
 * </table>
 *
 * <h3>메시지 형태</h3>
 * <ul>
 *   <li>단순 {@code String} — 메시지를 그대로 사용.</li>
 *   <li>{@code (msgTemplate, Object... msgArgs)} — {@link Strings#lenientFormat}으로 포맷팅.
 *       {@code %s} 자리표시자만 지원하며 args 개수 mismatch도 graceful 하게 처리한다.</li>
 * </ul>
 *
 * <h3>{@code checkNotNullArgument} vs {@code checkNotNull} 차이</h3>
 * <ul>
 *   <li>{@code checkNotNullArgument} — <b>메소드 인자</b>가 null인지 검사. 실패 시 {@code IllegalArgumentException}.
 *       반환값 없음.</li>
 *   <li>{@code checkNotNull} — 임의 객체(필드 값, 반환값 등)가 null인지 검사. 실패 시 {@code NullPointerException}.
 *       검증된 객체를 그대로 반환하므로 {@code Object o = checkNotNull(provider.get(), "...");} 같은
 *       inline 사용이 가능하다.</li>
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Preconditions {
	private Preconditions() {
		throw new AssertionError("Should not be invoked!!: class=" + Preconditions.class.getName());
	}

	/**
	 * 메소드 인자가 {@code null}이 아닌지 검증한다.
	 *
	 * @param obj	검증 대상 객체.
	 * @param msg	{@code obj}가 {@code null}일 때 예외에 사용할 메시지.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}인 경우.
	 */
	public static void checkNotNullArgument(Object obj, String msg) {
		if ( obj == null ) {
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * 메소드 인자가 {@code null}이 아닌지 검증한다.
	 * 메시지는 {@link Strings#lenientFormat}으로 포맷팅된다 ({@code %s} 자리표시자).
	 *
	 * @param obj			검증 대상 객체.
	 * @param msgTemplate	메시지 템플릿. {@code %s} 자리표시자에 {@code msgArgs}가 대입된다.
	 * @param msgArgs		템플릿에 대입할 값들.
	 * @throws IllegalArgumentException	{@code obj}가 {@code null}인 경우.
	 */
	public static void checkNotNullArgument(Object obj, String msgTemplate, Object... msgArgs) {
		if ( obj == null ) {
			throw new IllegalArgumentException(Strings.lenientFormat(msgTemplate, msgArgs));
		}
	}

	/**
	 * 메소드 인자 중 {@code null}이 아닌지 검증한다.
	 *
	 * @param obj	검증 대상 객체들.
	 * @param msg	{@code obj} 중 하나라도 {@code null}일 때 예외에 사용할 메시지.
	 * @throws IllegalArgumentException	{@code obj} 중 하나라도 {@code null}인 경우.
	 */
	public static void checkNotNullIterableArgument(Iterable<?> obj, String msgTemplate, Object... msgArgs) {
		if ( obj == null ) {
			throw new IllegalArgumentException(Strings.lenientFormat(msgTemplate, msgArgs));
		}
		for ( Object o: obj ) {
			if ( o == null ) {
				throw new IllegalArgumentException(Strings.lenientFormat(msgTemplate, msgArgs));
			}
		}
	}

	/**
	 * 인자 조건 {@code pred}가 {@code true}인지 검증한다.
	 *
	 * @param pred	검증할 조건. {@code true}여야 한다.
	 * @param msg	{@code pred}가 {@code false}일 때 예외에 사용할 메시지.
	 * @throws IllegalArgumentException	{@code pred}가 {@code false}인 경우.
	 */
	public static void checkArgument(boolean pred, String msg) {
		if ( !pred ) {
			throw new IllegalArgumentException(msg);
		}
	}

	/**
	 * 인자 조건 {@code pred}가 {@code true}인지 검증한다.
	 * 메시지는 {@link Strings#lenientFormat}으로 포맷팅된다.
	 *
	 * @param pred			검증할 조건.
	 * @param msgTemplate	메시지 템플릿 ({@code %s} 자리표시자).
	 * @param msgArgs		템플릿에 대입할 값들.
	 * @throws IllegalArgumentException	{@code pred}가 {@code false}인 경우.
	 */
	public static void checkArgument(boolean pred, String msgTemplate, Object... msgArgs) {
		if ( !pred ) {
			throw new IllegalArgumentException(Strings.lenientFormat(msgTemplate, msgArgs));
		}
	}

	/**
	 * 객체가 {@code null}이 아닌지 검증하고, null이 아니면 그대로 반환한다.
	 *
	 * @param <T>	검증 대상 타입.
	 * @param obj	검증 대상 객체.
	 * @param msg	{@code obj}가 {@code null}일 때 예외에 사용할 메시지.
	 * @return	{@code obj} 그대로 (null 아님이 보장됨).
	 * @throws NullPointerException	{@code obj}가 {@code null}인 경우.
	 */
	public static <T> T checkNotNull(T obj, String msg) {
		if ( obj == null ) {
			throw new NullPointerException(msg);
		}
		return obj;
	}

	/**
	 * 객체가 {@code null}이 아닌지 검증하고, null이 아니면 그대로 반환한다.
	 * 메시지는 {@link Strings#lenientFormat}으로 포맷팅된다.
	 *
	 * @param <T>			검증 대상 타입.
	 * @param obj			검증 대상 객체.
	 * @param msgTemplate	메시지 템플릿 ({@code %s} 자리표시자).
	 * @param msgArgs		템플릿에 대입할 값들.
	 * @return	{@code obj} 그대로 (null 아님이 보장됨).
	 * @throws NullPointerException	{@code obj}가 {@code null}인 경우.
	 */
	public static <T> T checkNotNull(T obj, String msgTemplate, Object... msgArgs) {
		if ( obj == null ) {
			throw new NullPointerException(Strings.lenientFormat(msgTemplate, msgArgs));
		}
		return obj;
	}

	/**
	 * 객체 상태 조건 {@code pred}가 {@code true}인지 검증한다. 메시지 없이 예외를 던진다.
	 *
	 * @param pred	검증할 상태 조건.
	 * @throws IllegalStateException	{@code pred}가 {@code false}인 경우.
	 */
	public static void checkState(boolean pred) {
		if ( !pred ) {
			throw new IllegalStateException();
		}
	}

	/**
	 * 객체 상태 조건 {@code pred}가 {@code true}인지 검증한다.
	 *
	 * @param pred	검증할 상태 조건.
	 * @param msg	{@code pred}가 {@code false}일 때 예외에 사용할 메시지.
	 * @throws IllegalStateException	{@code pred}가 {@code false}인 경우.
	 */
	public static void checkState(boolean pred, String msg) {
		if ( !pred ) {
			throw new IllegalStateException(msg);
		}
	}

	/**
	 * 객체 상태 조건 {@code pred}가 {@code true}인지 검증한다.
	 * 메시지는 {@link Strings#lenientFormat}으로 포맷팅된다.
	 *
	 * @param pred			검증할 상태 조건.
	 * @param msgTemplate	메시지 템플릿 ({@code %s} 자리표시자).
	 * @param msgArgs		템플릿에 대입할 값들.
	 * @throws IllegalStateException	{@code pred}가 {@code false}인 경우.
	 */
	public static void checkState(boolean pred, String msgTemplate, Object... msgArgs) {
		if ( !pred ) {
			throw new IllegalStateException(Strings.lenientFormat(msgTemplate, msgArgs));
		}
	}
}
