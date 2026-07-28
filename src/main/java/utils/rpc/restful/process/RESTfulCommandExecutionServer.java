package utils.rpc.restful.process;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import lombok.experimental.Delegate;

import utils.Preconditions;
import utils.async.StartableExecution;
import utils.async.command.CommandExecution;
import utils.async.command.CommandVariable;
import utils.func.FOption;
import utils.func.Unchecked;
import utils.io.FileUtils;
import utils.json.JacksonUtils;
import utils.rpc.restful.AsyncRpcSession;
import utils.rpc.restful.RESTfulAsyncRpcServer;
import utils.rpc.restful.RpcRequestMessage;
import utils.stream.FStream;

/**
 * 외부 명령(command) 실행을 비동기 연산으로 제공하는 {@link RESTfulAsyncRpcServer} 구현체.
 * <p>
 * {@link CommandExecutionDescriptor}(명령줄, 작업 디렉토리, 타임아웃, 출력 변수 등)를 읽어, 요청마다
 * {@link CommandExecution}을 구성한다. 연산 입력은 {@link CommandVariable}로 등록되어 명령줄 변수 치환
 * ({@code ${name:path}} 등)을 통해 명령에 전달되고, 출력 변수는 명령이 기록한 파일을 JSON으로 읽어 연산
 * 출력으로 반환한다.
 * <p>
 * 요청마다 작업 디렉토리 아래에 {@code session-<UUID>-<seq>} 형태의 세션 디렉토리를 만들어 입력·출력
 * 파일을 격리하며, 세션 종료 시 {@link #releaseSession}에서 해당 디렉토리를 삭제한다. 세션 endpoint는
 * {@code <baseUrl>/sessions/<seq>} 형태로 발급된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class RESTfulCommandExecutionServer extends RESTfulAsyncRpcServer {
	private static final Logger s_logger = LoggerFactory.getLogger(RESTfulCommandExecutionServer.class);
	private static final String SESSION_PREFIX = "/api/v1/sessions/";
	private static final AtomicLong s_sessionIdGen = new AtomicLong(0);
	
	private final CommandExecutionDescriptor m_opDesc;
	private final File m_workingDir;
	private final File m_envFile;
	private final JsonMapper m_jsonMapper = JacksonUtils.MAPPER;
	private final CommandVariableSerDe m_serde;
	private final String m_sessionPrefix;
	
	/**
	 * 세션 식별자 및 세션 디렉토리를 {@link CommandExecution}에 결합한 연산 단위.
	 * <p>
	 * 모든 {@link StartableExecution} 메소드는 {@link Delegate}를 통해 내부 {@link CommandExecution}에
	 * 위임된다.
	 */
	private class CommandExecutionOperation implements StartableExecution<Void>, AutoCloseable {
		private final String m_sessionId;
		private final File m_sessionDir;
		@Delegate private final CommandExecution m_execution;

		private CommandExecutionOperation(String sessionId, File sessionDir, CommandExecution execution) {
			m_sessionId = sessionId;
			m_sessionDir = sessionDir;
			m_execution = execution;
		}
	}

	
	/**
	 * 명령 실행 연산 서버를 생성한다.
	 * <p>
	 * 작업 디렉토리가 디스크립터에 지정되지 않은 경우 디스크립터 파일이 위치한 디렉토리를 사용한다.
	 *
	 * @param opDescFile	명령 실행 디스크립터({@link CommandExecutionDescriptor}) 파일.
	 * @param serde			연산 입출력 값과 명령 변수 사이의 변환을 담당하는 SerDe.
	 * @throws IllegalArgumentException	{@code opDescFile} 또는 {@code serde}가 {@code null}인 경우.
	 * @throws FileNotFoundException	{@code opDescFile}이 존재하지 않거나 일반 파일이 아닌 경우.
	 * @throws IOException	디스크립터 파일을 읽는 중 입출력 오류가 발생한 경우.
	 */
	public RESTfulCommandExecutionServer(File opDescFile, CommandVariableSerDe serde) throws IOException {
		Preconditions.checkNotNullArgument(opDescFile, "opDescFile is null");
		Preconditions.checkNotNullArgument(serde, "serde is null");
		if ( !opDescFile.isFile() ) {
			throw new FileNotFoundException("descriptor file not found: " + opDescFile);
		}

		m_opDesc = CommandExecutionDescriptor.load(opDescFile, m_jsonMapper);
		
		// 작업 디렉토리가 지정되지 않은 경우는 연산 기술자 파일이 속한 디렉토리로 설정한다.
		m_workingDir = FOption.getOrElse(m_opDesc.getWorkingDirectory(), opDescFile.getParentFile());
		m_envFile = new File(m_workingDir, "env.file");
		m_serde = serde;
		
		m_sessionPrefix = "session-" + UUID.randomUUID().toString();
		setLogger(s_logger);
	}
	
	@Override
	protected StartableExecution<Void> createExecution(RpcRequestMessage reqMsg) throws IOException {
		String sessionId = String.valueOf(s_sessionIdGen.incrementAndGet());
		
		File sessionDir = new File(m_workingDir, m_sessionPrefix + "-" + sessionId);
		if ( !sessionDir.mkdirs() ) {
			throw new IOException("failed to create CommandExecution session-dir: " + sessionDir.getAbsolutePath());
		}
		
		// 연산 구성 도중 IOException뿐 아니라 build()의 IllegalArgumentException 등 어떤 예외가 나더라도
		// 생성한 세션 디렉토리가 leak되지 않도록 success 플래그 기반으로 정리한다.
		boolean success = false;
		try {
			CommandExecution.Builder builder = CommandExecution.builder()
																.addCommand(m_opDesc.getCommandLine())
																.workingDirectory(m_workingDir)
																.environmentFile(m_envFile)
																.timeout(m_opDesc.getTimeout());
			for ( var ent: reqMsg.getInputs().entrySet() ) {
				CommandVariable var = m_serde.deserialize(ent.getKey(), sessionDir, ent.getValue());
				builder.addVariable(var);
			}
			if ( reqMsg.getOutputs() != null ) {
				Map<String,JsonNode> outputVars = reqMsg.getOutputs();
				for ( String outVarName: m_opDesc.getOutputVariables() ) {
					JsonNode outVarNode = outputVars.get(outVarName);
					if ( outVarNode != null) {
						CommandVariable var = m_serde.deserialize(outVarName, sessionDir, outVarNode);
						builder.addVariable(var);
					}
				}
			}
			else {
				// 요청 메시지에 출력 변수가 지정되지 않은 경우,
				// 디스크립터에 정의된 출력 변수만을 사용한다.
				for ( String varId: m_opDesc.getOutputVariables() ) {
					// 만일 입력 변수 이름과 동일한 출력 변수를 사용하는 경우에는
					// 출력 변수로 다시 등록하지 않는다.
					if ( !builder.hasVariable(varId) ) {
						CommandVariable var = m_serde.deserialize(varId, sessionDir, new TextNode(""));
						builder.addVariable(var);
					}
				}
			}

			// stdout/stderr redirection
			// 로그는 공용 작업 디렉토리에 남겨 세션 디렉토리 삭제 후에도 보존하며, 연산 간 덮어쓰기를
			// 막기 위해 append 모드로 기록한다.
			builder.redirectErrorStream();
			builder.redirectStdoutToFile(new File("output.log"), true);

			CommandExecution cmdExec = builder.build();
			CommandExecutionOperation op = new CommandExecutionOperation(sessionId, sessionDir, cmdExec);
			success = true;
			return op;
		}
		finally {
			if ( !success ) {
				Unchecked.runOrIgnore(() -> FileUtils.deleteDirectory(sessionDir));
			}
		}
	}

	@Override
	protected Duration getStartTimeout() {
		return m_opDesc.getStartTimeout();
	}

	@Override
	protected Duration getSessionRetainTimeout() {
		return m_opDesc.getSessionRetainTimeout();
	}

	@Override
	protected AsyncRpcSession allocateSession(StartableExecution<?> execution) {
		Preconditions.checkArgument(execution instanceof CommandExecutionOperation,
									"execution is not CommandExecutionOperation: %s", execution);
		CommandExecutionOperation cmdExecOp = (CommandExecutionOperation)execution;
		
		String sessionEndpoint = SESSION_PREFIX + cmdExecOp.m_sessionId;
		AsyncRpcSession session = new AsyncRpcSession(sessionEndpoint, execution);
		
		return session;
	}

	@Override
	protected void releaseSession(AsyncRpcSession session) {
		// CommandExecutionOperation이 AutoCloseable이므로 session.close()가 내부 execution을 close한다.
		session.close();

		CommandExecutionOperation cmdExecOp = (CommandExecutionOperation)session.getExecution();
		Unchecked.runOrIgnore(() -> FileUtils.deleteDirectory(cmdExecOp.m_sessionDir));
	}

	@Override
	protected Map<String, JsonNode> getExecutionOutput(StartableExecution<?> execution) {
		Preconditions.checkArgument(execution instanceof CommandExecutionOperation,
									"execution is not CommandExecutionOperation: %s", execution);
		
		CommandExecutionOperation cmdExec = (CommandExecutionOperation)execution;
		Map<String,CommandVariable> cmdVars = cmdExec.getVariableMap();
		
		return FStream.from(m_opDesc.getOutputVariables())
						.lookup(cmdVars)
						.mapOrThrow(kv -> m_serde.serialize(kv.value()))
						.toKeyValueStream(kv -> kv)
						.toMap();
	}
}
