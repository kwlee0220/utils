package utils.io;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 *
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
	 * @throws IOException
	 */
	public static TempFile create() throws IOException {
		return new TempFile();
	}
	
	public File getFile() {
		return m_tempFile;
	}
	
	public OutputStream getOutputStream() throws IOException {
		return new FileOutputStream(m_tempFile);
	}
	
	@Override
	public void close() throws IOException {
		m_tempFile.delete();
	}
}
