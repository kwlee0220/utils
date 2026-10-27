package utils.async.command;

import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.Nullable;


/**
 * {@link CommandExecution}의 sub-process가 {@code 0}이 아닌 종료 코드로 끝났음을 나타내는 예외.
 * <p>
 * 종료 코드({@link #getExitCode()})와, 출력이 파일로 redirect된 경우 그 파일의 마지막 부분
 * ({@link #getOutputTail()})을 제공한다. 출력 끝부분에는 비밀값이 포함될 수 있으므로 예외 메시지에는
 * 넣지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandFailedException extends ExecutionException {
	private static final long serialVersionUID = 1L;

	private final int m_exitCode;
	private final @Nullable String m_outputTail;

	/**
	 * 예외 객체를 생성한다.
	 *
	 * @param exitCode		sub-process의 종료 코드.
	 * @param outputTail	stderr(또는 stdout) 파일의 마지막 부분. 출력이 파일로 redirect되지 않았거나
	 * 						읽지 못한 경우 {@code null}.
	 */
	public CommandFailedException(int exitCode, @Nullable String outputTail) {
		super("Process failed: retCode=" + exitCode);

		m_exitCode = exitCode;
		m_outputTail = outputTail;
	}

	/**
	 * Sub-process의 종료 코드를 반환한다.
	 *
	 * @return	종료 코드 ({@code 0}이 아님).
	 */
	public int getExitCode() {
		return m_exitCode;
	}

	/**
	 * Sub-process 출력 파일의 마지막 부분(최대 4KB, 앞뒤 공백 제거)을 반환한다.
	 * <p>
	 * stderr가 파일로 redirect되었으면 그 파일, 아니면 stdout 파일(stderr가 합쳐졌을 수 있음)의 끝부분이다.
	 *
	 * @return	출력 끝부분. 출력이 파일로 redirect되지 않았거나 읽지 못한 경우 {@code null}.
	 */
	public @Nullable String getOutputTail() {
		return m_outputTail;
	}
}
