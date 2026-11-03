package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 실수({@code float}) 폭과 높이로 이루어진 불변(immutable) 2차원 크기.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2f implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 폭.
	 */
	private final float m_width;
	/**
	 * 높이.
	 */
	private final float m_height;
	
	/**
	 * 주어진 폭과 높이로 크기 객체를 생성한다. 값의 범위는 검사하지 않는다(음수도 허용).
	 *
	 * @param width	폭.
	 * @param height	높이.
	 */
	public Size2f(float width, float height) {
		m_width = width;
		m_height = height;
	}
	
	/**
	 * 폭을(를) 반환한다.
	 *
	 * @return	폭.
	 */
	public float getWidth() {
		return m_width;
	}
	
	/**
	 * 높이을(를) 반환한다.
	 *
	 * @return	높이.
	 */
	public float getHeight() {
		return m_height;
	}
	
	/**
	 * 폭과 높이를 각각 주어진 크기의 폭과 높이로 나눈 새 크기를 반환한다.
	 * 0으로 나누면 부동소수점 규칙에 따라 결과가 무한대 또는 {@code NaN}이 된다.
	 *
	 * @param size	나눌 크기.
	 * @return	나눈 결과 크기.
	 * @throws NullPointerException	{@code size}가 {@code null}인 경우.
	 */
	public Size2f divideBy(Size2f size) {
		return new Size2f(m_width / size.m_width, m_height / size.m_height);
	}
	
	/**
	 * 폭과 높이를 각각 주어진 크기의 폭과 높이로 나눈 새 크기를 반환한다.
	 * 0으로 나누면 부동소수점 규칙에 따라 결과가 무한대 또는 {@code NaN}이 된다.
	 *
	 * @param size	나눌 크기.
	 * @return	나눈 결과 크기.
	 * @throws NullPointerException	{@code size}가 {@code null}인 경우.
	 */
	public Size2f divideBy(Size2i size) {
		return new Size2f(m_width / size.getWidth(), m_height / size.getHeight());
	}
	
	/**
	 * 폭과 높이를 각각 {@link Math#floor(double)}로 내림한 정수 크기를 반환한다.
	 * {@code int} 범위를 벗어난 값은 {@code int} 변환 규칙에 따라 {@link Integer#MAX_VALUE} 또는
	 * {@link Integer#MIN_VALUE}가 되고, {@code NaN}은 0이 된다.
	 *
	 * @return	내림한 정수 크기.
	 */
	public Size2i floor() {
		return new Size2i((int)Math.floor(m_width), (int)Math.floor(m_height));
	}
	
	/**
	 * {@code "폭x높이"} 형식의 문자열(예: {@code "1.5x2.0"})을 파싱하여 크기 객체를 만든다.
	 * <p>
	 * 소문자 {@code 'x'}로 나눈 앞의 두 부분을 각각 {@link Float#parseFloat(String)}로 변환하며,
	 * 세 번째 이후 부분은 무시한다.
	 *
	 * @param str	파싱할 문자열.
	 * @return	파싱된 크기 객체.
	 * @throws NumberFormatException	폭 또는 높이 부분이 숫자가 아닌 경우.
	 * @throws ArrayIndexOutOfBoundsException	문자열에 {@code 'x'}가 없는 경우.
	 * @throws NullPointerException	{@code str}이 {@code null}인 경우.
	 */
	public static Size2f fromString(String str) {
		String[] parts = str.split("x");
		
		return new Size2f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%fx%f", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2f other = (Size2f)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
