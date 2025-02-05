package utils.func;


/**
 * Represents a slice of a sequence.
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
	 * @return	step	Step 값.
	 */
	public Integer step() {
		return m_step;
	}
	
	public static Builder builder() {
		return new Builder();
	}
	public static class Builder {
		private Integer m_start;
		private Integer m_end;
		private Integer m_step = 1;
		
		public Slice build() {
			return new Slice(this);
		}
		
		public Builder start(Integer start) {
			m_start = start;
			return this;
		}
		
		public Builder end(Integer end) {
			m_end = end;
			return this;
		}
		
		public Builder step(Integer step) {
			m_step = step;
			return this;
		}
	}
}
