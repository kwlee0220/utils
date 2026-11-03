package utils.io;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * 시스템 기본 임시 디렉토리에 생성한 임시 파일을 감싸는 객체이다.
 * {@link #close()}를 호출하면 해당 파일을 삭제하며, 삭제 실패는 무시된다.
 * @author Kang-Woo Lee (ETRI)
 */
public class TempFile implements Closeable {
	private final File m_tempFile;
	
	private TempFile() throws IOException {
		m_tempFile = Files.createTempFile(null, null).toFile();
	}
	
	/**
	 * 임시 파일을 생성한다.
	 *
	 * @return	임시 파일 객체.
	 * @throws IOException	임시 파일 생성 중 오류가 발생한 경우.
	 */
	public static TempFile create() throws IOException {
		return new TempFile();
	}
	
	/**
	 * 임시 파일의 {@link File} 객체를 반환한다.
	 *
	 * @return	임시 파일.
	 */
	public File getFile() {
		return m_tempFile;
	}
	
	/**
	 * 임시 파일에 쓰기 위한 출력 스트림을 연다.
	 * 기존 내용은 덮어쓰며, 반환된 스트림은 호출자가 닫아야 한다.
	 *
	 * @return	파일 출력 스트림.
	 * @throws IOException	파일을 열 수 없는 경우.
	 */
	public OutputStream getOutputStream() throws IOException {
		return new FileOutputStream(m_tempFile);
	}
	
	@Override
	public void close() throws IOException {
		m_tempFile.delete();
	}
}
