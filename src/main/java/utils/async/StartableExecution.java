package utils.async;

/**
 * {@link Startable#start()}를 호출해야 수행이 시작되는 {@link Execution}.
 *
 * @param <T>	연산 결과 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface StartableExecution<T> extends Execution<T>, Startable {
}
