package utils;

import java.io.Serializable;
import java.util.Objects;

/**
 * 실수({@code double}) 폭과 높이로 이루어진 불변(immutable) 2차원 크기.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Size2d implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * 폭.
	 */
	private final double m_width;
	/**
	 * 높이.
	 */
	private final double m_height;
	
	/**
	 * 주어진 폭과 높이로 크기 객체를 생성한다. 값의 범위는 검사하지 않는다(음수도 허용).
	 *
	 * @param width	폭.
	 * @param height	높이.
	 */
	public Size2d(double width, double height) {
		m_width = width;
		m_height = height;
	}
	
	/**
	 * 폭을(를) 반환한다.
	 *
	 * @return	폭.
	 */
	public double getWidth() {
		return m_width;
	}
	
	/**
	 * 높이을(를) 반환한다.
	 *
	 * @return	높이.
	 */
	public double getHeight() {
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
	public Size2d divideBy(Size2d size) {
		return new Size2d(m_width / size.m_width, m_height / size.m_height);
	}
	
	/**
	 * 폭과 높이를 각각 주어진 크기의 폭과 높이로 나눈 새 크기를 반환한다.
	 * 0으로 나누면 부동소수점 규칙에 따라 결과가 무한대 또는 {@code NaN}이 된다.
	 *
	 * @param size	나눌 크기.
	 * @return	나눈 결과 크기.
	 * @throws NullPointerException	{@code size}가 {@code null}인 경우.
	 */
	public Size2d divideBy(Size2i size) {
		return new Size2d(m_width / size.getWidth(), m_height / size.getHeight());
	}
	
	/**
	 * 폭과 높이를 각각 {@link Math#floor(double)}로 내림한 정수 크기를 반환한다.
	 * {@code int} 범위를 벗어난 값은 {@code int} 변환 규칙에 따라 {@link Integer#MAX_VALUE} 또는
	 * {@link Integer#MIN_VALUE}가 되고, {@code NaN}은 0이 된다.
	 *
	 * @return	내림한 정수 크기.
	 */
	public Size2i floorToInt() {
		return new Size2i((int)Math.floor(m_width), (int)Math.floor(m_height));
	}
	
	/**
	 * 폭과 높이를 각각 {@link Math#ceil(double)}로 올림한 정수 크기를 반환한다.
	 * {@code int} 범위를 벗어난 값은 {@code int} 변환 규칙에 따라 {@link Integer#MAX_VALUE} 또는
	 * {@link Integer#MIN_VALUE}가 되고, {@code NaN}은 0이 된다.
	 *
	 * @return	올림한 정수 크기.
	 */
	public Size2i ceilToInt() {
		return new Size2i((int)Math.ceil(m_width), (int)Math.ceil(m_height));
	}
	
	/**
	 * {@code "폭x높이"} 형식의 문자열을 파싱하여 미터 단위 크기 객체를 만든다.
	 * <p>
	 * 소문자 {@code 'x'}로 나눈 앞의 두 부분을 각각 앞뒤 공백을 제거한 뒤
	 * {@link UnitUtils#parseLengthInMeter(String)}로 변환한다. 따라서 각 부분에 {@code mm}, {@code cm},
	 * {@code m}, {@code km} 단위 접미사를 붙일 수 있고(예: {@code "1.5m x 20cm"}), 접미사가 없으면 미터로 해석한다.
	 * 세 번째 이후 부분은 무시한다.
	 *
	 * @param str	파싱할 문자열.
	 * @return	미터 단위로 변환된 크기 객체.
	 * @throws NumberFormatException	폭 또는 높이 부분이 숫자가 아닌 경우.
	 * @throws ArrayIndexOutOfBoundsException	문자열에 {@code 'x'}가 없는 경우.
	 * @throws NullPointerException	{@code str}이 {@code null}인 경우.
	 */
	public static Size2d fromString(String str) {
		String[] parts = str.split("x");
		
		double x = UnitUtils.parseLengthInMeter(parts[0].trim());
		double y = UnitUtils.parseLengthInMeter(parts[1].trim());
		return new Size2d(x, y);
	}
	
	@Override
	public String toString() {
		String fmt1 = (m_width == Math.floor(m_width)) ? "%.0f" :  "%f";
		String fmt2 = (m_height == Math.floor(m_height)) ? "%.0f" :  "%f";
		return String.format(fmt1 + "x" + fmt2, m_width, m_height);
	}
	
	/**
	 * 폭과 높이를 소수점 이하 {@code ndecimal} 자리로 표기한 {@code "폭x높이"} 문자열을 반환한다.
	 *
	 * @param ndecimal	소수점 이하 자릿수.
	 * @return	{@code "폭x높이"} 형식 문자열.
	 * @throws java.util.IllegalFormatException	{@code ndecimal}이 음수인 경우.
	 */
	public String toString(int ndecimal) {
		return String.format("%."+ndecimal+"fx%."+ndecimal+"f", m_width, m_height);
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( obj == null || getClass() != obj.getClass() ) {
			return false;
		}
		
		Size2d other = (Size2d)obj;
		return Objects.equals(m_width, other.m_width)
			&& Objects.equals(m_height, other.m_height);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(m_width, m_height);
	}
}
