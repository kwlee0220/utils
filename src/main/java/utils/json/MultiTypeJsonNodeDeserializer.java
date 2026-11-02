package utils.json;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import utils.Preconditions;

/**
 * 타입 이름 필드(기본 {@value JacksonUtils#TYPE_FIELD})의 값에 따라 등록된 deserializer를 골라 JSON 노드를
 * 객체로 바꾸는 등록부.
 * <p>
 * {@code {"@type": "ref", ...}}처럼 구체 타입을 필드로 밝히는 다형 JSON을 읽을 때 쓴다. 리플렉션을 쓰지 않고, 타입
 * 이름마다 {@link JsonNodeDeserializer}를 등록한다. 만든 뒤에는 등록 내용을 바꿀 수 없다. 여러 쓰레드가 함께
 * 쓰려면 등록된 deserializer들도 쓰레드 안전해야 한다(보통처럼 상태 없는 정적 메소드 참조를 등록하면 된다).
 * <pre>{@code
 * static final MultiTypeJsonNodeDeserializer<ArgumentSpec> DESERIALIZER = MultiTypeJsonNodeDeserializer.builder(ArgumentSpec.class)
 *         .register(ReferenceArgument.SERIALIZATION_TYPE, ReferenceArgument::deserializeFields)
 *         .register(LiteralArgument.SERIALIZATION_TYPE, LiteralArgument::deserializeFields)
 *         .build();
 *
 * ArgumentSpec spec = DESERIALIZER.deserialize(jnode);
 * }</pre>
 * deserializer에는 타입 이름 필드를 포함한 노드 전체를 넘긴다.
 * <p>
 * 이 클래스도 {@link JsonNodeDeserializer}이므로 다른 등록부에 하나의 deserializer로 등록하거나, 그 인터페이스를
 * 받는 곳에 그대로 넘길 수 있다.
 *
 * @param <T>	결과 객체의 공통 상위 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public final class MultiTypeJsonNodeDeserializer<T> implements JsonNodeDeserializer<T> {
	private final Class<T> m_baseType;
	private final String m_typeField;
	private final Map<String, JsonNodeDeserializer<? extends T>> m_deserializers;
	private final Set<String> m_typeNames;

	private MultiTypeJsonNodeDeserializer(Builder<T> builder) {
		m_baseType = builder.m_baseType;
		m_typeField = builder.m_typeField;
		m_deserializers = Map.copyOf(builder.m_deserializers);
		m_typeNames = Set.copyOf(builder.m_deserializers.keySet());
	}

	/**
	 * 새 등록부를 만드는 빌더를 얻는다.
	 *
	 * @param <T>		결과 객체의 공통 상위 타입.
	 * @param baseType	결과 객체의 공통 상위 타입. 오류 메시지에 이름을 쓴다.
	 * @return	빌더.
	 * @throws IllegalArgumentException	{@code baseType}이 {@code null}인 경우.
	 */
	public static <T> Builder<T> builder(Class<T> baseType) {
		return new Builder<>(baseType);
	}

	/**
	 * 타입 이름 필드의 값에 등록된 deserializer로 노드를 객체로 바꾼다.
	 *
	 * @param node	타입 이름 필드를 가진 JSON 노드.
	 * @return	deserializer가 만든 객체.
	 * @throws IllegalArgumentException	{@code node}가 {@code null}인 경우.
	 * @throws JacksonDeserializationException	타입 이름 필드가 없거나, JSON 문자열이 아니거나(숫자·불리언 포함),
	 * 										등록되지 않은 타입인 경우.
	 * @throws IOException	deserializer가 던진 경우.
	 */
	@Override
	public T deserialize(JsonNode node) throws IOException {
		Preconditions.checkNotNullArgument(node, "JsonNode is null");

		JsonNode typeNode = JacksonUtils.getFieldOrNull(node, m_typeField);
		if ( typeNode == null ) {
			throw new JacksonDeserializationException(String.format("'%s' field is missing: base=%s, %s",
																	m_typeField, m_baseType.getSimpleName(),
																	JacksonUtils.describeFields(node)));
		}
		// 타입 이름은 JSON 문자열만 받는다. 일반 문자열 조회처럼 숫자·불리언을 텍스트로 바꿔 받으면
		// {"@type":1}이 "1"로 등록된 타입을 고르게 된다.
		if ( !typeNode.isTextual() ) {
			throw JacksonUtils.typeMismatch(m_typeField, "string", typeNode);
		}
		String typeName = typeNode.textValue();
		JsonNodeDeserializer<? extends T> deserializer = m_deserializers.get(typeName);
		if ( deserializer == null ) {
			throw new JacksonDeserializationException(String.format("Unregistered %s type: %s (registered: %s)",
																	m_baseType.getSimpleName(), typeName,
																	m_typeNames));
		}
		return deserializer.deserialize(node);
	}

	/**
	 * 등록된 타입 이름들을 돌려준다.
	 *
	 * @return	타입 이름 집합(수정 불가).
	 */
	public Set<String> getTypeNames() {
		return m_typeNames;
	}

	/**
	 * 타입 이름 필드의 이름을 돌려준다.
	 *
	 * @return	필드 이름.
	 */
	public String getTypeField() {
		return m_typeField;
	}

	@Override
	public String toString() {
		return String.format("MultiTypeJsonNodeDeserializer[%s, field=%s, types=%s]", m_baseType.getSimpleName(), m_typeField,
							m_typeNames);
	}

	/**
	 * {@link MultiTypeJsonNodeDeserializer} 빌더.
	 *
	 * @param <T>	결과 객체의 공통 상위 타입.
	 */
	public static final class Builder<T> {
		private final Class<T> m_baseType;
		private String m_typeField = JacksonUtils.TYPE_FIELD;
		private final Map<String, JsonNodeDeserializer<? extends T>> m_deserializers
																						= new LinkedHashMap<>();

		private Builder(Class<T> baseType) {
			Preconditions.checkNotNullArgument(baseType, "baseType is null");

			m_baseType = baseType;
		}

		/**
		 * 타입 이름 필드의 이름을 정한다. 정하지 않으면 {@value JacksonUtils#TYPE_FIELD}이다.
		 *
		 * @param name	필드 이름.
		 * @return	이 빌더.
		 * @throws IllegalArgumentException	{@code name}이 {@code null}이거나 빈 문자열인 경우.
		 */
		public Builder<T> typeField(String name) {
			Preconditions.checkArgument(name != null && !name.isEmpty(), "type field name is null or empty");

			m_typeField = name;
			return this;
		}

		/**
		 * 타입 이름과 그 deserializer를 등록한다.
		 *
		 * @param typeName	타입 이름 필드의 값.
		 * @param deserializer	노드 전체를 받아 객체를 만드는 함수.
		 * @return	이 빌더.
		 * @throws IllegalArgumentException	인자가 {@code null}이거나, 이미 등록한 타입 이름인 경우.
		 */
		public Builder<T> register(String typeName, JsonNodeDeserializer<? extends T> deserializer) {
			Preconditions.checkNotNullArgument(typeName, "typeName is null");
			Preconditions.checkNotNullArgument(deserializer, "deserializer is null");
			Preconditions.checkArgument(!m_deserializers.containsKey(typeName), "duplicate type name: %s", typeName);

			m_deserializers.put(typeName, deserializer);
			return this;
		}

		/**
		 * 등록부를 만든다. 이후 이 빌더를 바꿔도 만든 등록부에는 영향이 없다.
		 *
		 * @return	새 등록부.
		 * @throws IllegalStateException	등록한 타입이 하나도 없는 경우.
		 */
		public MultiTypeJsonNodeDeserializer<T> build() {
			Preconditions.checkState(!m_deserializers.isEmpty(), "no type registered: base=%s",
									m_baseType.getSimpleName());

			return new MultiTypeJsonNodeDeserializer<>(this);
		}
	}
}
