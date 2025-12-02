package utils.statechart;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ExceptionState<C extends StateContext> extends SinkState<C> {
	private Throwable m_failureCause;
	
	public ExceptionState(String name, C context) {
		super(name, context);
	}
	
	public Throwable getFailureCause() {
		return m_failureCause;
	}
	
	public void setFailureCause(Throwable cause) {
		m_failureCause = cause;
	}
}
