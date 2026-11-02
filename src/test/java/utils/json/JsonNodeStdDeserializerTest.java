package utils.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * {@link JsonNodeStdDeserializer}가 {@code @JsonDeserialize}나 모듈 등록을 통해 등록부에 위임하는지 검증한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JsonNodeStdDeserializerTest {
	@JsonDeserialize(using = ShapeDeserializer.class)
	interface Shape { }
	record Circle(int radius) implements Shape { }
	record Square(int side) implements Shape { }

	static final MultiTypeJsonNodeDeserializer<Shape> REGISTRY
		= MultiTypeJsonNodeDeserializer.builder(Shape.class)
			.register("circle", node -> new Circle(JacksonUtils.getIntField(node, "radius")))
			.register("square", node -> new Square(JacksonUtils.getIntField(node, "side")))
			.build();

	@SuppressWarnings("serial")
	public static class ShapeDeserializer extends JsonNodeStdDeserializer<Shape> {
		public ShapeDeserializer() {
			super(Shape.class, REGISTRY);
		}
	}

	@Test
	public void delegatesToRegistry() throws IOException {
		Shape shape = JacksonUtils.MAPPER.readValue("{\"@type\":\"circle\",\"radius\":3}", Shape.class);
		assertEquals(new Circle(3), shape);

		// 컬렉션 원소와 JSON null도 처리한다(null은 Jackson이 처리하고 위임하지 않는다).
		List<Shape> shapes = JacksonUtils.MAPPER.readValue("[{\"@type\":\"square\",\"side\":2},null]",
															new TypeReference<List<Shape>>() { });
		assertEquals(new Square(2), shapes.get(0));
		assertNull(shapes.get(1));
	}

	@Test
	public void propagatesRegistryErrors() {
		// 등록부의 예외가 그대로 전달된다.
		JacksonDeserializationException e = assertThrows(JacksonDeserializationException.class,
					() -> JacksonUtils.MAPPER.readValue("{\"@type\":\"triangle\"}", Shape.class));
		// 등록 타입 목록의 순서는 고정되어 있지 않으므로 앞부분만 비교한다.
		assertTrue(e.getMessage().startsWith("Unregistered Shape type: triangle (registered: "), e.getMessage());
	}

	@Test
	public void rejectsNullArguments() {
		assertThrows(IllegalArgumentException.class, () -> new JsonNodeStdDeserializer<Shape>(Shape.class, null));
		assertThrows(IllegalArgumentException.class, () -> new JsonNodeStdDeserializer<Shape>(null, REGISTRY));
	}

	public static class Holder {
		public Shape shape;
		public int count;
	}

	@Test
	public void worksWithTrailingTokenCheck() throws IOException {
		// FAIL_ON_TRAILING_TOKENS를 켜도 배열 원소와 객체 필드 안에서 읽을 수 있어야 한다.
		ObjectMapper strict = JacksonUtils.newJsonMapper(false).rebuild()
										.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
										.build();
		List<Shape> shapes = strict.readValue("[{\"@type\":\"circle\",\"radius\":1},{\"@type\":\"square\",\"side\":2}]",
											new TypeReference<List<Shape>>() { });
		assertEquals(List.of(new Circle(1), new Square(2)), shapes);

		Holder holder = strict.readValue("{\"shape\":{\"@type\":\"circle\",\"radius\":3},\"count\":5}", Holder.class);
		assertEquals(new Circle(3), holder.shape);
		assertEquals(5, holder.count);

		// 최상위 값 뒤의 진짜 후행 토큰은 여전히 거부된다.
		assertThrows(IOException.class, () -> strict.readValue("{\"@type\":\"circle\",\"radius\":1} {}", Shape.class));
	}

	interface Plain { }
	record Point(int x) implements Plain { }

	@Test
	public void canBeRegisteredDirectlyInModule() throws IOException {
		// 어노테이션 없이 모듈에 직접 만들어 등록할 수 있다.
		JsonNodeDeserializer<Plain> deser = node -> new Point(JacksonUtils.getIntField(node, "x"));
		ObjectMapper mapper = JacksonUtils.newJsonMapper(false).rebuild()
							.addModule(new SimpleModule().addDeserializer(Plain.class,
																		new JsonNodeStdDeserializer<>(Plain.class, deser)))
							.build();
		assertEquals(new Point(7), mapper.readValue("{\"x\":7}", Plain.class));
	}
}
