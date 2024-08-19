package utils;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;

import utils.func.FOption;
import utils.stream.FStream;


/**
 * {@code JsonConfigurations}는 Json 파일 기반 설정 정보를 다루는 인터페이스를 정의한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JsonConfigurations {
	private static final JsonMapper MAPPER = JsonMapper.builder()
														.addModule(new JavaTimeModule())
														.build();
	private final File m_confFile;
	
	public static JsonMapper getJsonSerDe() {
		return MAPPER;
	}
	
	/**
	 * 주어진 파일기반 Json 설정 객체를 생성한다.
	 * 
	 * @param	configFile	Json 설정 파일
	 */
	public JsonConfigurations(File configFile) {
		Preconditions.checkArgument(configFile != null);
		
		m_confFile = configFile;
	}
	
	public <T> FOption<T> getConfiguration(String key, Class<T> configClass) throws IOException {
		Preconditions.checkArgument(key != null, "Configuration key is null");
		Preconditions.checkArgument(configClass != null, "Configuration class is null");
		
		return FStream.from(MAPPER.readTree(m_confFile).properties())
						.findFirst(ent -> ent.getKey().equals(key))
						.mapOrThrow(ent -> MAPPER.readValue(ent.getValue().traverse(), configClass));
	}
}
