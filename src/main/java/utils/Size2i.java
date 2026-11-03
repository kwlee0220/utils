package utils;

import java.awt.Dimension;
import java.io.Serializable;
import java.util.Objects;

/**
 * 정수({@code int}) 폭과 높이로 이루어진 불변(immutable) 2차원 크기.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2i implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 폭.
	 */
	private final int m_width;
	/**
	 * 높이.
	 */
	private final int m_height;
	
	/**
	 * 주어진 폭과 높이로 크기 객체를 생성한다. 값의 범위는 검사하지 않는다(음수도 허용).
	 *
	 * @param width	폭.
	 * @param height	높이.
	 */
	public Size2i(int width, int height) {
		m_width = width;
		m_height = height;
	}
	
	/**
	 * 폭을(를) 반환한다.
	 *
	 * @return	폭.
	 */
	public int getWidth() {
		return m_width;
	}
	
	/**
	 * 높이을(를) 반환한다.
	 *
	 * @return	높이.
	 */
	public int getHeight() {
		return m_height;
	}
	
	/**
	 * 면적(폭 × 높이)을 반환한다.
	 * <b>주의:</b> {@code int} 곱셈이므로 결과가 {@code int} 범위를 넘으면 넘친(overflow) 값을 반환한다.
	 *
	 * @return	면적.
	 */
	public int getArea() {
		return m_width * m_height;
	}
	
	/**
	 * 폭을 x 좌표, 높이를 y 좌표로 하는 {@link Point2i}로 변환한다.
	 *
	 * @return	변환된 점.
	 */
	public Point2i toPoint2i() {
		return new Point2i(m_width, m_height);
	}
	
	/**
	 * 같은 폭과 높이를 갖는 {@link Dimension}을 새로 만들어 반환한다.
	 *
	 * @return	변환된 {@link Dimension}.
	 */
	public Dimension toDimension() {
		return new Dimension(m_width, m_height);
	}
	
	/**
	 * {@code "폭x높이"} 형식의 문자열(예: {@code "640x480"})을 파싱하여 크기 객체를 만든다.
	 * <p>
	 * 소문자 {@code 'x'}로 나눈 앞의 두 부분을 각각 {@link Integer#parseInt(String)}로 변환하며,
	 * 세 번째 이후 부분은 무시한다.
	 * {@link Integer#parseInt(String)}는 공백을 허용하지 않으므로 {@code "640 x 480"}처럼 공백이 있으면 실패한다.
	 *
	 * @param str	파싱할 문자열.
	 * @return	파싱된 크기 객체.
	 * @throws NumberFormatException	폭 또는 높이 부분이 숫자가 아닌 경우.
	 * @throws ArrayIndexOutOfBoundsException	문자열에 {@code 'x'}가 없는 경우.
	 * @throws NullPointerException	{@code str}이 {@code null}인 경우.
	 */
	public static Size2i fromString(String str) {
		String[] parts = str.split("x");
		
		return new Size2i(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
	}
	
	@Override
	public String toString() {
		return String.format("%dx%d", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2i other = (Size2i)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
