package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import utils.KeyValue;
import utils.Preconditions;
import utils.async.command.CommandVariable;

/**
 * 명령 실행 변수 값을 JSON 텍스트 그대로 파일에 담아 주고받는 기본 {@link CommandVariableSerDe} 구현체.
 * <p>
 * 역직렬화 시 입력 JSON 노드를 JSON 문자열로 직렬화하여 변수 디렉토리 아래 변수 이름과 같은 파일에
 * 매핑한 {@link CommandVariable}을 만들고, 직렬화 시 변수 값(파일 내용)을 JSON 트리로 다시 파싱한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultCommandVariableSerDe implements CommandVariableSerDe {
	private final JsonMapper m_jsonMapper;

	/**
	 * SerDe를 생성한다.
	 *
	 * @param jsonMapper	JSON 직렬화/역직렬화에 사용할 {@link JsonMapper}.
	 */
	public DefaultCommandVariableSerDe(JsonMapper jsonMapper) {
		m_jsonMapper = jsonMapper;
	}

	/**
	 * 명령 변수 값 JSON 노드를 JSON 문자열로 직렬화하여 변수 디렉토리 아래 {@code id} 파일에 매핑한
	 * {@link CommandVariable}을 생성한다.
	 *
	 * @throws IllegalArgumentException	{@code id}/{@code cvDir}/{@code jnode}가 {@code null}이거나
	 * 									{@code cvDir}가 디렉토리가 아닌 경우.
	 */
	@Override
	public CommandVariable deserialize(String id, File cvDir, @NotNull JsonNode jnode) throws IOException {
		Preconditions.checkNotNullArgument(id, "id is null");
		Preconditions.checkNotNullArgument(cvDir, "cvDir is null");
		Preconditions.checkArgument(cvDir.isDirectory(), "cvDir is not a directory: " + cvDir.getAbsolutePath());
		Preconditions.checkNotNullArgument(jnode, "jnode is null");

		File file = new File(cvDir, id);
		try {
			String argValue = m_jsonMapper.writeValueAsString(jnode);

			return new CommandVariable(id, argValue, file);
		}
		catch ( IOException e ) {
			throw new IOException("Failed to write value to file: name=" + id
										+ ", path=" + file.getAbsolutePath(), e);
		}
	}

	/**
	 * 명령 변수의 값(JSON 텍스트)을 JSON 트리로 파싱하여 변수 이름과 함께 반환한다.
	 * 값이 {@code null}이면 JSON 노드도 {@code null}이 된다.
	 */
	@Override
	public KeyValue<String,JsonNode> serialize(CommandVariable var) throws IOException {
		String value = var.getValue();
		JsonNode jnode = (value != null) ? m_jsonMapper.readTree(value) : null;
		return KeyValue.of(var.getName(), jnode);
	}

}
