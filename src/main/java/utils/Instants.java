package utils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import utils.func.FOption;

/**
 * {@link Instant}의 문자열 변환({@link #parse(String)}, {@link #toString(Instant)}, {@link #toUtcString(Instant)})과
 * JDBC {@link Timestamp} 변환을 제공한다.
 * <ul>
 *   <li><b>시간대</b>: 시간대를 인자로 받는 메소드가 기본형이다. 시간대를 받지 않는 {@link #parse(String)}·
 *       {@link #toString(Instant)}는 호출할 때마다 {@link ZoneId#systemDefault()}를 읽어 쓰고,
 *       {@link #toUtcString(Instant)}는 UTC를 쓴다.</li>
 *   <li><b>지원 표기</b>({@link #parse(String, ZoneId)}): 오프셋({@code Z}, {@code ±hh:mm}, {@code ±hhmm},
 *       {@code ±hh:mm:ss})이 있거나 없는 ISO-8601 일시, {@code T} 대신 공백 하나로 구분한 일시, 날짜만 있는 표기.
 *       오프셋이 없으면 주어진 시간대로 해석한다.</li>
 *   <li><b>출력 형식</b>({@link #toString(Instant, ZoneId)}): ISO-8601, 소수점 이하 최소 3자리이며 밀리초 미만은
 *       손실 없이 최대 9자리. 자릿수를 고정하려면 {@link #toString(Instant, ZoneId, java.time.temporal.ChronoUnit)}를
 *       쓴다. {@link #toString(Instant)}는 JDK의 {@link Instant#toString()}(항상 UTC)과 달리 시스템 시간대로 쓴다.</li>
 *   <li><b>{@code null}</b>: 인자가 {@code null}이면 {@link IllegalArgumentException}을 던진다. JDBC {@code NULL}을
 *       다루는 {@link #fromTimestamp(Timestamp)}·{@link #toTimestamp(Instant)}만 {@code null}을 그대로 돌려준다.</li>
 * </ul>
 * <p>
 * 2026-11-01에 {@code fromString} → {@code parse}, {@code toZonedString} → {@code toString},
 * {@code toUTCString} → {@code toUtcString}으로 이름을 바꿨다(별칭 없음).
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Instants {
	/**
	 * 출력 형식: {@code yyyy-MM-dd'T'HH:mm:ss.SSS[SSSSSS]XXX}(오프셋에 초가 있으면 {@code ±hh:mm:ss}).
	 * 소수점 이하는 최소 3자리(밀리초)이고, 값에 밀리초 미만이 있으면 필요한 만큼(최대 9자리) 더 쓴다.
	 * 따라서 밀리초 정밀도 값은 이전 형식과 같고, 그보다 정밀한 값도
	 * {@link #parse(String)}으로 손실 없이 복원된다(2026-11-01 이전에는 {@code .SSS}로 고정되어 밀리초 미만을 버렸다).
	 * <p>
	 * 시간대는 포매터에 고정하지 않고 변환할 때 정한다. 2026-11-01 이전에는 시스템 시간대용 포매터가 클래스를 로딩할
	 * 때의 {@link ZoneId#systemDefault()}를 고정해, 이후 기본 시간대가 바뀌어도 이전 시간대로 출력했다.
	 */
	private static final DateTimeFormatter LOSSLESS_FORMATTER = new DateTimeFormatterBuilder()
																.append(DateTimeFormatter.ISO_LOCAL_DATE)
																.appendLiteral('T')
																.appendPattern("HH:mm:ss")
																.appendFraction(ChronoField.NANO_OF_SECOND, 3, 9, true)
																.appendOffset("+HH:MM:ss", "Z")
																.toFormatter();
	/** {@link #toString(Instant, ZoneId, ChronoUnit)}용: 소수점 이하 자릿수를 고정한 포매터(0·3·6·9자리). */
	private static final DateTimeFormatter SECONDS_FORMATTER = fixedPrecisionFormatter(0);
	private static final DateTimeFormatter MILLIS_FORMATTER = fixedPrecisionFormatter(3);
	private static final DateTimeFormatter MICROS_FORMATTER = fixedPrecisionFormatter(6);
	private static final DateTimeFormatter NANOS_FORMATTER = fixedPrecisionFormatter(9);
	/** 날짜만 있는 표기({@code yyyy-MM-dd}). */
	private static final Pattern DATE_ONLY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	/** 날짜와 시각을 공백 하나로 구분한 표기({@code yyyy-MM-dd HH:mm...}). */
	private static final Pattern SPACE_SEPARATED_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d.*");
	/**
	 * 문자열 끝에 타임존({@code Z} 또는 {@code ±hh:mm}/{@code ±hhmm}/{@code ±hh:mm:ss} 오프셋)이 명시되었는지
	 * 판별하는 패턴.
	 */
	private static final Pattern OFFSET_SUFFIX = Pattern.compile(".*([Zz]|[+-]\\d{2}:?\\d{2}(:\\d{2})?)$");
	/** 콜론 없는 끝의 오프셋({@code ±hhmm}). 파싱 전에 {@code ±hh:mm}으로 바꾼다. */
	private static final Pattern COMPACT_OFFSET_SUFFIX = Pattern.compile("([+-]\\d{2})(\\d{2})$");
	/**
	 * 오프셋이 명시된 ISO-8601 일시의 파서. 오프셋은 {@code Z}, {@code ±hh:mm}, {@code ±hh:mm:ss} 하나만 받는다
	 * ({@code ±hhmm}은 {@link #COMPACT_OFFSET_SUFFIX}로 미리 바꾼다). {@link ResolverStyle#STRICT}로 날짜를 검증한다.
	 * <p>
	 * 2026-11-01에 고쳤다. 그 전의 파서는 기본(SMART) 해석이라 {@code 2023-02-29T12:00Z}를 {@code 02-28}로,
	 * {@code T24:00Z}를 다음 날 00:00으로 조용히 바꿨고, 두 오프셋 표기를 선택 구간 두 개로 이어 붙여
	 * {@code …ZZ}나 {@code …+09:00+0900}처럼 오프셋이 두 번 붙은 문자열도 받았다.
	 */
	private static final DateTimeFormatter OFFSET_DATE_TIME_PARSER = new DateTimeFormatterBuilder()
																	.parseCaseInsensitive()
																	.append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
																	.appendOffset("+HH:MM:ss", "Z")
																	.toFormatter()
																	.withResolverStyle(ResolverStyle.STRICT);

	private Instants() {
		throw new AssertionError("should not be called: " + getClass());
	}

	/**
	 * JDBC {@link Timestamp}를 {@link Instant}로 변환한다.
	 * <p>
	 * JDBC 결과의 {@code NULL} 컬럼을 그대로 전달할 수 있도록 {@code null}을 허용한다
	 * ({@link #toTimestamp(Instant)}와 함께 이 클래스에서 {@code null}을 받는 두 메소드 중 하나).
	 *
	 * @param ts	변환할 값. {@code null}일 수 있다.
	 * @return	변환된 시각. {@code ts}가 {@code null}이면 {@code null}.
	 */
	@Nullable
	public static Instant fromTimestamp(@Nullable Timestamp ts) {
		return FOption.map(ts, Timestamp::toInstant);
	}
	
	/**
	 * {@link Instant}를 JDBC {@link Timestamp}로 변환한다. 나노초까지 유지한다.
	 * <p>
	 * {@link #fromTimestamp(Timestamp)}의 짝으로, JDBC 파라미터에 {@code NULL}을 그대로 넘길 수 있도록
	 * {@code null}을 받는다.
	 *
	 * @param instant	변환할 시각. {@code null}일 수 있다.
	 * @return	변환된 값. {@code instant}가 {@code null}이면 {@code null}.
	 */
	@Nullable
	public static Timestamp toTimestamp(@Nullable Instant instant) {
		return FOption.map(instant, Timestamp::from);
	}
	
	/**
	 * 주어진 시각을 지정한 시간대의 ISO-8601 문자열로 변환한다
	 * (예: {@code 2024-07-10T14:40:37.123+09:00}, {@code 2024-07-10T14:40:37.123456+09:00}).
	 * <p>
	 * 소수점 이하는 최소 3자리이며, 밀리초 미만이 있으면 손실 없이 최대 9자리까지 쓴다.
	 * 오프셋은 {@code ±hh:mm}이고, 0이면 {@code Z}, 초 단위가 있으면 {@code ±hh:mm:ss}로 쓴다(예: 1908년 이전
	 * {@code Asia/Seoul}의 지방 평균시 {@code +08:27:52}). 2026-11-01 이전에는 오프셋의 초를 버려 복원한 시각이 어긋났다.
	 *
	 * @param instant	변환할 시각.
	 * @param zone		변환할 시간대.
	 * @return	변환된 문자열.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우(2026-11-01 이전에는 {@code instant}가
	 * 									{@code null}이면 {@code null}을 반환했다).
	 * @throws java.time.DateTimeException	{@link LocalDateTime}으로 나타낼 수 없는 극단의 시각인 경우
	 * 									(예: {@link Instant#MAX}, {@link Instant#MIN}).
	 * @see #toString(Instant, ZoneId, ChronoUnit)	소수점 이하 자릿수를 고정하려는 경우.
	 */
	public static String toString(Instant instant, ZoneId zone) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");
		
		return LOSSLESS_FORMATTER.format(instant.atZone(zone));
	}
	/**
	 * 주어진 시각을 시스템 기본 시간대의 ISO-8601 문자열로 변환한다.
	 * <p>
	 * 기본 시간대는 호출할 때마다 {@link ZoneId#systemDefault()}로 읽는다.
	 * 형식은 {@link #toString(Instant, ZoneId)}와 같다.
	 * <p>
	 * JDK의 {@link Instant#toString()}은 항상 UTC로 쓰지만, 이 메소드는 시스템 시간대로 쓴다
	 * (예: {@code 2024-07-10T14:40:37.000+09:00}). UTC가 필요하면 {@link #toUtcString(Instant)}를 쓴다.
	 *
	 * @param instant	변환할 시각.
	 * @return	변환된 문자열.
	 * @throws IllegalArgumentException	{@code instant}가 {@code null}인 경우.
	 * @throws java.time.DateTimeException	{@link LocalDateTime}으로 나타낼 수 없는 극단의 시각인 경우.
	 */
	public static String toString(Instant instant) {
		return toString(instant, ZoneId.systemDefault());
	}
	/**
	 * 주어진 시각을 UTC 기준 ISO-8601 문자열로 변환한다
	 * (예: {@code 2024-07-10T05:40:37.000Z}, {@code 2024-07-10T05:40:37.123456Z}).
	 * <p>
	 * 소수점 이하는 최소 3자리이며, 밀리초 미만이 있으면 손실 없이 최대 9자리까지 쓴다.
	 *
	 * @param instant	변환할 시각.
	 * @return	변환된 문자열.
	 * @throws IllegalArgumentException	{@code instant}가 {@code null}인 경우.
	 * @throws java.time.DateTimeException	{@link LocalDateTime}으로 나타낼 수 없는 극단의 시각인 경우.
	 */
	public static String toUtcString(Instant instant) {
		return toString(instant, ZoneOffset.UTC);
	}
	
	/**
	 * 주어진 시각을 지정한 시간대의 ISO-8601 문자열로 변환하되, 소수점 이하 자릿수를 정밀도에 맞춰 고정한다.
	 * <p>
	 * 정밀도별 형식은 다음과 같다. 정밀도보다 작은 단위는 반올림하지 않고 버린다
	 * ({@link Instant#truncatedTo(java.time.temporal.TemporalUnit)}와 같다).
	 * <ul>
	 *   <li>{@link ChronoUnit#SECONDS}: {@code 2024-07-10T05:40:37Z}</li>
	 *   <li>{@link ChronoUnit#MILLIS}: {@code 2024-07-10T05:40:37.123Z}</li>
	 *   <li>{@link ChronoUnit#MICROS}: {@code 2024-07-10T05:40:37.123456Z}</li>
	 *   <li>{@link ChronoUnit#NANOS}: {@code 2024-07-10T05:40:37.123456789Z}</li>
	 * </ul>
	 *
	 * @param instant	변환할 시각.
	 * @param zone		변환할 시간대.
	 * @param precision	정밀도. 위 네 단위 중 하나.
	 * @return	변환된 문자열.
	 * @throws IllegalArgumentException	인자가 {@code null}이거나, 지원하지 않는 정밀도인 경우.
	 * @throws java.time.DateTimeException	{@link LocalDateTime}으로 나타낼 수 없는 극단의 시각인 경우.
	 */
	public static String toString(Instant instant, ZoneId zone, ChronoUnit precision) {
		Preconditions.checkNotNullArgument(instant, "instant is null");
		Preconditions.checkNotNullArgument(zone, "zone is null");
		Preconditions.checkNotNullArgument(precision, "precision is null");
		
		DateTimeFormatter formatter = switch ( precision ) {
			case SECONDS -> SECONDS_FORMATTER;
			case MILLIS -> MILLIS_FORMATTER;
			case MICROS -> MICROS_FORMATTER;
			case NANOS -> NANOS_FORMATTER;
			default -> throw new IllegalArgumentException("unsupported precision: " + precision
														+ " (SECONDS, MILLIS, MICROS or NANOS expected)");
		};
		return formatter.format(instant.atZone(zone));
	}
	
	/**
	 * 시각 문자열을 {@link Instant}로 변환한다.
	 * <p>
	 * 다음 표기를 지원한다. 앞뒤 공백은 무시한다.
	 * <ol>
	 *   <li>타임존이 명시된 ISO-8601 일시: {@code Z} 또는 {@code ±hh:mm}/{@code ±hhmm}/{@code ±hh:mm:ss} 오프셋을
	 *       갖는 표기. 초와 소수점 이하는 생략 가능하다. {@code defaultZone}은 쓰지 않는다.
	 *       (예: {@code 2024-07-10T14:40:37Z}, {@code 2024-07-10T14:40+09:00})</li>
	 *   <li>타임존이 없는 ISO-8601 일시: {@code defaultZone}의 시각으로 해석한다.
	 *       (예: {@code 2024-07-10T14:40:37})</li>
	 *   <li>날짜와 시각을 {@code T} 대신 공백 하나로 구분한 위의 두 표기. 초·소수점 생략과 오프셋 규칙이 같다.
	 *       (예: {@code 2024-07-10 14:40:37}, {@code 2024-07-10 14:40}, {@code 2024-07-10 14:40:37.5Z})</li>
	 *   <li>날짜만 있는 표기: {@code defaultZone}에서 그날이 시작되는 시각(보통 자정)으로 해석한다.
	 *       (예: {@code 2024-07-10})</li>
	 * </ol>
	 * 날짜와 시각은 엄격하게 검증한다. {@code 2023-02-29}, {@code 2024-02-30}, {@code 24:00}처럼 존재하지 않는 값과
	 * 오프셋이 두 번 붙은 문자열은 {@link java.time.format.DateTimeParseException}을 던진다.
	 * <p>
	 * 서머타임 전환으로 {@code defaultZone}에 없는 시각은 전환 폭만큼 뒤로 밀리고, 두 번 있는 시각은 앞쪽 오프셋으로
	 * 해석한다({@link java.time.ZonedDateTime#of(LocalDateTime, ZoneId)}의 규칙).
	 *
	 * @param str			변환할 시각 문자열.
	 * @param defaultZone	타임존이 없는 표기를 해석할 시간대.
	 * @return	변환된 {@link Instant}.
	 * @throws java.time.format.DateTimeParseException	지원하지 않는 표기인 경우.
	 * @throws IllegalArgumentException	인자가 {@code null}인 경우.
	 */
	public static Instant parse(String str, ZoneId defaultZone) {
		Preconditions.checkNotNullArgument(str, "string is null");
		Preconditions.checkNotNullArgument(defaultZone, "defaultZone is null");
		
		str = str.strip();
		if ( DATE_ONLY_PATTERN.matcher(str).matches() ) {
			return LocalDate.parse(str).atStartOfDay(defaultZone).toInstant();
		}
		if ( SPACE_SEPARATED_PATTERN.matcher(str).matches() ) {
			// "2024-07-10 14:40:37" 형태는 'T' 표기로 바꿔 아래의 같은 규칙으로 해석한다.
			// 2026-11-01 이전에는 "yyyy-MM-dd HH:mm:ss" 하나만 받았다.
			str = str.substring(0, 10) + 'T' + str.substring(11);
		}
		
		if ( OFFSET_SUFFIX.matcher(str).matches() ) {
			// 'Z' 또는 '±hh:mm'/'±hhmm'/'±hh:mm:ss' 오프셋이 명시된 경우. '±hhmm'은 '±hh:mm'으로 바꿔 하나의 파서로 읽는다.
			str = COMPACT_OFFSET_SUFFIX.matcher(str).replaceFirst("$1:$2");
			return OffsetDateTime.parse(str, OFFSET_DATE_TIME_PARSER).toInstant();
		}
		else {
			return LocalDateTime.parse(str).atZone(defaultZone).toInstant();
		}
	}
	
	/**
	 * 시각 문자열을 {@link Instant}로 변환한다. 타임존이 없는 표기는 시스템 기본 시간대로 해석한다.
	 * <p>
	 * 기본 시간대는 호출할 때마다 {@link ZoneId#systemDefault()}로 읽는다.
	 * 지원 표기는 {@link #parse(String, ZoneId)}와 같다.
	 *
	 * @param str	변환할 시각 문자열.
	 * @return	변환된 {@link Instant}.
	 * @throws java.time.format.DateTimeParseException	지원하지 않는 표기인 경우.
	 * @throws IllegalArgumentException	{@code str}이 {@code null}인 경우.
	 */
	public static Instant parse(String str) {
		return parse(str, ZoneId.systemDefault());
	}
	
	private static DateTimeFormatter fixedPrecisionFormatter(int fractionDigits) {
		DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder()
												.append(DateTimeFormatter.ISO_LOCAL_DATE)
												.appendLiteral('T')
												.appendPattern("HH:mm:ss");
		if ( fractionDigits > 0 ) {
			builder.appendFraction(ChronoField.NANO_OF_SECOND, fractionDigits, fractionDigits, true);
		}
		return builder.appendOffset("+HH:MM:ss", "Z").toFormatter();
	}
}
