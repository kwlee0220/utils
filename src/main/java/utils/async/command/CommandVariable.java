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
 * <h2>생성</h2>
 * 용도에 따라 다음 팩토리로 만든다. 용도는 {@link #close()} 때 파일을 지울지(파일 소유권)를 정한다.
 * <table border="1">
 *   <caption>팩토리별 동작</caption>
 *   <tr><th>팩토리</th><th>값</th><th>{@code close()} 시 파일 삭제</th></tr>
 *   <tr><td>{@link #input(String, String)}</td><td>문자열. {@code path} 요청 시 임시 파일 생성</td><td>예 (임시 파일)</td></tr>
 *   <tr><td>{@link #input(String, String, File)}</td><td>문자열. {@code path} 요청 시 주어진 파일에 기록(덮어씀)</td><td>예</td></tr>
 *   <tr><td>{@link #inputFile(String, File)}</td><td>이미 있는 파일의 내용</td><td><b>아니오</b> (호출자 소유)</td></tr>
 *   <tr><td>{@link #inputFile(String, File, boolean)}</td><td>이미 있는 파일의 내용</td><td>지정에 따름</td></tr>
 *   <tr><td>{@link #output(String, File)}</td><td>명령이 기록한 파일 내용. 생성 시 기존 파일을 지운다</td><td>예</td></tr>
 * </table>
 * 하위 클래스는 {@link #CommandVariable(String, String, File, boolean)}로 생성한다.
 *
 * <h2>값 읽기</h2>
 * 파일 모드의 {@link #getValue()}는 호출할 때마다 파일을 다시 읽으므로, 명령 실행 중 미리 읽었더라도 이후 기록된
 * 내용을 얻을 수 있다. {@link #close()}는 파일 내용을 읽어 둔 뒤 (소유한 경우) 파일을 삭제하며, 이후의
 * {@link #getValue()}는 그 값을 반환한다. ({@code RESTfulAsyncRpcServer}처럼 실행이 끝나면 곧바로 실행을
 * close하고 출력은 나중에 읽는 사용처가 있다.)
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandVariable implements Keyed<String>, AutoCloseable {
	private static final Logger s_logger = LoggerFactory.getLogger(CommandVariable.class);

	private final String m_name;
	@Nullable private String m_value;		// null이면 {@link #m_file}에서 읽어온다
	// null이면 {@link #m_value}에서 읽어온다. 값만으로 생성된 변수는 'path' modifier가 처음 요청될 때
	// 임시 파일이 생성되어 설정된다.
	@Nullable private File m_file;
	private boolean m_deleteOnClose;	// close()에서 m_file을 삭제할지 여부. 값만으로 생성되어 만든 임시 파일은 항상 삭제한다.
	@Nullable private String m_lastReadValue;	// 파일 모드에서 마지막으로 읽은 값. close() 후 getValue()가 반환한다.
	private boolean m_closed = false;
	
	/**
	 * 문자열 값을 갖는 입력 변수를 생성한다.
	 * <p>
	 * 생성 시점에는 파일을 만들지 않는다. {@code path} modifier({@code ${name:path}})가 처음 요청될 때 값을 기록한
	 * 임시 파일({@code cmdvar-*.txt}, POSIX에서는 소유자만 읽기·쓰기 가능)을 만들며, {@link #close()}에서 삭제한다.
	 *
	 * @param name	변수 이름.
	 * @param value	변수 값.
	 * @return	입력 변수.
	 * @throws IllegalArgumentException	{@code name} 또는 {@code value}가 {@code null}인 경우.
	 */
	public static CommandVariable input(String name, String value) {
		Preconditions.checkNotNullArgument(value, "value is null");
		return new CommandVariable(name, value, null, true);
	}

	/**
	 * 문자열 값을 갖고, {@code path} 요청 시 그 값을 주어진 파일에 기록하는 입력 변수를 생성한다.
	 * <p>
	 * 파일이 이미 있어도 값으로 덮어쓴다. 파일은 본 변수가 소유하여 {@link #close()}에서 삭제한다. 명령이 같은
	 * 파일에 결과를 다시 쓰는 입출력 겸용 변수로도 쓸 수 있다 ({@code path} 요청 이후의 {@link #getValue()}는 파일을
	 * 읽는다).
	 *
	 * @param name	변수 이름.
	 * @param value	변수 값.
	 * @param file	값을 기록할 파일.
	 * @return	입력 변수.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static CommandVariable input(String name, String value, File file) {
		Preconditions.checkNotNullArgument(value, "value is null");
		Preconditions.checkNotNullArgument(file, "file is null");
		return new CommandVariable(name, value, file, true);
	}

	/**
	 * 이미 있는 파일을 값으로 하는 입력 변수를 생성한다. 파일은 호출자 소유이므로 {@link #close()}에서 삭제하지 않는다.
	 *
	 * @param name	변수 이름.
	 * @param file	값이 담긴 파일.
	 * @return	입력 변수.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static CommandVariable inputFile(String name, File file) {
		return inputFile(name, file, false);
	}

	/**
	 * 이미 있는 파일을 값으로 하는 입력 변수를 생성한다.
	 *
	 * @param name	변수 이름.
	 * @param file	값이 담긴 파일.
	 * @param deleteOnClose	{@code true}이면 {@link #close()}에서 파일을 삭제한다 (예: 실행을 위해 복사해 둔 파일).
	 * @return	입력 변수.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static CommandVariable inputFile(String name, File file, boolean deleteOnClose) {
		Preconditions.checkNotNullArgument(file, "file is null");
		return new CommandVariable(name, null, file, deleteOnClose);
	}

	/**
	 * 명령이 결과를 기록하는 출력 변수를 생성한다.
	 * <p>
	 * 파일이 이미 있으면 생성 시 삭제한다. 이전 실행이 남긴 파일을 이번 실행의 결과로 잘못 읽지 않기 위함이며,
	 * 명령이 결과를 기록하지 않으면 {@link #getValue()}가 실패하여 이를 드러낸다. 파일은 {@link #close()}에서 삭제한다.
	 *
	 * @param name	변수 이름.
	 * @param file	명령이 결과를 기록할 파일.
	 * @return	출력 변수.
	 * @throws IllegalArgumentException	인자가 {@code null}이거나, 이미 있는 파일을 삭제하지 못한 경우.
	 */
	public static CommandVariable output(String name, File file) {
		Preconditions.checkNotNullArgument(file, "file is null");
		Preconditions.checkArgument(!file.exists() || file.delete(),
									"failed to delete stale output file: %s", file.getAbsolutePath());
		return new CommandVariable(name, null, file, true);
	}

	/**
	 * 변수를 생성한다. 하위 클래스용이며, 일반 사용자는 {@link #input(String, String)} 등의 팩토리를 사용한다.
	 *
	 * @param name	변수 이름.
	 * @param value	변수 값. {@code null}이면 파일 모드로, {@code file}의 내용이 값이 된다.
	 * @param file	값이 담긴(또는 {@code path} 요청 시 값을 기록할) 파일. {@code value}가 있고 {@code file}이
	 * 				{@code null}이면 {@code path} 요청 시 임시 파일을 만든다.
	 * @param deleteOnClose	{@code true}이면 {@link #close()}에서 {@code file}을 삭제한다. 임시 파일은 항상 삭제한다.
	 * @throws IllegalArgumentException	{@code name}이 {@code null}이거나, {@code value}와 {@code file}이 모두
	 * 									{@code null}인 경우.
	 */
	protected CommandVariable(String name, @Nullable String value, @Nullable File file, boolean deleteOnClose) {
		Preconditions.checkNotNullArgument(name, "name is null");
		Preconditions.checkArgument(value != null || file != null, "either value or file must be given: name=%s", name);

		m_name = name;
		m_value = value;
		m_file = file;
		m_deleteOnClose = deleteOnClose;
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
	 * 파일 모드이면 파일 내용을 읽어 둔 뒤, 변수가 파일을 소유하면 삭제한다. 이후 {@link #getValue()}는 읽어 둔
	 * 값을 반환한다.
	 * <p>
	 * 파일을 읽지 못해도(예: 명령이 결과 파일을 만들지 않음) 예외를 던지지 않고 진행한다. 파일이 없는 변수(값만으로
	 * 생성되고 {@code path}로 사용되지 않은 변수)는 삭제할 파일이 없다. {@link #inputFile(String, File)}로 만든 변수의
	 * 파일은 삭제하지 않는다. 중복 호출은 안전하다.
	 */
	@Override
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
		if ( m_file != null && m_deleteOnClose ) {
			tryDeleteFile();
		}
	}
	
	/**
	 * 주어진 modifier에 따른 치환 결과를 반환한다.
	 *
	 * @param mod	modifier ({@code name}, {@code value}, {@code path}).
	 * @return	치환 결과.
	 * @throws IllegalArgumentException	지원하지 않는 modifier인 경우.
	 * @throws IllegalStateException	{@link #close()}된 뒤 {@code path}를 요청한 경우. 닫힌 변수가 파일을 다시
	 * 									만들면 그 파일은 정리되지 않으므로 거부한다.
	 */
	public synchronized String getValueByModifier(@NotNull String mod) {
		Preconditions.checkNotNullArgument(mod, "modifier must be non-null");
		
		switch ( mod ) {
			case "name":
				return getName();
			case "value":
				return getValue();
			case "path":
				Preconditions.checkState(!m_closed, "variable is already closed: name=%s", m_name);
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
					m_deleteOnClose = true;
				}
				// 파일이 이미 있어도 값으로 덮어쓴다. (예전에는 기존 파일 내용이 값 대신 쓰여 값이 조용히 무시되었다.)
				IOUtils.toFile(m_value, StandardCharsets.UTF_8, m_file);
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