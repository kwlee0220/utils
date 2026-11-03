package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import utils.stream.FStream;

/**
 * 여러 입력 스트림을 순서대로 이어 하나의 입력 스트림처럼 읽게 하는 {@link InputStream}이다.
 * 현재 원본 스트림이 EOF에 도달하면 다음 원본 스트림으로 넘어간다.
 * <p>
 * 본 클래스는 {@code close()}를 재정의하지 않으므로 본 스트림을 닫아도 원본 스트림들은
 * 닫히지 않는다. 원본 스트림의 종료는 호출자가 책임진다.
 * @author Kang-Woo Lee (ETRI)
 */
public class SequenceInputStream extends InputStream {
	private final FStream<InputStream> m_sources;
	private InputStream m_src;
	
	/**
	 * 주어진 입력 스트림들을 순서대로 이어 읽는 스트림을 생성한다.
	 *
	 * @param srcs	이어 읽을 입력 스트림들.
	 * @return	생성된 {@link SequenceInputStream} 객체.
	 */
	public static SequenceInputStream sequence(InputStream... srcs) {
		return new SequenceInputStream(FStream.of(srcs));
	}
	
	/**
	 * 주어진 리스트의 입력 스트림들을 순서대로 이어 읽는 스트림을 생성한다.
	 *
	 * @param srcs	이어 읽을 입력 스트림 리스트.
	 * @return	생성된 {@link SequenceInputStream} 객체.
	 */
	public static SequenceInputStream sequence(List<InputStream> srcs) {
		return new SequenceInputStream(FStream.from(srcs));
	}
	
	/**
	 * 주어진 입력 스트림들을 순서대로 이어 읽는 스트림을 생성한다.
	 * 생성 시점에 첫 번째 원본 스트림을 {@code sources}에서 미리 가져온다.
	 *
	 * @param sources	이어 읽을 입력 스트림들의 스트림.
	 */
	public SequenceInputStream(FStream<InputStream> sources) {
		m_sources = sources;
		m_src = sources.next().getOrNull();
	}

	@Override
	public int read() throws IOException {
		while ( m_src != null ) {
			int ret = m_src.read();
			if ( ret != -1 ) {
				return ret;
			}
			
			m_src = m_sources.next().getOrNull();
		}
		
		return -1;
	}

	@Override
    public int read(byte b[], int off, int len) throws IOException {
		while ( m_src != null ) {
			int nread = m_src.read(b, off, len);
			if ( nread >= 0 ) {
				return nread;
			}
			m_src = m_sources.next().getOrNull();
		}
		
		return -1;
    }
	
	@Override
    public int available() throws IOException {
        return m_src != null ? m_src.available() : 0;
    }
}
