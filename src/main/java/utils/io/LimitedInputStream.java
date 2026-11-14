package utils.io;

import java.io.IOException;
import java.io.InputStream;

import utils.Preconditions;



/**
 * 원본 입력 스트림에서 지정한 byte 수까지만 읽을 수 있도록 제한하는 {@link InputStream}이다.
 * 제한에 도달하면 원본에 데이터가 남아 있어도 EOF({@code -1})를 반환한다.
 * <p>
 * 본 클래스는 {@code close()}를 재정의하지 않으므로 본 스트림을 닫아도 원본 스트림은 닫히지 않는다.
 * @author Kang-Woo Lee (ETRI)
 */
public class LimitedInputStream extends InputStream {
	private final InputStream m_src;
	private int m_remains;
	
	/**
	 * 원본 입력 스트림에서 최대 {@code limit} byte까지만 읽는 스트림을 생성한다.
	 *
	 * @param src	원본 입력 스트림.
	 * @param limit	읽을 수 있는 최대 byte 수. 0 이상이어야 한다.
	 * @throws IllegalArgumentException	{@code src}가 {@code null}이거나 {@code limit}이 음수인 경우.
	 */
	public LimitedInputStream(InputStream src, int limit) {
		Preconditions.checkNotNullArgument(src, "src is null");
		Preconditions.checkArgument(limit >= 0, "limit is negative: " + limit);
		
		m_src = src;
		m_remains = limit;
	}

	@Override
	public int read() throws IOException {
		if ( m_remains == 0 ) {
			return -1;
		}
		
		int b = m_src.read();
		if ( b >= 0 ) {
			--m_remains;
		}
		return b;
	}

	@Override
    public int read(byte b[], int off, int len) throws IOException {
		if ( m_remains == 0 ) {
			return -1;
		}
		
		int nbytes = Math.min(m_remains, len);
		int nreads = m_src.read(b, off, nbytes);
		if ( nreads < 0 ) {
			return -1;
		}
		m_remains -= nreads;
		
		return nreads;
    }
}
