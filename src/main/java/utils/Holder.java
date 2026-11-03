package utils;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 값 하나를 담아 두고 바꿀 수 있는 가변(mutable) 컨테이너.
 * <p>
 * 값은 {@code null}일 수 있다. 동기화하지 않으므로 여러 스레드에서 함께 쓰려면 호출자가 동기화해야 한다.
 * <p>
 * <b>주의:</b> 값 필드가 {@code transient}이므로 직렬화했다가 복원하면 값이 {@code null}이 된다.
 *
 * @param <T>	담는 값의 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class Holder<T> implements Serializable {
	private static final long serialVersionUID = -102726106391846640L;
	
	private transient T m_value;
	
	/**
	 * 주어진 값을 담은 {@link Holder}를 만든다.
	 *
	 * @param <T>	담는 값의 타입.
	 * @param value	초기 값. {@code null}을 허용한다.
	 * @return	새 {@link Holder} 객체.
	 */
	public static final <T> Holder<T> of(T value) {
		return new Holder<>(value);
	}
	
	private Holder(T value) {
		m_value = value;
	}
	
	/**
	 * 담긴 값을 반환한다.
	 *
	 * @return	담긴 값. {@code null}일 수 있다.
	 */
	public T get() {
		return m_value;
	}
	
	/**
	 * 담긴 값을 주어진 값으로 바꾼다.
	 *
	 * @param value	새 값. {@code null}을 허용한다.
	 * @return	이 {@link Holder} 객체.
	 */
	public Holder<T> set(T value) {
		m_value = value;
		return this;
	}
	
	/**
	 * 현재 값에 {@code func}를 적용한 결과로 담긴 값을 바꾼다.
	 *
	 * @param func	현재 값({@code null}일 수 있음)을 받아 새 값을 돌려주는 함수.
	 * @return	이 {@link Holder} 객체.
	 * @throws NullPointerException	{@code func}가 {@code null}인 경우.
	 */
	public Holder<T> update(Function<? super T,? extends T> func) {
		m_value = func.apply(m_value);
		return this;
	}
	
	@Override
	public String toString() {
		return (m_value != null)
					? String.format("Holder:{%s}", m_value)
					: "Holder:{}";
	}
}
