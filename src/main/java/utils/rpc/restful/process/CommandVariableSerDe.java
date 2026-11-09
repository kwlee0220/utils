package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.fasterxml.jackson.databind.JsonNode;

import utils.KeyValue;
import utils.async.command.CommandVariable;

/**
 * 연산 입출력 값(JSON)과 명령 실행 변수({@link CommandVariable}) 사이의 변환을 담당하는 SerDe.
 * <p>
 * {@link RESTfulCommandExecutionServer}가 연산 입력 JSON을 명령에 전달할 {@link CommandVariable}로
 * 풀어내거나({@link #deserialize}), 출력 변수를 준비하거나({@link #deserializeOutput}), 명령이 출력 파일에
 * 기록한 변수 값을 다시 JSON으로 거두어들일 때({@link #serialize}) 사용한다. 변수 값을 어떤 형식의 파일로
 * 표현할지는 구현체가 결정한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface CommandVariableSerDe {
	/**
	 * 명령 변수의 이름과 JSON 노드로부터 {@link CommandVariable}을 역직렬화한다.
	 *
	 * @param id	명령 변수 이름.
	 * @param cvDir	명령 변수에 해당하는 파일이 위치할 디렉토리.
	 * @param jnode	명령 변수 값으로 직렬화된 JSON 노드. ({@code null} 불가)
	 * @return	역직렬화된 {@link CommandVariable}.
	 * @throws IOException	JSON 직렬화/역직렬화 중 오류가 발생한 경우.
	 */
	public CommandVariable deserialize(String id, File cvDir, @NotNull JsonNode jnode) throws IOException;
	
	/**
	 * 출력 변수의 이름과 요청 메시지에 지정된 값(출력 위치 등)으로부터 {@link CommandVariable}을 만든다.
	 * <p>
	 * 출력 변수는 명령이 결과를 기록할 자리이므로, 입력과 달리 지정된 값을 미리 읽어 파일에 채울 필요가 없는
	 * 경우가 있다(예: 원격 파일 요소의 참조). 그런 구분이 필요한 구현체가 재정의한다. 기본 구현은
	 * {@link #deserialize}와 같다.
	 *
	 * @param id	출력 변수 이름.
	 * @param cvDir	명령 변수에 해당하는 파일이 위치할 디렉토리.
	 * @param jnode	요청 메시지에 지정된 출력 변수 값. 지정되지 않았으면 빈 문자열 노드. ({@code null} 불가)
	 * @return	출력 변수에 해당하는 {@link CommandVariable}.
	 * @throws IOException	JSON 직렬화/역직렬화 중 오류가 발생한 경우.
	 */
	public default CommandVariable deserializeOutput(String id, File cvDir, @NotNull JsonNode jnode)
		throws IOException {
		return deserialize(id, cvDir, jnode);
	}
	
	/**
	 * {@link CommandVariable}을 명령 변수 이름과 JSON 노드로 직렬화한다.
	 *
	 * @param var	직렬화할 {@link CommandVariable}.
	 * @return	직렬화된 명령 변수 이름과 JSON 노드.
	 * @throws IOException	JSON 직렬화/역직렬화 중 오류가 발생한 경우.
	 */
	public KeyValue<String,JsonNode> serialize(CommandVariable var) throws IOException;
}
