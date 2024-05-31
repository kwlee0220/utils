package utils.func;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Slice {
	private Integer m_start;
	private Integer m_end;
	private Integer m_step = 1;
	
	private Slice(Builder builder) {
		m_start = builder.m_start;
		m_end = builder.m_end;
		m_step = builder.m_step;
	}
	
	public Integer start() {
		return m_start;
	}
	
	public Integer end() {
		return m_end;
	}
	
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
