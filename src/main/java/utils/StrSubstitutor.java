package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;

import com.google.common.collect.Maps;

import utils.io.IOUtils;


/**
 * <code>${...}</code> 형식의 변수 참조를 주어진 키-값 맵으로 치환하는 유틸리티 클래스이다.
 * <p>
 * Apache Commons Text의 {@link StringSubstitutor}를 감싼 얇은 래퍼로,
 * 사용자 정의 맵 lookup과 기본 interpolator lookup(<code>sys</code>, <code>env</code>,
 * <code>date</code> 등)을 함께 사용한다.
 *
 * <h3>변수 참조 라우팅</h3>
 * <ul>
 *   <li><b>prefix 없는 참조 — <code>${X}</code>:</b> 사용자 키-값 맵에서 {@code X}를 찾아 치환한다.</li>
 *   <li><b>prefix 있는 참조 — <code>${env:HOME}</code>, <code>${sys:user.dir}</code> 등:</b>
 *       해당 prefix의 내장 lookup을 사용한다 ({@code env}, {@code sys}, {@code date} 등).</li>
 *   <li><b>매칭되지 않은 참조:</b> {@link #failOnUndefinedVariable(boolean)} 설정에 따라
 *       {@link IllegalArgumentException}을 던지거나 원본 토큰을 그대로 둔다.</li>
 * </ul>
 *
 * <h3>기본 설정</h3>
 * <ul>
 *   <li>중첩 치환 활성화 — 치환된 값 내부의 참조와 변수 이름 내부의 참조를 모두 재치환한다.</li>
 *   <li>미정의 변수에 대해 예외를 발생시킨다({@link IllegalArgumentException}).</li>
 * </ul>
 * 각각은 {@link #enableNestedSubstitution(boolean)}, {@link #failOnUndefinedVariable(boolean)}로
 * 전환할 수 있다.
 *
 * <h3>사용 패턴</h3>
 * <ul>
 *   <li>키-값 매핑이 있을 때: {@link #with(Map)} 또는 {@link #with(String, String)}로 인스턴스를
 *       생성하고, chaining 으로 설정 후 {@link #replace(String)} 또는 {@link #replace(File, File)}을 호출.</li>
 *   <li>매핑 없이 내장 lookup({@code env}, {@code sys} 등)만 필요할 때: 무인자 {@link #StrSubstitutor()} 사용.</li>
 *   <li>환경 파일처럼 앞선 정의를 뒤에서 재사용하는 누적 확장이 필요할 때:
 *       {@link #replaceIncrementally(List, Map)} 사용.</li>
 * </ul>
 * <p>
 * 이 클래스는 스레드 안전하지 않다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class StrSubstitutor {
	private final StringSubstitutor m_substitutor;
	// 설정 상속을 위해 인스턴스 필드로 보관 (Apache StringSubstitutor에 일부 getter가 누락됨).
	private boolean m_failOnUndefined = true;
	private boolean m_nestedSubstitution = true;
	
	/**
	 * 주어진 키-값 맵을 lookup 소스로 하는 {@code StrSubstitutor} 인스턴스를 생성한다.
	 * <p>
	 * 사용자 키-값 lookup 외에도 {@code ${env:...}}, {@code ${sys:...}}, {@code ${date:...}}
	 * 등 Apache 내장 interpolator lookup이 자동으로 동작한다.
	 * <p>
	 * 내부적으로 전달된 맵을 참조로 보관하므로, 생성 이후 맵을 변경하면
	 * 이후의 {@link #replace(String)} 호출에 그 변화가 반영된다.
	 *
	 * @param keyValues	치환에 사용할 키-값 맵. {@code null}이면 안 된다.
	 * @return	생성된 {@code StrSubstitutor} 인스턴스.
	 */
	public static StrSubstitutor with(Map<String, String> keyValues) {
		return new StrSubstitutor(keyValues);
	}

	/**
	 * 단일 키-값을 lookup 소스로 하는 {@code StrSubstitutor} 인스턴스를 생성한다.
	 * <p>
	 * 단일 변수만 치환하는 간단한 케이스를 위한 편의 메소드이다.
	 * 내부적으로 {@link Map#of(Object, Object)}로 불변 맵을 만들어 보관한다.
	 * 사용자 키-값 외에 Apache 내장 lookup({@code env}, {@code sys}, {@code date} 등)도 동작한다.
	 *
	 * @param key	치환할 단일 변수 이름. {@code null}이면 안 된다.
	 * @param value	해당 변수의 값. {@code null}이면 안 된다.
	 * @return	생성된 {@code StrSubstitutor} 인스턴스.
	 */
	public static StrSubstitutor with(String key, String value) {
		Preconditions.checkNotNullArgument(key, "key must not be null");
		Preconditions.checkNotNullArgument(value, "value must not be null");
		return new StrSubstitutor(Map.of(key, value));
	}

	/**
	 * 주어진 키-값 맵을 lookup 소스로 하는 {@code StrSubstitutor}를 생성한다.
	 * <p>
	 * 외부에서는 {@link #with(Map)} 또는 {@link #with(String, String)} 정적 팩토리를 사용해
	 * 인스턴스를 생성한다. 내부적으로 전달된 맵을 참조로 보관하므로 생성 이후 맵을 변경하면
	 * 이후의 {@link #replace(String)} 호출에 그 변화가 반영된다.
	 *
	 * @param keyValues	치환에 사용할 키-값 맵.
	 */
	private StrSubstitutor(Map<String,String> keyValues) {
		Preconditions.checkNotNullArgument(keyValues, "keyValues must not be null");

		StringLookupFactory factory = StringLookupFactory.INSTANCE;
		StringLookup lookup = factory.mapStringLookup(keyValues);
		StringLookup interpolator = factory.interpolatorStringLookup(Map.of(), lookup, true);

		m_substitutor = new StringSubstitutor(interpolator)
							.setDisableSubstitutionInValues(!m_nestedSubstitution)
							.setEnableSubstitutionInVariables(m_nestedSubstitution)
							.setEnableUndefinedVariableException(m_failOnUndefined);
	}

	/**
	 * 사용자 정의 키-값 없이 기본 interpolator lookup만 사용하는 {@code StrSubstitutor}를 생성한다.
	 * <p>
	 * <code>${env:...}</code>, <code>${sys:...}</code> 등 내장 lookup은 동작하지만,
	 * 사용자 키에 대한 참조는 미정의로 취급된다.
	 */
	public StrSubstitutor() {
		this(Map.of());
	}

	/**
	 * 템플릿에 미정의 변수 참조가 있을 때 예외를 발생시킬지 여부를 설정한다.
	 * <p>
	 * 기본값은 {@code true}이며, {@code false}로 설정하면 미정의 변수는
	 * 원본 토큰(<code>${X}</code>) 그대로 남는다.
	 *
	 * @param flag	{@code true}이면 미정의 변수에 대해 {@link IllegalArgumentException}을 던진다.
	 * @return	메서드 체이닝을 위한 자기 자신.
	 */
	public StrSubstitutor failOnUndefinedVariable(boolean flag) {
		m_substitutor.setEnableUndefinedVariableException(flag);
		m_failOnUndefined = flag;
		return this;
	}

	/**
	 * 중첩 치환 동작을 활성화 또는 비활성화한다.
	 * <p>
	 * 이 메서드는 Apache {@link StringSubstitutor}의 두 가지 설정을 하나로 묶어 제어한다.
	 * <ul>
	 *   <li><b>값 내부 치환</b> — 치환된 값 자체가 다시 <code>${...}</code>를 포함하면 이를 재치환한다.
	 *       예: 맵에 <code>B = "${A}a"</code>, <code>A = "1"</code>이 있을 때 <code>${B}</code>를
	 *       치환하면 활성화 시 {@code "1a"}, 비활성화 시 {@code "${A}a"}가 된다.</li>
	 *   <li><b>변수 이름 내부 치환</b> — 변수 이름 자체에 참조를 포함하는 형태
	 *       (예: <code>${${key}}</code>)를 허용한다.</li>
	 * </ul>
	 * 기본값은 {@code true}이다.
	 *
	 * @param flag	{@code true}이면 두 종류의 중첩 치환을 모두 허용한다.
	 * @return	메서드 체이닝을 위한 자기 자신.
	 */
	public StrSubstitutor enableNestedSubstitution(boolean flag) {
		m_substitutor.setDisableSubstitutionInValues(!flag)
					.setEnableSubstitutionInVariables(flag);
		m_nestedSubstitution = flag;
		return this;
	}

	/**
	 * 템플릿 문자열 내의 <code>${...}</code> 참조를 현재 설정에 따라 치환한다.
	 *
	 * @param template	치환 대상 템플릿 문자열. {@code null}이면 {@code null}을 반환한다.
	 * @return	치환이 완료된 문자열.
	 * @throws IllegalArgumentException	{@link #failOnUndefinedVariable(boolean)}가 {@code true}
	 * 									(기본값)인 상태에서 미정의 변수가 참조된 경우.
	 */
	public String replace(String template) {
		if ( template == null ) {
			return null;
		}
		return m_substitutor.replace(template);
	}

	/**
	 * 순서가 있는 키-값 목록을 앞에서부터 차례로 치환하되,
	 * 이전 항목의 치환 결과를 이후 항목의 lookup 소스에 누적하면서 확장한다.
	 * <p>
	 * 초기 lookup 소스는 {@code facts}이며, 각 단계에서 <code>kv.value()</code>를 치환해
	 * 그 결과를 결과 맵과 lookup 소스에 함께 추가한다. 따라서 뒤쪽 항목의 값은
	 * {@code facts}에 더해 이전 항목의 키까지 참조할 수 있다. 환경 파일(<code>.env</code>류)처럼
	 * 앞선 정의를 뒤에서 재사용하는 포맷을 해석할 때 유용하다.
	 * <p>
	 * 내부 처리에는 {@code this}의 설정({@link #failOnUndefinedVariable(boolean)},
	 * {@link #enableNestedSubstitution(boolean)})이 그대로 상속된다.
	 * 단, lookup 소스는 {@code facts}와 누적된 키-값만 사용되며 {@code this}의 lookup 맵은 사용되지 않는다.
	 * <p>
	 * 같은 키가 {@code keyValues}에 중복으로 등장하면 마지막 값이 결과/lookup에 남는다.
	 *
	 * @param keyValues	치환 순서대로 평가될 키-값 목록.
	 * @param facts		초기 lookup 소스로 사용되는 키-값 맵. 이 맵 자체는 변경되지 않는다.
	 * @return	각 키에 대해 누적 확장된 치환 결과를 입력 순서대로 담은 {@link LinkedHashMap}.
	 */
	public LinkedHashMap<String,String> replaceIncrementally(List<KeyValue<String,String>> keyValues,
																Map<String,String> facts) {
		Preconditions.checkNotNullArgument(keyValues, "keyValues must not be null");
		Preconditions.checkNotNullArgument(facts, "facts must not be null");

		// expandedFacts는 mapStringLookup이 reference로 보관하므로, 아래 루프에서 추가되는
		// 키-값 쌍은 즉시 후속 iteration의 lookup에 반영된다 — incremental 누적의 핵심 트릭.
		Map<String,String> expandedFacts = Maps.newHashMap(facts);
		StrSubstitutor subst = with(expandedFacts)
									.failOnUndefinedVariable(m_failOnUndefined)
									.enableNestedSubstitution(m_nestedSubstitution);

		LinkedHashMap<String,String> result = Maps.newLinkedHashMap();
		for ( KeyValue<String,String> kv : keyValues ) {
			String replaced = subst.replace(kv.value());
			result.put(kv.key(), replaced);
			expandedFacts.put(kv.key(), replaced);
		}

		return result;
	}

	/**
	 * 템플릿 파일을 읽어 변수 치환을 적용한 결과를 출력 파일에 쓴다.
	 * <p>
	 * 치환 동작은 현재 인스턴스의 설정({@link #failOnUndefinedVariable(boolean)},
	 * {@link #enableNestedSubstitution(boolean)})을 그대로 따른다. {@code templateFile}과
	 * {@code outputFile}이 같은 파일이어도 안전하다.
	 * <p>
	 * 출력은 같은 디렉토리에 임시 파일로 먼저 작성된 뒤 atomic move 로 최종 위치로 옮겨지므로,
	 * 치환 또는 쓰기 도중 예외가 발생해도 출력 파일이 부분 변경 상태로 남지 않는다.
	 * (atomic move 가 지원되지 않는 환경에서는 일반 replace fallback 으로 동작한다.)
	 * <p>
	 * 임시 파일은 POSIX 환경에서 {@code 0600} 권한으로 생성되므로 atomic move 후 출력 파일의
	 * 권한이 원본보다 제한될 수 있다. 보안에 민감한 컨텍스트에서는 이 점을 유의한다.
	 *
	 * @param templateFile	치환 대상 템플릿 파일. {@code null}이면 안 된다.
	 * @param outputFile	치환 결과를 쓸 파일. {@code null}이면 안 된다.
	 * 						{@code templateFile}과 같아도 된다.
	 * @throws IOException	파일 입출력 실패.
	 */
	public void replace(File templateFile, File outputFile) throws IOException {
		Preconditions.checkNotNullArgument(templateFile, "templateFile must not be null");
		Preconditions.checkNotNullArgument(outputFile, "outputFile must not be null");

		String template = IOUtils.toString(templateFile);
		String substituted = replace(template);

		Path target = outputFile.toPath();
		Path parent = target.toAbsolutePath().getParent();
		// outputFile이 root 자체인 가장자리 케이스 — system temp dir 으로 fallback.
		// (이 경우 atomic move 가 다른 파일시스템 mount 라 fallback 분기로 빠질 수 있음.)
		if ( parent == null ) {
			parent = Path.of(System.getProperty("java.io.tmpdir"));
		}
		Path tmp = Files.createTempFile(parent, ".strsubst-", ".tmp");
		try {
			IOUtils.toFile(substituted, tmp.toFile());
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch ( AtomicMoveNotSupportedException e ) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally {
			Files.deleteIfExists(tmp);
		}
	}
}