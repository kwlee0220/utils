package utils.async.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Keyed;
import utils.Utilities;
import utils.func.Lazy;
import utils.io.IOUtils;


/**
 * {@link CommandExecution}의 command line에서 치환되는 변수 단위.
 * <p>
 * 각 변수는 고유한 이름({@link #getName()})과 값({@link #getValue()})을 가지며, command line에
 * {@code ${name}} 또는 {@code ${name:modifier}} 형태로 등장하면 {@link #getValueByModifier(String)}의
 * 결과로 치환된다.
 *
 * <h3>Modifier</h3>
 * 기본 지원되는 modifier는 다음과 같다 (구현체별로 추가 가능):
 * <ul>
 *   <li>{@code name} — {@link #getName()} 결과 (modifier 생략 시 기본값)</li>
 *   <li>{@code value} — {@link #getValue()} 결과</li>
 *   <li>{@code path} — ({@link FileVariable} 한정) 변수가 보유한 파일의 절대 경로</li>
 * </ul>
 * 미지원 modifier는 {@link IllegalArgumentException}을 던진다.
 *
 * <h3>Lifecycle</h3>
 * {@link #close()}가 호출되면 변수가 보유한 자원이 해제된다. 구현체에 따라 close 이후의
 * {@link #getValue()} / {@link #getValueByModifier(String)} 호출은 지원되지 않을 수 있다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CommandVariable extends Keyed<String> {
	/**
	 * Command variable 이름을 반환한다.
	 *
	 * @return	Command variable 이름
	 */
	public String getName();
	
	/**
	 * Command variable 값을 반환한다.
	 *
	 * @return	Command variable 값
	 */
	public String getValue();
	
	/**
	 * Command variable의 key를 반환한다. 기본 구현은 {@link #getName()}을 반환한다.
	 *
	 * @return Command variable key
	 */
	@Override
	public default String key() {
		return getName();
	}
	
	/**
	 * Command variable 동작을 위해 할당된 자원을 모두 반환한다.
	 * <p>
	 * 구현체에 따라 본 메소드 호출 이후에는 {@link #getValue()} 또는
	 * {@link #getValueByModifier(String)} 호출이 지원되지 않을 수 있다.
	 */
	public void close();
	
	/**
	 * 지정된 modifier에 해당하는 값을 반환한다.
	 *
	 * @param mod	modifier. 본 default 구현은 "name"/"value"만 처리하며,
	 * 				구현체가 override하여 추가 modifier를 지원할 수 있다
	 * 				(예: {@link FileVariable}의 "path").
	 * @return	modifier에 해당하는 값.
	 * @throws IllegalArgumentException 지원되지 않는 modifier가 전달된 경우.
	 */
	public default String getValueByModifier(@NotNull String mod) {
		Utilities.checkNotNullArgument(mod, "mod must be non-null");
		
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
			Utilities.checkNotNullArgument(name, "name");
			Utilities.checkNotNullArgument(value, "value");

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

		@Override
		public String toString() {
			return String.format("StringVariable[%s]: %s", m_name, m_value);
		}
	}
	
	/**
	 * File 타입의 Command variable 구현.
	 * <p>
	 * <b>주의:</b> 이 클래스는 생성자에 전달된 파일의 소유권을 가져가는 것으로 간주한다.
	 * {@link #close()} 호출 시 해당 파일은 삭제되므로, 외부에서 계속 보존되어야 하는 파일을
	 * 넘기면 안 된다. 일반적으로는 명령 실행을 위해 임시로 생성된 파일에만 사용한다.
	 * {@code close()} 이후에는 {@link #getValue()} 또는 {@link #getValueByModifier(String)} 호출을
	 * 지원하지 않는다.
	 *
	 * @author Kang-Woo Lee (ETRI)
	 */
	public static final class FileVariable implements CommandVariable {
		private static final Logger s_logger = LoggerFactory.getLogger(FileVariable.class);

		private final String m_name;
		private final File m_file;
		private final Lazy<String> m_cachedValue = Lazy.of(this::readFileContent);

		/**
		 * 주어진 이름과 파일로 {@code FileVariable}을 생성한다.
		 * 파일 소유권/lifecycle 계약은 클래스 Javadoc 참고.
		 *
		 * @param name	변수 이름.
		 * @param file	이 변수가 소유할 파일. {@code close()} 시 삭제된다.
		 */
		public FileVariable(String name, File file) {
			Utilities.checkNotNullArgument(name, "name");
			Utilities.checkNotNullArgument(file, "file");

			m_name = name;
			m_file = file;
		}

		@Override
		public String getName() {
			return m_name;
		}

		/**
		 * 파일의 내용을 문자열로 반환한다.
		 * <p>
		 * 첫 호출 시점에 한 번만 파일을 읽어 들이고, 이후 호출은 캐시된 값을 반환한다.
		 * 첫 read가 성공한 이후 객체 lifetime 동안 캐시된 값이 그대로 반환되므로 외부 파일 변경은
		 * 반영되지 않는다. 첫 read 전에 파일 변경/삭제가 발생하면 그 영향(예: I/O 실패)이 그대로 노출된다.
		 *
		 * @throws RuntimeException 파일 읽기에 실패한 경우.
		 */
		@Override
		public String getValue() {
			return m_cachedValue.get();
		}

		private String readFileContent() {
			try {
				return IOUtils.toString(m_file, StandardCharsets.UTF_8);
			}
			catch ( IOException e ) {
				throw new RuntimeException("Failed to read FileVariable: name=" + m_name
											+ ", path=" + m_file.getAbsolutePath(), e);
			}
		}
		
		/**
		 * 이 변수가 보유한 파일을 반환한다.
		 * <p>
		 * 반환된 {@link File}은 본 객체의 소유이므로 외부에서 삭제·이동·내용 변경 등의 mutation을
		 * 수행해서는 안 된다. 파일 정리는 {@link #close()}를 통해서만 수행해야 한다.
		 *
		 * @return 본 변수가 보유한 파일.
		 */
		public File getFile() {
			return m_file;
		}

		/**
		 * 이 변수가 소유한 파일을 삭제한다.
		 * <p>
		 * 생성자에 전달된 파일은 이 객체의 소유이므로, {@code close()} 호출 시 시스템에서 제거된다.
		 */
		@Override
		public void close() {
			tryDeleteFile();
		}

		private void tryDeleteFile() {
			// delete()가 false를 반환하더라도 파일이 이미 사라진 상태라면(예: 이전 호출에서 삭제됨)
			// 정상으로 간주하고 warn을 남기지 않는다.
			if ( !m_file.delete() && m_file.exists() ) {
				s_logger.warn("failed to delete file: name={}, path={}", m_name, m_file.getAbsolutePath());
			}
		}

		@Override
		public String getValueByModifier(@NotNull String mod) {
			if ( "path".equals(mod) ) {
				return m_file.getAbsolutePath();
			}
			// "name"/"value" 등 공통 modifier 및 null 검사는 interface default에 위임한다.
			return CommandVariable.super.getValueByModifier(mod);
		}
		
		@Override
		public String toString() {
			return String.format("FileVariable[%s]: %s", m_name, m_file.getAbsolutePath());
		}
	}
}