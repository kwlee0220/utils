package utils;

import java.io.IOException;

import utils.io.IOUtils;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;


/**
 * 상위 명령({@link ParentCommand})에 딸린 picocli 하위 명령의 기반 클래스.
 * <p>
 * {@link #run()}은 {@link #configureLog4j()}로 로그를 설정한 뒤, 이 명령 아래에 다시 하위 명령이
 * 지정되었으면 그 명령을 실행하고, 아니면 상위 명령의 {@link PicocliCommand#getInitialContext()}로 얻은
 * 컨텍스트로 {@link #run(Object)}을 호출한다. 실행이 끝나면 컨텍스트가 {@link AutoCloseable}인 경우 닫는다.
 * {@code --help}/{@code -h} 옵션({@link UsageHelp})이 포함된다.
 * <p>
 * <b>주의:</b> 실행 중 발생한 예외는 표준 오류에 {@code "failed: <예외>"}로 출력되고(컨텍스트 생성 또는
 * {@link #run(Object)} 실패 시에는 사용법도 표준 출력에 출력) 호출자에게 전달되지 않는다.
 *
 * @param <T>	초기 컨텍스트 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PicocliSubCommand<T> implements PicocliCommand<T> {
	@ParentCommand private PicocliCommand<T> m_parent;
	@Spec private CommandSpec m_spec;
	@Mixin private UsageHelp m_help;
	
	/**
	 * 하위 명령을 생성한다. picocli가 리플렉션으로 호출한다.
	 */
	public PicocliSubCommand() { }

	/**
	 * 하위 명령의 실제 작업을 수행한다.
	 *
	 * @param initialContext	상위 명령이 제공한 초기 컨텍스트.
	 * @throws Exception	작업이 실패한 경우.
	 */
	abstract protected void run(T initialContext) throws Exception;
	
	@Override
	public T getInitialContext() throws Exception {
		return m_parent.getInitialContext();
	}
	
	/**
	 * picocli가 주입한 상위 명령을 반환한다.
	 *
	 * @return	상위 명령. 명령줄 파싱 전에는 {@code null}.
	 */
	public PicocliCommand<T> getParent() {
		return m_parent;
	}
	
	/**
	 * 이 하위 명령의 {@link CommandLine}을 반환한다.
	 *
	 * @return	이 명령의 {@link CommandLine}.
	 */
	public CommandLine getCommandLine() {
		return m_spec.commandLine();
	}
	
	@Override
	public void run() {
		try {
			configureLog4j();
			
			ParseResult sub = m_spec.commandLine().getParseResult().subcommand();
			if ( sub != null ) {
				@SuppressWarnings("unchecked")
				PicocliCommand<T> subC = (PicocliCommand<T>)sub.commandSpec().userObject();
				subC.run();
			}
			else {
				T server = null;
				try {
					server = m_parent.getInitialContext();
					run(server);
				}
				catch ( Exception e ) {
					System.err.printf("failed: %s%n%n", e);
					
					m_spec.commandLine().usage(System.out, Ansi.OFF);
				}
				finally {
					IOUtils.close(server);
				}
			}
		}
		catch ( Exception e ) {
			System.err.printf("failed: %s%n%n", e);
		}
	}

	@Override
	public void configureLog4j() throws IOException {
		m_parent.configureLog4j();
	}
}