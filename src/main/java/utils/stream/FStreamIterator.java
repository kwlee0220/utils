package utils.stream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import utils.func.FOption;


/**
 * {@link FStream}을 {@link Iterator}로 순회하는 반복자({@link FStream#iterator()}).
 * <p>
 * 원소는 {@link #hasNext()}가 처음 불릴 때 하나씩 읽는다(미리 읽지 않음). 스트림 끝에 닿으면 그 즉시 원본을 닫으므로,
 * 끝까지 순회하면 빈 스트림이라도 원본이 닫힌다. 중간에 멈추는 경우에는 {@link #close()}나 원본의 {@code close()}로
 * 직접 닫아야 한다. 끝 여부는 {@link FOption}으로 판단하므로 {@code null} 원소도 그대로 반환한다.
 *
 * @param <T>	원소 타입.
 * @author Kang-Woo Lee (ETRI)
 */
class FStreamIterator<T> implements Iterator<T>, AutoCloseable {
	private final FStream<T> m_strm;
	private FOption<T> m_next = null;	// 미리 읽은 다음 원소. null이면 아직 읽지 않음.
	private boolean m_done = false;		// 끝에 닿았거나 닫힘. 원본은 이미 닫혔다.

	FStreamIterator(FStream<T> strm) {
		m_strm = strm;
	}

	@Override
	public void close() throws Exception {
		if ( !m_done ) {
			m_done = true;
			m_next = null;
			m_strm.close();
		}
	}

	@Override
	public boolean hasNext() {
		if ( m_done ) {
			return false;
		}
		if ( m_next == null ) {
			m_next = m_strm.next();
			if ( m_next.isAbsent() ) {
				m_done = true;
				m_next = null;
				m_strm.closeQuietly();
				return false;
			}
		}
		return true;
	}

	@Override
	public T next() {
		if ( !hasNext() ) {
			throw new NoSuchElementException();
		}

		T next = m_next.getUnchecked();
		m_next = null;
		return next;
	}
}
