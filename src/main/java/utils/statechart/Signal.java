package utils.statechart;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface Signal {
	public static class CancelSignal implements Signal {
		@Override
		public String toString() {
			return "CancelSignal";
		}
	}

}
