package utils.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import utils.KeyValue;
import utils.StrSubstitutor;
import utils.Tuple;
import utils.Utilities;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class EnvironmentFileLoader {
	private final File m_envFile;
	private final Map<String,String> m_facts = Maps.newHashMap();
	
	/**
	 * 환경 파일 로더를 생성한다.
	 * 
	 * @param envFile 환경 파일
	 * @return 환경 파일 로더
	 * @throws IOException 환경 파일을 읽는 과정에서 오류가 발생한 경우
	 */
	public static EnvironmentFileLoader from(File envFile) throws IOException {
		return new EnvironmentFileLoader(envFile);
	}
	
	private EnvironmentFileLoader(File envFile) {
		Preconditions.checkArgument(envFile != null, "envFile is null");
		
		m_envFile = envFile;
	}
	
	/**
	 * 환경 파일에서 참조하는 수 있는 변수들을 추가한다.
	 * 
	 * @param facts 변수 이름과 값의 맵
	 * @return this
	 */
	public EnvironmentFileLoader withFacts(Map<String,String> facts) {
		m_facts.putAll(facts);
		return this;
	}
	
	/**
	 * 환경 파일을 읽어서 변수 치환을 수행한 결과를 반환한다.
	 * 
	 * @return 변수 치환이 완료된 환경 변수 맵
	 * @throws IOException 환경 파일을 읽는 과정에서 오류가 발생한 경우
	 */
	public LinkedHashMap<String,String> load() throws IOException {
		List<KeyValue<String, String>> keyValues = readEnvironmentFile(m_envFile);
		return StrSubstitutor.replaceIncrementally(keyValues, m_facts);
	}
	
	/**
	 * 환경 파일에서 읽은 값을 이용하여 시스템 프로퍼티를 업데이트한다.
	 * 
	 * @throws IOException 환경 파일을 읽는 과정에서 오류가 발생한 경우
	 */
	public void updateSystemProperties() throws IOException {
		for ( Map.Entry<String,String> e : load().entrySet() ) {
			System.setProperty(e.getKey(), e.getValue());
		}
	}
	
	private List<KeyValue<String,String>> readEnvironmentFile(File envFile) throws IOException {
		if ( !envFile.exists() ) {
			throw new FileNotFoundException("Cannot find the environment file: " + envFile.getAbsolutePath());
		}

		List<KeyValue<String,String>> keyValues = Lists.newArrayList();
		int count = 0;
		for ( String line: Files.readAllLines(envFile.toPath()) ) {
			++count;
			
			line = line.trim();
			if ( line.isEmpty() || line.startsWith("#") ) {
				continue;
			}
			
			Tuple<String,String> splits = Utilities.split(line, '=');
			if ( splits == null ) {
				throw new IOException("Ill-formed line in the environment file: " + line + ", line" + count);
			}
			String value = splits._2;
			
			// 따옴표 제거
			if ( value.startsWith("\"") && value.endsWith("\"") && value.length() > 1 ) {
				value = value.substring(1, value.length() - 1);
			}
			keyValues.add(KeyValue.of(splits._1, value));
		}
		
		return keyValues;
	}
}