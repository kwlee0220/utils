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
import utils.Split;
import utils.StrSubstitutor;

/**
 * 환경 파일(<code>.env</code>류)을 읽어 변수 치환을 수행하는 로더이다.
 * <p>
 * 파일은 한 줄에 <code>KEY=VALUE</code> 형식으로 정의하며, 빈 줄과 <code>#</code>로 시작하는
 * 주석 줄은 무시된다. 값이 큰따옴표(<code>"..."</code>)로 감싸진 경우 바깥쪽 따옴표는 제거된다.
 * <p>
 * 값에 포함된 <code>${...}</code> 참조는 {@link StrSubstitutor}로 치환되며,
 * 파일에 먼저 등장한 항목의 값은 뒤에 등장하는 항목의 참조 대상이 된다(누적 확장).
 * 추가로 {@link #withFacts(Map)}로 등록한 변수와 {@code ${env:...}}, {@code ${sys:...}} 등
 * 내장 lookup도 참조할 수 있다.
 *
 * <h3>사용 패턴</h3>
 * <pre>{@code
 * Map<String,String> env = EnvironmentFileLoader.from(new File(".env"))
 *                                               .withFacts(Map.of("HOME", "/home/user"))
 *                                               .load();
 * }</pre>
 * 시스템 프로퍼티로 곧바로 반영하려면 {@link #updateSystemProperties()}를 사용한다.
 * <p>
 * 이 클래스는 스레드 안전하지 않다.
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
		return new StrSubstitutor().replaceIncrementally(keyValues, m_facts);
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
			
			Split split = Split.split(line, "=");
			if ( split.tail().isEmpty() ) {
				throw new IOException("Ill-formed line in the environment file: " + line + ", line" + count);
			}
			String value = split.tail().get();
			
			// 따옴표 제거
			if ( value.startsWith("\"") && value.endsWith("\"") && value.length() > 1 ) {
				value = value.substring(1, value.length() - 1);
			}
			keyValues.add(KeyValue.of(split.head(), value));
		}
		
		return keyValues;
	}
}