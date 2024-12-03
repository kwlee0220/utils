package utils;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.FOption;
import utils.io.FileUtils;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class HomeDirPicocliCommand implements Runnable, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(HomeDirPicocliCommand.class);
	
	@Spec protected CommandSpec m_spec;
	@Mixin private UsageHelp m_help;
	
	@Nullable private final String m_homeDirEnvVarName;
	private Path m_homeDir = null;
	private Logger m_logger;
	
	protected abstract void run(Path homeDir) throws Exception;
	
	protected HomeDirPicocliCommand() {
		m_homeDirEnvVarName = null;
	}
	
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
			m_homeDir = FileUtils.getUserHomeDir().toPath();
			if ( getLogger().isDebugEnabled() ) {
				getLogger().debug("set homeDir '{}' from the user homeDir", m_homeDir);
			}
		}
		
		return m_homeDir;
	}

	@Option(names={"--home"}, paramLabel="path", description={"Home directory"})
	public void setHomeDir(Path path) {
		m_homeDir = path;
	}

	@Override
	public Logger getLogger() {
		return FOption.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
	
	public Path toAbsolutePath(Path path) {
		if ( !path.isAbsolute() ) {
			return getHomeDir().resolve(path).normalize();
		}
		else {
			return path;
		}
	}
	
	public Path toAbsolutePath(String path) {
		return toAbsolutePath(Paths.get(path));
	}

	protected static final void runCommand(HomeDirPicocliCommand cmd, String[] args) throws Exception {
		CommandLine commandLine = new CommandLine(cmd)
										.setUsageHelpWidth(100)
										.setCaseInsensitiveEnumValuesAllowed(true);
		try {
			commandLine.parse(args);

			if ( commandLine.isUsageHelpRequested() ) {
				commandLine.usage(System.out, Ansi.OFF);
			}
			else {
				try {
					cmd.run();
				}
				catch ( Exception e ) {
					System.err.println(e);
				}
			}
		}
		catch ( Throwable e ) {
			System.err.println(e);
			commandLine.usage(System.out, Ansi.OFF);
		}
	}
}