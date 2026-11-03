package utils;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.Nullable;

import utils.io.FileUtils;
import utils.func.Nullables;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;


/**
 * 홈 디렉토리를 기준으로 동작하는 picocli 명령의 기반 클래스.
 * <p>
 * 홈 디렉토리는 {@code --home} 옵션, 생성자에 준 환경 변수, 현재 작업 디렉토리 순으로 정해진다.
 * 하위 클래스는 {@link #run(Path)}을 구현하며, 여기서 던진 예외는 {@link #run()}이 받아
 * 표준 오류에 출력한 뒤 {@code System.exit(-1)}로 프로세스를 끝낸다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class HomeDirPicocliCommand implements Runnable, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(HomeDirPicocliCommand.class);
	
	/** picocli가 주입하는 이 명령의 명세. */
	@Spec protected CommandSpec m_spec;
	@Mixin private UsageHelp m_help;
	
	private final @Nullable String m_homeDirEnvVarName;
	private Path m_homeDir = null;
	private Logger m_logger;
	
	/**
	 * 명령의 실제 작업을 수행한다. {@link #run()}이 홈 디렉토리를 정한 뒤 호출한다.
	 *
	 * @param homeDir	홈 디렉토리 ({@link #getHomeDir()}의 결과).
	 * @throws Exception	작업 중 오류가 발생한 경우. {@link #run()}이 받아 프로세스를 종료시킨다.
	 */
	protected abstract void run(Path homeDir) throws Exception;
	
	/**
	 * 홈 디렉토리 환경 변수 없이 명령 객체를 생성한다.
	 * {@code --home} 옵션이 없으면 현재 작업 디렉토리가 홈 디렉토리가 된다.
	 */
	protected HomeDirPicocliCommand() {
		m_homeDirEnvVarName = null;
	}
	
	/**
	 * 홈 디렉토리를 지정하는 환경 변수 이름을 주어 명령 객체를 생성한다.
	 *
	 * @param environVarName	{@code --home} 옵션이 없을 때 홈 디렉토리 경로를 읽을 환경 변수 이름.
	 * 			{@code null}이면 환경 변수를 보지 않는다.
	 */
	protected HomeDirPicocliCommand(@Nullable String environVarName) {
		m_homeDirEnvVarName = environVarName;
	}
	
	@Override
	public void run() {
		try {
			if ( getLogger().isInfoEnabled() ) {
				getLogger().info("use home.dir={}", getHomeDir());
			}
			
			run(getHomeDir());
		}
		catch ( Exception e ) {
			System.err.printf("failed: %s%n", e);
			System.exit(-1);
		}
	}
	
	/**
	 * 홈 디렉토리를 반환한다.
	 * <p>
	 * {@link #setHomeDir(Path)}({@code --home} 옵션)로 지정된 값이 있으면 그것을, 없으면 생성자에 준 환경 변수의
	 * 값을, 그것도 없으면 현재 작업 디렉토리({@code user.dir})를 쓴다. 처음 정해진 값은 저장되어 이후 호출에서
	 * 그대로 반환된다. 경로가 실제로 존재하는지는 검사하지 않는다.
	 *
	 * @return	홈 디렉토리 경로.
	 */
	public Path getHomeDir() {
		// 명시적으로 홈 디렉토리가 설정되지 않은 경우에는 환경 변수에 지정된 홈 디렉토리로 지정한다.
		if ( m_homeDir == null ) {
			if ( m_homeDirEnvVarName != null ) {
				String path = System.getenv(m_homeDirEnvVarName);
				if ( path != null ) {
					m_homeDir = Paths.get(path);
					if ( getLogger().isDebugEnabled() ) {
						getLogger().debug("set homeDir '{}' from the environment variable '{}'",
											m_homeDir, m_homeDirEnvVarName);
					}
				}
			}
		}
		// 홈 디렉토리가 지정되지 않은 경우에는 사용자 홈 디렉토리를 홈 디렉토리로 설정한다.
		if ( m_homeDir == null ) {
//			m_homeDir = FileUtils.getUserHomeDir().toPath();
			m_homeDir = FileUtils.getCurrentWorkingDirectory().toPath();
			getLogger().info("set homeDir '{}", m_homeDir);
		}
		
		return m_homeDir;
	}

	/**
	 * 홈 디렉토리를 지정한다. {@code --home} 옵션으로 설정된다.
	 *
	 * @param path	홈 디렉토리 경로. {@code null}이면 다음 {@link #getHomeDir()} 호출 때 다시 정한다.
	 */
	@Option(names={"--home"}, paramLabel="path", description={"Home directory"})
	public void setHomeDir(Path path) {
		m_homeDir = path;
	}

	@Override
	public Logger getLogger() {
		return Nullables.orElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	/**
	 * 상대 경로를 홈 디렉토리 기준의 경로로 바꾼다. 절대 경로는 그대로 반환한다.
	 * 홈 디렉토리 자체가 상대 경로이면 결과도 상대 경로일 수 있다.
	 *
	 * @param path	변환할 경로.
	 * @return	홈 디렉토리 기준으로 해석하고 정규화한 경로, 또는 {@code path}가 절대 경로이면 {@code path} 그대로.
	 */
	public Path toAbsolutePath(Path path) {
		if ( !path.isAbsolute() ) {
			return getHomeDir().resolve(path).normalize();
		}
		else {
			return path;
		}
	}
	
	/**
	 * 경로 문자열을 {@link #toAbsolutePath(Path)}와 같은 방식으로 변환한다.
	 *
	 * @param path	변환할 경로 문자열.
	 * @return	홈 디렉토리 기준으로 해석한 경로.
	 * @throws java.nio.file.InvalidPathException	{@code path}를 경로로 바꿀 수 없는 경우.
	 */
	public Path toAbsolutePath(String path) {
		return toAbsolutePath(Paths.get(path));
	}
}
