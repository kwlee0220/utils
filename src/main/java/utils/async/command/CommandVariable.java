package utils.async.command;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.google.common.base.Preconditions;

import utils.Keyed;
import utils.io.IOUtils;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CommandVariable extends Keyed<String>, Closeable {
	/**
	 * Command variable 이름을 반환한다.
	 *
	 * @return	Command variable 이름
	 */
	public @NonNull String getName();
	
	/**
	 * Command variable 값을 반환한다.
	 *
	 * @return	Command variable 값
	 */
	public String getValue();
	
	public default String key() {
        return getName();
	}
	
	/**
	 * Command variable 동작을 위해 할당된 자원을 모두 반환한다.
	 */
	public void close();
	
	/**
	 * 지정된 modifier에 해당하는 값을 반환한다.
	 *
	 * @param mod	modifier. "key", "value" 중 하나여야 한다.
	 * @return	modifier에 해당하는 값
	 */
	public default String getValueByModifier(@NonNull String mod) {
		Preconditions.checkArgument(mod != null, "mod must be non-null");
		
		switch ( mod ) {
			case "name":
				return getName();
			case "value":
				return getValue();
			default:
				throw new IllegalArgumentException("Unsupported Modifier: " + mod);
		}
	}
	
	/**
	 * String 타입의 Command variable 구현.
	 *
	 * @author Kang-Woo Lee (ETRI)
	 */
	public static final class StringVariable implements CommandVariable {
		private final String m_name;
		private final String m_value;
		
		public StringVariable(String name, String value) {
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
	
	/**
	 * File 타입의 Command variable 구현.
	 *
	 * @author Kang-Woo Lee (ETRI)
	 */
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