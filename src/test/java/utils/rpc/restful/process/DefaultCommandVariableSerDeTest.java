package utils.rpc.restful.process;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import utils.KeyValue;
import utils.async.command.CommandVariable;
import utils.json.JacksonUtils;


/**
 * {@link DefaultCommandVariableSerDe} 직렬화/역직렬화 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class DefaultCommandVariableSerDeTest {
	private static final JsonMapper MAPPER = JacksonUtils.MAPPER;
	private final DefaultCommandVariableSerDe m_serde = new DefaultCommandVariableSerDe(MAPPER);

	@TempDir File m_dir;

	private JsonNode json(String literal) throws IOException {
		return MAPPER.readTree(literal);
	}

	// ----- deserialize -----

	@Test
	public void testDeserializeTextNode() throws IOException {
		CommandVariable var = m_serde.deserialize("greeting", m_dir, new TextNode("hello"));

		Assertions.assertEquals("greeting", var.getName());
		Assertions.assertEquals(new File(m_dir, "greeting"), var.getFile());
		// 값은 JSON 직렬화된 형태(따옴표 포함)로 보관된다.
		Assertions.assertEquals("\"hello\"", var.getValue());
	}

	@Test
	public void testDeserializeObjectNode() throws IOException {
		JsonNode node = json("{\"a\":1,\"b\":\"two\"}");
		CommandVariable var = m_serde.deserialize("payload", m_dir, node);

		Assertions.assertEquals("payload", var.getName());
		Assertions.assertEquals(node, MAPPER.readTree(var.getValue()));
	}

	@Test
	public void testDeserializeNullNodeRejected() {
		// jnode는 non-null 계약이므로 null이면 거부된다.
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_serde.deserialize("opt", m_dir, null));
	}

	@Test
	public void testDeserializeNullIdRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_serde.deserialize(null, m_dir, new TextNode("v")));
	}

	@Test
	public void testDeserializeNullDirRejected() {
		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_serde.deserialize("id", null, new TextNode("v")));
	}

	@Test
	public void testDeserializeNonDirectoryRejected() throws IOException {
		File notDir = new File(m_dir, "regular-file");
		Files.writeString(notDir.toPath(), "x", StandardCharsets.UTF_8);

		Assertions.assertThrows(IllegalArgumentException.class,
								() -> m_serde.deserialize("id", notDir, new TextNode("v")));
	}

	// ----- serialize -----

	@Test
	public void testSerializeParsesValueBackToNode() throws IOException {
		CommandVariable var = new CommandVariable("greeting", "\"hello\"", new File(m_dir, "greeting"));

		KeyValue<String,JsonNode> kv = m_serde.serialize(var);

		Assertions.assertEquals("greeting", kv.key());
		Assertions.assertEquals(new TextNode("hello"), kv.value());
	}

	// ----- round trip -----

	@Test
	public void testRoundTrip() throws IOException {
		JsonNode node = json("{\"x\":[1,2,3],\"y\":{\"z\":true}}");

		CommandVariable var = m_serde.deserialize("data", m_dir, node);
		KeyValue<String,JsonNode> kv = m_serde.serialize(var);

		Assertions.assertEquals("data", kv.key());
		Assertions.assertEquals(node, kv.value());
	}
}
