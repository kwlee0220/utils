package utils.async.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Keyed;
import utils.Preconditions;
import utils.io.IOUtils;
import utils.stream.FStream;


/**
 * {@link CommandExecution}의 command line에서 {@code ${name}} 또는 {@code ${name:modifier}} 형태로
 * 참조되는 이름 붙은 값.
 * <p>
 * 값은 문자열로 직접 갖거나(값 모드) 파일에 담아 둘 수 있다(파일 모드). 참조 시 modifier에 따라 다음으로 치환된다.
 * <ul>
 *   <li>{@code name} (생략 시 기본값) — 변수 이름.</li>
 *   <li>{@code value} — 변수 값. 파일 모드이면 파일 내용(UTF-8).</li>
 *   <li>{@code path} — 값이 기록된 파일의 절대 경로. 값 모드이면 이때 값을 파일로 옮기고 파일 모드로 바뀐다.
 *       파일 없이 값만으로 생성된 변수는 임시 파일을 만든다.</li>
 * </ul>
 * 명령이 결과를 기록하는 출력 변수는 파일로 생성하고, 명령이 끝난 뒤 {@link #getValue()}로 결과를 읽는다.
 * 파일 모드의 {@link #getValue()}는 호출할 때마다 파일을 다시 읽으므로, 명령 실행 중 미리 읽었더라도 이후 기록된
 * 내용을 얻을 수 있다.
 * <p>
 * {@link #close()}는 파일 내용을 읽어 둔 뒤 변수 파일을 삭제하며, 이후의 {@link #getValue()}는 그 값을 반환한다.
 * ({@code RESTfulAsyncRpcServer}처럼 실행이 끝나면 곧바로 실행을 close하고 출력은 나중에 읽는 사용처가 있다.)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariable implements Keyed<String> {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandVariable.class);

	private final String m_name;
	@Nullable private String m_value;		// null이면 {@link #m_file}에서 읽어온다
	// null이면 {@link #m_value}에서 읽어온다. 값만으로 생성된 변수는 'path' modifier가 처음 요청될 때
	// 임시 파일이 생성되어 설정된다.
	@Nullable private File m_file;
	@Nullable private String m_lastReadValue;	// 파일 모드에서 마지막으로 읽은 값. close() 후 getValue()가 반환한다.
	private boolean m_closed = false;
	
	/**
	 * Command variable을 생성한다.
	 *
	 * @param name	Command variable 이름.
	 * @param value	Command variable 값.
	 * 				{@code null}이면 {{@link #getValue()}} 호출시
	 * 				{@link #m_file}에서 읽어온다.
	 * @param file	Command variable 값이 기록될 파일.
	 */
	public CommandVariable(@NotNull String name, @Nullable String value, @NotNull File file) {
		Preconditions.checkNotNullArgument(name, "name");
		Preconditions.checkNotNullArgument(file, "file is null");

		m_name = name;
		m_value = value;
		m_file = file;
	}
	
	/**
	 * 값만으로 command variable을 생성한다.
	 * <p>
	 * 생성 시점에는 파일을 만들지 않는다. {@code path} modifier({@code ${name:path}})가 처음 요청될 때
	 * 값을 기록한 임시 파일을 생성하며, 이 파일은 {@link #close()}에서 삭제된다.
	 *
	 * @param name	Command variable 이름.
	 * @param value	Command variable 값.
	 * @throws IOException	현재 구현에서는 발생하지 않는다. 기존 호출자와의 호환을 위해 선언을 유지한다.
	 */
	public CommandVariable(@NotNull String name, @NotNull String value) throws IOException {
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
	 */
	public CommandVariable(@NotNull String name, @NotNull File file) {
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

	public @NotNull String getName() {
		return m_name;
	}
	
	/**
	 * 변수 값을 반환한다.
	 * <p>
	 * 파일 모드이면 호출할 때마다 파일을 다시 읽는다. {@link #close()}로 파일이 삭제된 뒤에는 마지막으로 읽은
	 * 값({@code close()}가 읽어 둔 값)을 반환한다.
	 *
	 * @return	변수 값.
	 * @throws IllegalStateException	{@code close()} 시점에 파일을 읽지 못했고(예: 명령이 결과 파일을 만들지 않음)
	 * 									그 전에도 읽은 적이 없는 파일 모드 변수를 {@code close()} 후에 읽는 경우.
	 * @throws RuntimeException	파일을 읽지 못한 경우.
	 */
	public synchronized String getValue() {
		if ( !isFileMode() ) {
			return m_value;
		}
		if ( m_closed ) {
			Preconditions.checkState(m_lastReadValue != null,
									"variable file was deleted before its value was read: name=%s", m_name);
			return m_lastReadValue;
		}
		m_lastReadValue = readFileContent();
		return m_lastReadValue;
	}
	
	/**
	 * 변수 값이 기록된(또는 기록될) 파일을 반환한다.
	 *
	 * @return	파일. 값만으로 생성된 변수가 아직 {@code path} modifier로 사용되지 않았으면 {@code null}.
	 */
	public synchronized @Nullable File getFile() {
		return m_file;
	}
	
	@Override
	public synchronized String toString() {
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
	
	/**
	 * 파일 모드이면 파일 내용을 읽어 둔 뒤 변수 파일을 삭제한다. 이후 {@link #getValue()}는 읽어 둔 값을 반환한다.
	 * <p>
	 * 파일을 읽지 못해도(예: 명령이 결과 파일을 만들지 않음) 예외를 던지지 않고 삭제를 진행한다. 파일이 없는
	 * 변수(값만으로 생성되고 {@code path}로 사용되지 않은 변수)는 삭제할 파일이 없다. 중복 호출은 안전하다.
	 */
	public synchronized void close() {
		if ( m_closed ) {
			return;
		}
		if ( isFileMode() ) {
			try {
				m_lastReadValue = readFileContent();
			}
			catch ( RuntimeException e ) {
				s_logger.debug("failed to read variable file before deleting it: {}, cause={}", this, ""+e);
			}
		}
		m_closed = true;
		if ( m_file != null ) {
			tryDeleteFile();
		}
	}
	
	/**
	 * 주어진 modifier에 따른 치환 결과를 반환한다.
	 *
	 * @param mod	modifier ({@code name}, {@code value}, {@code path}).
	 * @return	치환 결과.
	 * @throws IllegalArgumentException	지원하지 않는 modifier인 경우.
	 */
	public synchronized String getValueByModifier(@NotNull String mod) {
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
				if ( m_file == null ) {
					// 값만으로 생성된 변수: 값을 기록할 임시 파일을 만든다. POSIX에서는 소유자만 읽을 수 있는
					// 권한(0600)으로 생성되므로 비밀값을 담아도 다른 사용자에게 노출되지 않는다.
					m_file = Files.createTempFile("cmdvar-", ".txt").toFile();
					IOUtils.toFile(m_value, StandardCharsets.UTF_8, m_file);
				}
				else if ( !m_file.exists() ) {
					IOUtils.toFile(m_value, StandardCharsets.UTF_8, m_file);
				}
				m_value = null;
			}
			catch ( IOException e ) {
				throw new RuntimeException("Failed to write FileVariable: name=" + m_name
											+ ", path=" + (m_file != null ? m_file.getAbsolutePath() : "(temp)"), e);
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