package utils.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import utils.Preconditions;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JacksonUtils {
	/**
	 * 라이브러리 전체가 함께 쓰는 공유 매퍼. 설정은 {@link #newJsonMapper(boolean)}와 같다.
	 * <p>
	 * <b>설정을 바꾸지 않는다.</b> {@code configure}·{@code enable}·{@code registerModule} 등으로 바꾸면 이 매퍼를 쓰는
	 * JVM 안의 모든 코드에 영향이 간다. 설정이 다른 매퍼가 필요하면 {@link #newJsonMapper(boolean)}로 새로 만들어
	 * 바꾼다. 읽기·쓰기만 하는 용도에는 {@link JsonMapper#reader()}/{@link JsonMapper#writer()}로 얻는
	 * 불변 객체를 써도 된다.
	 */
	public static final JsonMapper MAPPER = newJsonMapper(false);

	/**
	 * 다형 JSON에서 구체 타입 이름을 담는 필드의 표준 이름. {@link MultiTypeJsonNodeDeserializer}의 기본값이다.
	 */
	public static final String TYPE_FIELD = "@type";

	private JacksonUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + JacksonUtils.class.getName());
	}
	
	/**
	 * 라이브러리 표준 설정의 새 {@link JsonMapper}를 만든다.
	 * <ul>
	 *   <li>{@code java.time} 지원: {@link JavaTimeModule}을 항상 등록한다. 여기에 더해
	 *       {@link JsonMapper.Builder#findAndAddModules()}로 클래스패스의 다른 모듈도 등록한다. 서비스 목록
	 *       ({@code META-INF/services})이 빠진 fat jar에서도 {@code java.time}을 다룰 수 있도록 명시 등록을
	 *       함께 둔다(같은 모듈의 중복 등록은 Jackson이 무시한다).</li>
	 *   <li>날짜·시각({@code Instant}, {@code LocalDateTime} 등)은 숫자 timestamp가 아니라 ISO-8601 문자열로 쓴다
	 *       ({@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} 끔). 읽을 때는 두 형식을 모두 받는다.
	 *       {@code Duration}은 여전히 숫자(초)로 쓴다.</li>
	 * </ul>
	 *
	 * @param prettyPrint	{@code true}이면 들여쓰기한 출력을 만든다.
	 * @return	새 매퍼.
	 */
	public static JsonMapper newJsonMapper(boolean prettyPrint) {
		JsonMapper.Builder builder = JsonMapper.builder()
												.addModule(new JavaTimeModule())
												.findAndAddModules()
												.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		if ( prettyPrint ) {
			builder = builder.enable(SerializationFeature.INDENT_OUTPUT);
		}
		return builder.build();
	}
	
	/*
	 * 필드 조회 메소드는 모두 같은 규칙을 따른다(타입 X: 없음(JsonNode)·String·Text·Boolean·Int·Long·Double·Array).
	 * String은 스칼라 값을 텍스트로 바꿔 받고(설정 파일 호환), Text는 JSON 문자열만 받는다(식별자 등).
	 *
	 *   getXField(node, name)                    필드 없음·JSON null → 예외,     타입 불일치 → 예외
	 *   getXFieldOrNull(node, name)              필드 없음·JSON null → null,     타입 불일치 → 예외
	 *   getXFieldOrDefault(node, name, default)  필드 없음·JSON null → default,  타입 불일치 → 예외
	 *
	 * 예외는 모두 JacksonDeserializationException(IOException의 하위 타입)이다.
	 *
	 * 필드를 가진 노드(부모)가 JSON 객체가 아니면(JSON null 포함) 세 형태 모두 예외를 던진다. 잘못된 구조를
	 * 기본값으로 조용히 받아들이지 않기 위해서다.
	 * 단, getFieldOrNull은 이 검사를 하지 않는 저수준 조회로 남긴다.
	 */

	/**
	 * 필드를 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * <p>
	 * 다른 조회 메소드와 달리 {@code node}가 JSON 객체인지 확인하지 않는다. 배열·스칼라·JSON {@code null}
	 * 노드이면 필드가 없는 것으로 보고 {@code null}을 돌려준다. 구조가 잘못된 입력을 거부해야 하면
	 * {@link #getField(JsonNode, String)}나 타입별 조회 메소드를 쓴다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 */
	@Nullable
	public static JsonNode getFieldOrNull(JsonNode node, String fieldName) {
		Preconditions.checkNotNullArgument(node, "node is null");
		
		JsonNode field = node.get(fieldName);
		if ( field == null || field.isNull()  ) {
			return null;
		}
		else {
			return field;
		}
	}
	/**
	 * 필수 필드를 읽는다.
	 * <p>
	 * 필드가 없거나 JSON {@code null}이면 예외를 던진다. 예외 메시지에는 필드 이름과, 노드에 있는
	 * 필드 이름들(최대 {@value #MAX_FIELD_NAMES_IN_MESSAGE}개)만 넣고 값은 넣지 않는다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}인 경우.
	 */
	public static JsonNode getField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		if ( field == null ) {
			throw new JacksonDeserializationException("Field not found: field=" + fieldName + ", "
														+ describeFields(node));
		}
		return field;
	}

	/**
	 * 타입별 조회 메소드의 공통 진입점. {@code node}가 JSON 객체인지 확인한 뒤 필드를 읽는다.
	 */
	@Nullable
	private static JsonNode findField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		Preconditions.checkNotNullArgument(node, "node is null");
		if ( !node.isObject() ) {
			throw new JacksonDeserializationException(String.format("invalid node for field '%s': object expected, but %s",
																	fieldName, node.getNodeType().name().toLowerCase()));
		}
		return getFieldOrNull(node, fieldName);
	}

	private static final int MAX_FIELD_NAMES_IN_MESSAGE = 10;
	static String describeFields(JsonNode node) {
		if ( !node.isObject() ) {
			return "node=" + node.getNodeType().name().toLowerCase();
		}
		// 메시지에는 처음 몇 개만 쓰므로, 큰 객체에서도 그만큼만 모으고 나머지는 개수만 센다.
		List<String> names = new ArrayList<>(MAX_FIELD_NAMES_IN_MESSAGE);
		Iterator<String> iter = node.fieldNames();
		while ( iter.hasNext() && names.size() < MAX_FIELD_NAMES_IN_MESSAGE ) {
			names.add(iter.next());
		}
		int remaining = node.size() - names.size();
		String more = (remaining > 0) ? String.format(", ... (%d more)", remaining) : "";
		return "available fields=[" + String.join(", ", names) + more + "]";
	}

	// --- 문자열 ---

	/**
	 * 필수 문자열 필드 값을 읽는다.
	 * <p>
	 * 문자열뿐 아니라 숫자·불리언 같은 스칼라 값도 그 텍스트 표현으로 읽는다(예: {@code 19051} → {@code "19051"}).
	 * 객체나 배열이면 예외를 던진다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, 객체나 배열인 경우.
	 */
	public static String getStringField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asString(getField(node, fieldName), fieldName);
	}
	/**
	 * 문자열 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getStringField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 객체나 배열인 경우.
	 */
	@Nullable
	public static String getStringFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return getStringFieldOrDefault(node, fieldName, null);
	}
	/**
	 * 문자열 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getStringField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 객체나 배열인 경우.
	 */
	public static String getStringFieldOrDefault(JsonNode node, String fieldName, String defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asString(field, fieldName) : defaultValue;
	}

	// --- 엄격한 문자열(Text) ---

	/**
	 * 필수 문자열 필드 값을 읽는다. {@link #getStringField(JsonNode, String)}와 달리 <b>JSON 문자열만</b> 받는다.
	 * <p>
	 * 숫자·불리언·객체·배열이면 예외를 던진다. 식별자처럼 스키마상 문자열이어야 하는 필드에 쓴다
	 * ({@code "id": 1}을 {@code "1"}로 받아들이지 않는다).
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나,
	 * 										JSON 문자열이 아닌 경우.
	 */
	public static String getTextField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asText(getField(node, fieldName), fieldName);
	}
	/**
	 * 문자열 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getTextField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, JSON 문자열이 아닌 경우.
	 */
	@Nullable
	public static String getTextFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return getTextFieldOrDefault(node, fieldName, null);
	}
	/**
	 * 문자열 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getTextField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, JSON 문자열이 아닌 경우.
	 */
	public static String getTextFieldOrDefault(JsonNode node, String fieldName, String defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asText(field, fieldName) : defaultValue;
	}

	// --- 불리언 ---

	/**
	 * 필수 불리언 필드 값을 읽는다.
	 * <p>
	 * JSON 불리언과, 대소문자를 구분하지 않는 문자열 {@code "true"}/{@code "false"}를 받는다. 그 밖의 값이면
	 * 예외를 던진다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, 불리언으로 읽을 수 없는 경우.
	 */
	public static boolean getBooleanField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asBoolean(getField(node, fieldName), fieldName);
	}
	/**
	 * 불리언 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getBooleanField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 불리언으로 읽을 수 없는 값인 경우.
	 */
	@Nullable
	public static Boolean getBooleanFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asBoolean(field, fieldName) : null;
	}
	/**
	 * 불리언 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getBooleanField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 불리언으로 읽을 수 없는 값인 경우.
	 */
	public static boolean getBooleanFieldOrDefault(JsonNode node, String fieldName, boolean defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asBoolean(field, fieldName) : defaultValue;
	}

	// --- 정수(int) ---

	/**
	 * 필수 정수 필드 값을 읽는다.
	 * <p>
	 * {@code int} 범위의 JSON 정수만 받는다. 문자열, 소수, 범위를 넘는 정수이면 예외를 던진다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, {@code int}로 읽을 수 없는 경우.
	 */
	public static int getIntField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asInt(getField(node, fieldName), fieldName);
	}
	/**
	 * 정수 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getIntField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, {@code int}로 읽을 수 없는 값인 경우.
	 */
	@Nullable
	public static Integer getIntFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asInt(field, fieldName) : null;
	}
	/**
	 * 정수 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getIntField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, {@code int}로 읽을 수 없는 값인 경우.
	 */
	public static int getIntFieldOrDefault(JsonNode node, String fieldName, int defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asInt(field, fieldName) : defaultValue;
	}

	// --- 정수(long) ---

	/**
	 * 필수 {@code long} 필드 값을 읽는다. {@code long} 범위의 JSON 정수만 받는다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, {@code long}으로 읽을 수 없는 경우.
	 */
	public static long getLongField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asLong(getField(node, fieldName), fieldName);
	}
	/**
	 * {@code long} 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getLongField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, {@code long}으로 읽을 수 없는 값인 경우.
	 */
	@Nullable
	public static Long getLongFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asLong(field, fieldName) : null;
	}
	/**
	 * {@code long} 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getLongField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, {@code long}으로 읽을 수 없는 값인 경우.
	 */
	public static long getLongFieldOrDefault(JsonNode node, String fieldName, long defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asLong(field, fieldName) : defaultValue;
	}

	// --- 실수(double) ---

	/**
	 * 필수 {@code double} 필드 값을 읽는다. JSON 숫자(정수 포함)만 받고, 문자열이거나 {@code double} 범위를 넘어 무한대가 되는 값(예: {@code 1e400})이면 예외를 던진다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, 숫자가 아니거나 {@code double} 범위를 넘는 경우.
	 */
	public static double getDoubleField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asDouble(getField(node, fieldName), fieldName);
	}
	/**
	 * {@code double} 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 * 값의 규칙은 {@link #getDoubleField(JsonNode, String)}와 같다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	필드 값 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 숫자가 아니거나 {@code double} 범위를 넘는 경우.
	 */
	@Nullable
	public static Double getDoubleFieldOrNull(JsonNode node, String fieldName) throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asDouble(field, fieldName) : null;
	}
	/**
	 * {@code double} 필드 값을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code defaultValue}를 돌려준다.
	 * 값의 규칙은 {@link #getDoubleField(JsonNode, String)}와 같다.
	 *
	 * @param node			필드를 가진 노드.
	 * @param fieldName		필드 이름.
	 * @param defaultValue	필드가 없을 때 돌려줄 값.
	 * @return	필드 값.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 숫자가 아니거나 {@code double} 범위를 넘는 경우.
	 */
	public static double getDoubleFieldOrDefault(JsonNode node, String fieldName, double defaultValue)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asDouble(field, fieldName) : defaultValue;
	}

	// --- 배열 ---

	/**
	 * 필수 배열 필드의 원소들을 읽는다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	원소 목록(수정 불가).
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 없거나 JSON {@code null}이거나, 배열이 아닌 경우.
	 */
	public static List<JsonNode> getArrayField(JsonNode node, String fieldName) throws JacksonDeserializationException {
		return asElementList(getField(node, fieldName), fieldName);
	}

	/**
	 * 배열 필드의 원소들을 읽는다. 필드가 없거나 JSON {@code null}이면 {@code null}을 돌려준다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	원소 목록(수정 불가) 또는 {@code null}.
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 배열이 아닌 경우.
	 */
	@Nullable
	public static List<JsonNode> getArrayFieldOrNull(JsonNode node, String fieldName)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asElementList(field, fieldName) : null;
	}

	/**
	 * 배열 필드의 원소들을 읽는다. 필드가 없거나 JSON {@code null}이면 빈 목록을 돌려준다.
	 * <p>
	 * 다른 타입의 {@code OrDefault}와 달리 기본값을 받지 않고 항상 빈 목록을 쓴다.
	 *
	 * @param node		필드를 가진 노드.
	 * @param fieldName	필드 이름.
	 * @return	원소 목록(수정 불가).
	 * @throws JacksonDeserializationException	{@code node}가 JSON 객체가 아니거나, 필드가 배열이 아닌 경우.
	 */
	public static List<JsonNode> getArrayFieldOrEmpty(JsonNode node, String fieldName)
		throws JacksonDeserializationException {
		JsonNode field = findField(node, fieldName);
		return (field != null) ? asElementList(field, fieldName) : List.of();
	}

	private static String asString(JsonNode field, String fieldName) throws JacksonDeserializationException {
		if ( field.isContainerNode() ) {
			throw typeMismatch(fieldName, "string", field);
		}
		return field.asText();
	}

	private static String asText(JsonNode field, String fieldName) throws JacksonDeserializationException {
		if ( !field.isTextual() ) {
			throw typeMismatch(fieldName, "string", field);
		}
		return field.textValue();
	}

	// 수치·불리언 변환은 JsonScalars에 맡긴다(STRICT: JSON 타입 그대로, 불리언은 "true"/"false" 문자열도 받음).

	private static boolean asBoolean(JsonNode field, String fieldName) throws JacksonDeserializationException {
		try {
			return JsonScalars.toBoolean(field, JsonScalars.Policy.STRICT);
		}
		catch ( JacksonDeserializationException e ) {
			throw withField(fieldName, e);
		}
	}

	private static int asInt(JsonNode field, String fieldName) throws JacksonDeserializationException {
		try {
			return JsonScalars.toInt(field, JsonScalars.Policy.STRICT);
		}
		catch ( JacksonDeserializationException e ) {
			throw withField(fieldName, e);
		}
	}

	private static long asLong(JsonNode field, String fieldName) throws JacksonDeserializationException {
		try {
			return JsonScalars.toLong(field, JsonScalars.Policy.STRICT);
		}
		catch ( JacksonDeserializationException e ) {
			throw withField(fieldName, e);
		}
	}

	private static double asDouble(JsonNode field, String fieldName) throws JacksonDeserializationException {
		try {
			return JsonScalars.toDouble(field, JsonScalars.Policy.STRICT);
		}
		catch ( JacksonDeserializationException e ) {
			throw withField(fieldName, e);
		}
	}

	private static JacksonDeserializationException withField(String fieldName, JacksonDeserializationException e) {
		return new JacksonDeserializationException(String.format("invalid field '%s': %s", fieldName, e.getMessage()),
													e);
	}

	private static List<JsonNode> asElementList(JsonNode field, String fieldName)
		throws JacksonDeserializationException {
		if ( !field.isArray() ) {
			throw typeMismatch(fieldName, "array", field);
		}
		List<JsonNode> elements = new ArrayList<>(field.size());
		field.elements().forEachRemaining(elements::add);
		return Collections.unmodifiableList(elements);
	}

	static JacksonDeserializationException typeMismatch(String fieldName, String expected, JsonNode field) {
		return new JacksonDeserializationException(String.format("invalid field '%s': %s expected, but %s",
																fieldName, expected,
																field.getNodeType().name().toLowerCase()));
	}
}
