package utils.stream;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * 읽은 원소를 스트림 앞에 되돌려 넣을 수 있는 스트림.
 * <p>
 * {@link #prepend(Object)}로 넣은 원소는 원본 스트림보다 먼저 반환되며, 여러 개를 넣은 경우에는
 * 가장 나중에 넣은 원소부터(LIFO) 반환된다. 닫으면 되돌려 넣은 원소를 버리고 원본 스트림도 닫는다.
 *
 * @param <T>	원소 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class PrependableFStream<T> implements FStream<T> {
	private final FStream<T> m_src;
	private final List<T> m_prefix = Lists.newArrayList();
	private boolean m_closed = false;
	
	PrependableFStream(FStream<T> src) {
		utils.Preconditions.checkNotNullArgument(src, "FStream is null");
		
		m_src = src;
	}

	@Override
	public void close() throws Exception {
		m_closed = true;
		m_prefix.clear();
		m_src.close();
	}

	/**
	 * 다음 원소를 소비하지 않고 미리 확인한다.
	 * <p>
	 * 되돌려 넣은 원소가 없으면 원본 스트림에서 한 원소를 읽어 앞에 넣어 두므로, 이후 {@link #next()}가
	 * 같은 원소를 반환한다.
	 *
	 * @return	다음 원소. 원소가 없거나 스트림이 닫힌 경우는 {@link FOption#empty()}.
	 */
	public FOption<T> peekNext() {
		if ( m_closed ) {
			return FOption.empty();
		}
		
		if ( m_prefix.isEmpty()) {
			return m_src.next().ifPresent(m_prefix::add);
		}
		else {
			return FOption.of(m_prefix.get(m_prefix.size()-1));
		}
	}
	
	/**
	 * 다음 원소가 있는지 여부를 반환한다.
	 * <p>
	 * 되돌려 넣은 원소가 없으면 원본 스트림에서 한 원소를 읽어 앞에 넣어 둔다.
	 * {@link #peekNext()}와 달리 닫힘 여부를 검사하지 않으므로, 닫힌 뒤의 동작은 원본 스트림의
	 * {@code next()} 동작을 따른다.
	 *
	 * @return	다음 원소가 있으면 {@code true}.
	 */
	public boolean hasNext() {
		if ( m_prefix.size() > 0 ) {
			return true;
		}
		
		return m_src.next()
					.ifPresent(m_prefix::add)
					.isPresent();
	}

	@Override
	public FOption<T> next() {
		if ( m_prefix.isEmpty() ) {
			return m_src.next();
		}
		else {
			return FOption.of(m_prefix.remove(m_prefix.size()-1));
		}
	}

	/**
	 * 주어진 원소를 스트림 앞에 넣는다. 다음 {@link #next()} 호출은 이 원소를 반환한다.
	 *
	 * @param value	앞에 넣을 원소.
	 * @throws IllegalStateException	스트림이 이미 닫힌 경우.
	 */
	public void prepend(T value) {
		Preconditions.checkState(!m_closed);
		
		m_prefix.add(value);
	}
	
	/**
	 * {@code predicate}를 만족하는 동안 원소들에 {@code effect}를 차례로 적용한다.
	 * <p>
	 * {@code predicate}를 만족하지 않는 첫 원소를 만나면 그 원소를 스트림 앞에 되돌려 넣고 멈춘다.
	 * 스트림은 닫지 않는다.
	 *
	 * @param predicate	계속 진행할지 판단하는 조건.
	 * @param effect	조건을 만족하는 원소에 적용할 작업.
	 */
	public void forEachWhile(Predicate<? super T> predicate, Consumer<? super T> effect) {
		FOption<T> onext;
		while ( (onext = next()).isPresent() ) {
			if ( onext.filter(predicate).ifPresent(effect).isAbsent() ) {
				prepend(onext.get());
				break;
			}
		}
	}
}
