package utils.async.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Keyed;
import utils.Preconditions;
import utils.func.Lazy;
import utils.io.IOUtils;
import utils.stream.FStream;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariable implements Keyed<String> {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandVariable.class);

	private final String m_name;
	@Nullable private String m_value;		// null이면 {@link #m_file}에서 읽어온다
	@Nullable private final File m_file;	// null이면 {@link #m_value}에서 읽어온다
	private final Lazy<String> m_cachedValue = Lazy.of(this::readFileContent);
	
	/**
	 * Command variable을 생성한다.
	 *
	 * @param name	Command variable 이름.
	 * @param value	Command variable 값.
	 * 				{@code null}이면 {{@link #getValue()}} 호출시
	 * 				{@link #m_file}에서 읽어온다.
	 * @param file	Command variable 값이 기록될 파일.
	 */
	public CommandVariable(String name, @Nullable String value, @NonNull File file) {
		Preconditions.checkNotNullArgument(name, "name");
		Preconditions.checkNotNullArgument(value, "value is null");
		Preconditions.checkNotNullArgument(file, "file is null");

		m_name = name;
		m_value = value;
		m_file = file;
	}
	
	/**
	 * Command variable을 생성한다.
	 *
	 * @param name	Command variable 이름.
	 * @param value	Command variable 값.
	 * @throws IOException	Command variable 값이 기록될 임시 파일을 생성하는 중 오류가 발생한 경우.
	 */
	public CommandVariable(String name, @NonNull String value) throws IOException {
		Preconditions.checkNotNullArgument(name, "name is null");
		Preconditions.checkNotNullArgument(value, "value is null");

		m_name = name;
		m_value = value;
		m_file = null;
	}
	
	/**
	 * Command variable을 생성한다.
	 *
	 * @param name	Command variable 이름.
	 * @param file	Command variable 값이 기록될 파일.
	 * @throws IOException	Command variable 값이 기록될 임시 파일을 생성하는 중 오류가 발생한 경우.
	 */
	public CommandVariable(String name, File file) throws IOException {
		Preconditions.checkNotNullArgument(name, "name");
		Preconditions.checkNotNullArgument(file, "file");

		m_name = name;
		m_value = null;
		m_file = file;
	}
	
	/**
	 * Command variable의 key를 반환한다. 기본 구현은 {@link #getName()}을 반환한다.
	 *
	 * @return Command variable key
	 */
	@Override
	public String key() {
		return getName();
	}

	public String getName() {
		return m_name;
	}
	
	public String getValue() {
		if ( isFileMode() ) {
			return m_cachedValue.get();
		}
		else {
			return m_value;
		}
	}
	
	public File getFile() {
		return m_file;
	}
	
	@Override
	public String toString() {
		String valueStr = (m_value != null) ? "value=" + m_value : "";
		String fileStr = (m_file != null) ? String.format("file=%s (exists=%s)",
														m_file.getAbsolutePath(), m_file.exists()) : "";
		return FStream.of(valueStr, fileStr)
						.filter(s -> !s.isEmpty())
						.join(", ", m_name + "=[", "]");
	}

	private String readFileContent() {
		Preconditions.checkState(isFileMode(), "not in file mode");
		
		try {
			return IOUtils.toString(m_file, StandardCharsets.UTF_8);
		}
		catch ( IOException e ) {
			throw new RuntimeException("Failed to read FileVariable: name=" + m_name
										+ ", path=" + m_file.getAbsolutePath(), e);
		}
	}
	
	public void close() {
		getValue();
		tryDeleteFile();
	}
	
	public String getValueByModifier(@NotNull String mod) {
		Preconditions.checkNotNullArgument(mod, "modifier must be non-null");
		
		switch ( mod ) {
			case "name":
				return getName();
			case "value":
				return getValue();
			case "path":
				toFileMode();
				return m_file.getAbsolutePath();
			default:
				throw new IllegalArgumentException("Unsupported Modifier: " + mod);
		}
	}
	
	private boolean isFileMode() {
		return m_value == null;
	}
	
	private void toFileMode() {
		if ( !isFileMode() ) {
			try {
				if ( !m_file.exists() ) {
					if ( m_value != null) {
						IOUtils.toFile(m_value, m_file);
					}
					else {
						m_file.createNewFile();
					}
				}
				m_value = null;
			}
			catch ( IOException e ) {
				throw new RuntimeException("Failed to write FileVariable: name=" + m_name
											+ ", path=" + m_file.getAbsolutePath(), e);
			}
		}
	}

	private void tryDeleteFile() {
		// delete()가 false를 반환하더라도 파일이 이미 사라진 상태라면(예: 이전 호출에서 삭제됨)
		// 정상으로 간주하고 warn을 남기지 않는다.
		if ( !m_file.delete() && m_file.exists() ) {
			s_logger.warn("failed to delete file: name={}, path={}", m_name, m_file.getAbsolutePath());
		}
	}
}