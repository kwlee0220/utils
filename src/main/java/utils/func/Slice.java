package utils.func;


/**
 * 순서가 있는 원소열의 구간(시작·끝·간격)을 표현한다.
 * <p>
 * 시작 인덱스는 포함, 끝 인덱스는 제외이며 둘 다 {@code null}이면 제한이 없다. 이 값을 쓰는
 * {@code FStream.slice}는 음수 시작 인덱스를 {@code 0}으로 보고, 끝이 시작보다 작으면 빈 결과를 낸다.
 * step은 {@code null}(= 1)이거나 양수여야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Slice {
	private final Integer m_start;
	private final Integer m_end;
	private final Integer m_step;
	
	private Slice(Builder builder) {
		m_start = builder.m_start;
		m_end = builder.m_end;
		m_step = builder.m_step;
	}
	
	/**
	 * Slice의 시작 인덱스를 반환한다.
	 * 
	 * @return	시작 인덱스.
	 */
	public Integer start() {
		return m_start;
	}
	
	/**
	 * Slice의 마지막(exclusive) 인덱스를 반환한다.
	 * <p>
	 * 반환되는 인덱스는 실제로는 마지막 인덱스보다 1 큰 값이다.
	 * 
	 * @return 끝 인덱스.
	 */
	public Integer end() {
		return m_end;
	}
	
	/**
	 * Slice의 step 값을 반환한다.
	 * 
	 * @return	Step 값.
	 */
	public Integer step() {
		return m_step;
	}
	
	@Override
	public String toString() {
		return String.format("Slice[start=%s, end=%s, step=%s]", m_start, m_end, m_step);
	}
	
	/**
	 * {@link Slice}를 생성하기 위한 {@link Builder}를 생성한다.
	 * <p>
	 * 생성된 빌더의 시작/끝 인덱스는 {@code null}이고 step은 1로 초기화되어 있다.
	 * 
	 * @return	새 {@link Builder} 객체.
	 */
	public static Builder builder() {
		return new Builder();
	}
	/**
	 * {@link Slice} 객체 생성을 위한 빌더 클래스.
	 * <p>
	 * 설정하지 않은 시작/끝 인덱스는 {@code null}로, step은 1로 유지된다. step은 {@link #build()}에서 검사한다.
	 */
	public static class Builder {
		/**
		 * 시작/끝 인덱스가 {@code null}이고 step이 1인 빌더를 생성한다.
		 */
		public Builder() { }
		
		private Integer m_start;
		private Integer m_end;
		private Integer m_step = 1;
		
		/**
		 * 지금까지 설정된 값으로 {@link Slice} 객체를 생성한다.
		 * 
		 * @return	생성된 {@link Slice} 객체.
		 * @throws IllegalArgumentException	step이 {@code null}이 아니면서 양수가 아닌 경우.
		 */
		public Slice build() {
			// step 0은 읽을 때 0으로 나누기가 되고, 음수는 양수처럼 동작하므로 미리 거부한다.
			utils.Preconditions.checkArgument(m_step == null || m_step > 0,
											"step must be positive: %s", m_step);
			return new Slice(this);
		}
		
		/**
		 * Slice의 시작 인덱스를 설정한다.
		 * 
		 * @param start	시작 인덱스. {@code null}일 수 있다.
		 * @return	본 빌더 객체.
		 */
		public Builder start(Integer start) {
			m_start = start;
			return this;
		}
		
		/**
		 * Slice의 끝(exclusive) 인덱스를 설정한다.
		 * 
		 * @param end	끝 인덱스. {@code null}일 수 있다.
		 * @return	본 빌더 객체.
		 */
		public Builder end(Integer end) {
			m_end = end;
			return this;
		}
		
		/**
		 * Slice의 step 값을 설정한다.
		 * 
		 * @param step	step 값. {@code null}(= 1)이거나 양수여야 한다({@link #build()}에서 검사).
		 * @return	본 빌더 객체.
		 */
		public Builder step(Integer step) {
			m_step = step;
			return this;
		}
	}
}
