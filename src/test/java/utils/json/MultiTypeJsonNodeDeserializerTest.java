package utils.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * {@link MultiTypeJsonNodeDeserializer}의 타입 이름에 따른 deserializer 선택과 오류 처리를 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class MultiTypeJsonNodeDeserializerTest {
	private interface Shape { }
	private record Circle(int radius) implements Shape {
		static Circle deserializeFields(JsonNode node) throws IOException {
			return new Circle(JacksonUtils.getField(node, "radius").intValue());
		}
	}
	private record Square(int side) implements Shape {
		static Square deserializeFields(JsonNode node) throws IOException {
			return new Square(JacksonUtils.getField(node, "side").intValue());
		}
	}

	private static final MultiTypeJsonNodeDeserializer<Shape> DESERIALIZER = MultiTypeJsonNodeDeserializer.builder(Shape.class)
																		.register("circle", Circle::deserializeFields)
																		.register("square", Square::deserializeFields)
																		.build();

	private static JsonNode json(String text) throws IOException {
		return JacksonUtils.MAPPER.readTree(text);
	}

	@Test
	public void deserialize_selectsDeserializerByTypeName() throws IOException {
		assertEquals(new Circle(3), DESERIALIZER.deserialize(json("{\"@type\":\"circle\",\"radius\":3}")));
		assertEquals(new Square(4), DESERIALIZER.deserialize(json("{\"@type\":\"square\",\"side\":4}")));
		assertEquals(Set.of("circle", "square"), DESERIALIZER.getTypeNames());
		// 타입 필드를 정하지 않으면 공용 상수 JacksonUtils.TYPE_FIELD를 쓴다.
		assertEquals(JacksonUtils.TYPE_FIELD, DESERIALIZER.getTypeField());
		assertEquals("@type", JacksonUtils.TYPE_FIELD);
	}

	@Test
	public void deserialize_reportsMissingAndUnregisteredTypes() throws IOException {
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
												() -> DESERIALIZER.deserialize(json("{\"radius\":3,\"secret\":\"pw\"}")));
		assertEquals("'@type' field is missing: base=Shape, available fields=[radius, secret]", e.getMessage());

		e = assertThrows(JacksonDeserializationException.class,
						() -> DESERIALIZER.deserialize(json("{\"@type\":\"triangle\"}")));
		assertTrue(e.getMessage().startsWith("Unregistered Shape type: triangle (registered: "), e.getMessage());

		// 타입 이름은 JSON 문자열만 받는다. 숫자·불리언을 텍스트로 바꿔 등록 타입을 고르지 않는다.
		assertThrows(JacksonDeserializationException.class, () -> DESERIALIZER.deserialize(json("{\"@type\":{\"x\":1}}")));
		MultiTypeJsonNodeDeserializer<String> scalars = MultiTypeJsonNodeDeserializer.builder(String.class)
																.register("1", node -> "one")
																.register("true", node -> "true")
																.build();
		e = assertThrows(JacksonDeserializationException.class, () -> scalars.deserialize(json("{\"@type\":1}")));
		assertEquals("invalid field '@type': string expected, but number", e.getMessage());
		e = assertThrows(JacksonDeserializationException.class, () -> scalars.deserialize(json("{\"@type\":true}")));
		assertEquals("invalid field '@type': string expected, but boolean", e.getMessage());
		assertEquals("one", scalars.deserialize(json("{\"@type\":\"1\"}")));
		assertThrows(IllegalArgumentException.class, () -> DESERIALIZER.deserialize(null));
	}

	@Test
	public void deserialize_propagatesDeserializerIOException() throws IOException {
		// deserializer가 던진 IOException은 그대로 전달한다.
		IOException e = assertThrows(IOException.class, () -> DESERIALIZER.deserialize(json("{\"@type\":\"circle\"}")));
		assertTrue(e.getMessage().startsWith("Field not found: field=radius"), e.getMessage());
	}

	@Test
	public void builder_customTypeFieldAndValidation() throws IOException {
		MultiTypeJsonNodeDeserializer<Shape> deserializer = MultiTypeJsonNodeDeserializer.builder(Shape.class)
														.typeField("kind")
														.register("circle", Circle::deserializeFields)
														.build();
		assertEquals(new Circle(1), deserializer.deserialize(json("{\"kind\":\"circle\",\"radius\":1}")));

		MultiTypeJsonNodeDeserializer.Builder<Shape> builder = MultiTypeJsonNodeDeserializer.builder(Shape.class).register("circle", Circle::deserializeFields);
		assertThrows(IllegalArgumentException.class, () -> builder.register("circle", Circle::deserializeFields));
		assertThrows(IllegalArgumentException.class, () -> builder.register(null, Circle::deserializeFields));
		assertThrows(IllegalArgumentException.class, () -> builder.register("x", null));
		assertThrows(IllegalArgumentException.class, () -> builder.typeField(""));
		assertThrows(IllegalStateException.class, () -> MultiTypeJsonNodeDeserializer.builder(Shape.class).build());
		assertThrows(IllegalArgumentException.class, () -> MultiTypeJsonNodeDeserializer.builder(null));
	}

	@Test
	public void isItselfAJsonNodeDeserializer() throws IOException {
		// 등록부 자체를 하나의 deserializer로 다른 등록부에 넣을 수 있다.
		JsonNodeDeserializer<Shape> asDeserializer = DESERIALIZER;
		assertEquals(new Circle(2), asDeserializer.deserialize(json("{\"@type\":\"circle\",\"radius\":2}")));

		MultiTypeJsonNodeDeserializer<Shape> outer = MultiTypeJsonNodeDeserializer.builder(Shape.class)
																.typeField("family")
																.register("basic", DESERIALIZER)
																.build();
		assertEquals(new Square(5), outer.deserialize(json("{\"family\":\"basic\",\"@type\":\"square\",\"side\":5}")));
	}

	@Test
	public void builtDeserializer_isNotAffectedByLaterRegistrations() throws IOException {
		MultiTypeJsonNodeDeserializer.Builder<Shape> builder = MultiTypeJsonNodeDeserializer.builder(Shape.class).register("circle", Circle::deserializeFields);
		MultiTypeJsonNodeDeserializer<Shape> deserializer = builder.build();
		builder.register("square", Square::deserializeFields);

		assertEquals(Set.of("circle"), deserializer.getTypeNames());
		assertThrows(JacksonDeserializationException.class, () -> deserializer.deserialize(json("{\"@type\":\"square\",\"side\":1}")));
		assertThrows(UnsupportedOperationException.class, () -> deserializer.getTypeNames().add("x"));
		Shape shape = builder.build().deserialize(json("{\"@type\":\"square\",\"side\":1}"));
		assertSame(Square.class, shape.getClass());
	}
}
