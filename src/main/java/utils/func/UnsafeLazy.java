package utils.func;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import utils.Preconditions;


/**
 * 동기화 없이 값을 지연 적재(lazy loading)하는 클래스이다.
 * <p>
 * API는 {@link Lazy}와 같다. 다른 점은 동기화하지 않는다는 것뿐이므로, 한 스레드에서만 쓰거나 호출자가 따로
 * 동기화하는 경우에 쓴다. 여러 스레드가 동시에 처음 {@link #get()}을 호출하면 supplier가 여러 번 호출될 수 있고,
 * 어느 결과가 남을지 정해지지 않는다. 동적 프록시가 필요하면 {@link Lazy#wrap(Supplier, Class)}를 쓴다.
 *
 * @param <T>	적재되는 값의 타입.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UnsafeLazy<T> {
	@Nullable private volatile T m_ref;
	@Nullable private final Supplier<? extends T> m_supplier;

	/**
	 * 주어진 {@link Supplier}로 값을 처음 {@link #get()}할 때 적재하는 인스턴스를 생성한다.
	 *
	 * @param <T>		적재되는 값의 타입.
	 * @param supplier	값을 생성하는 {@link Supplier}.
	 * @return	{@link UnsafeLazy} 인스턴스.
	 * @throws IllegalArgumentException	{@code supplier}가 {@code null}인 경우.
	 */
	public static <T> UnsafeLazy<T> of(Supplier<? extends T> supplier) {
		Preconditions.checkNotNullArgument(supplier, "supplier is null");
		return new UnsafeLazy<>(null, supplier);
	}

	/**
	 * 주어진 값이 이미 적재된 인스턴스를 생성한다.
	 * <p>
	 * supplier가 없으므로 {@link #invalidate()}로 비운 뒤 {@link #get()}을 호출하면 {@link IllegalStateException}이
	 * 발생한다.
	 *
	 * @param <T>	적재되는 값의 타입.
	 * @param value	적재할 값.
	 * @return	{@link UnsafeLazy} 인스턴스.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public static <T> UnsafeLazy<T> ofValue(T value) {
		Preconditions.checkNotNullArgument(value, "value is null");
		return new UnsafeLazy<>(value, null);
	}

	private UnsafeLazy(@Nullable T value, @Nullable Supplier<? extends T> supplier) {
		m_ref = value;
		m_supplier = supplier;
	}

	/**
	 * 적재된 값이 있는지 여부를 반환한다.
	 *
	 * @return	적재된 값이 있으면 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public boolean isLoaded() {
		return m_ref != null;
	}

	/**
	 * 적재된 값을 반환한다. 적재된 값이 없으면 supplier를 호출하여 적재한 뒤 반환한다.
	 *
	 * @return	적재된 값. {@code null}이 아니다.
	 * @throws IllegalStateException	값이 비어 있고 supplier도 없는 경우, 또는 supplier가 {@code null}을 반환한
	 * 			경우({@code null}은 "적재 안 됨"과 구분할 수 없으므로 적재하지 않는다. {@link Lazy}와 같다).
	 */
	public T get() {
		T value = m_ref;
		if ( value == null ) {
			Preconditions.checkState(m_supplier != null, "Value supplier is null");
			value = m_supplier.get();
			Preconditions.checkState(value != null, "supplier returned null value");
			m_ref = value;
		}
		return value;
	}

	/**
	 * 주어진 값을 적재한다.
	 *
	 * @param value	적재할 값.
	 * @throws IllegalArgumentException	{@code value}가 {@code null}인 경우.
	 */
	public void set(T value) {
		Preconditions.checkNotNullArgument(value, "value is null");
		m_ref = value;
	}

	/**
	 * 적재된 값을 비운다. supplier가 있으면 다음 {@link #get()}에서 다시 적재한다.
	 */
	public void invalidate() {
		m_ref = null;
	}

	/**
	 * 적재된 값을 비우고, 비운 값이 있었으면 {@code dtor}에 넘긴다.
	 *
	 * @param dtor	비운 값을 정리하는 함수. 비운 값이 없으면 호출되지 않는다.
	 * @throws IllegalArgumentException	{@code dtor}가 {@code null}인 경우.
	 */
	public void invalidate(Consumer<? super T> dtor) {
		Preconditions.checkNotNullArgument(dtor, "dtor is null");
		T value = m_ref;
		if ( value != null ) {
			m_ref = null;
			dtor.accept(value);
		}
	}

	@Override
	public String toString() {
		T value = m_ref;
		return String.format("UnsafeLazy[%s]", (value != null) ? value : "unloaded");
	}
}
