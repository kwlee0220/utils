package utils.async;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import utils.io.IOUtils;


/**
 * <code>CommandExecutor</code>는 주어진 명령어 프로그램을 sub-process를 통해 실행시키는 작업을 수행한다.
 * <p>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CommandVariable extends Closeable {
	public String getName();
	public String getValue();
	
	/**
	 * Command variable 동작을 위해 할당된 자원을 모두 반환한다.
	 */
	public void close();
	
	public default String getValueByModifier(String mod) {
		switch ( mod ) {
			case "name":
				return getName();
			case "value":
				return getValue();
			default:
				throw new IllegalArgumentException("Unsupported Modifier: " + mod);
		}
	}
	
	public static final class StringVariable implements CommandVariable {
		private final String m_name;
		private final String m_value;
		
		private StringVariable(String name, String value) {
			m_name = name;
			m_value = value;
		}
		
		@Override
		public String getName() {
			return m_name;
		}

		@Override
		public String getValue() {
			return m_value;
		}

		@Override
		public void close() { }
	}
	
	public static class FileVariable implements CommandVariable {
		private final String m_name;
		private final File m_file;
		
		public FileVariable(String name, File file) {
			m_name = name;
			m_file = file;
		}
		
		@Override
		public String getName() {
			return m_name;
		}

		@Override
		public String getValue() {
			try {
				return IOUtils.toString(m_file);
			}
			catch ( IOException e ) {
				throw new RuntimeException("Failed to read FileVariable: name=" + m_name
											+ ", path=" + m_file.getAbsolutePath() + ", cause=" + e);
			}
		}
		
		public File getFile() {
			return m_file;
		}
		
		public void deleteFile() {
			m_file.delete();
		}

		@Override
		public void close() {
			if ( m_file != null ) {
				m_file.delete();
			}
		}

		@Override
		public String getValueByModifier(String mod) {
			switch ( mod ) {
				case "name":
					return getName();
				case "value":
					return getValue();
				case "path":
					return getFile().getAbsolutePath();
				default:
					throw new IllegalArgumentException("Unsupported Modifier: " + mod);
			}
		}
		
		@Override
		public String toString() {
			return String.format("FileVariable[%s]: %s", m_name, m_file.getAbsolutePath());
		}
	}
}