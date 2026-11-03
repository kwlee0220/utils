package utils;

import java.time.Duration;

import ch.qos.logback.classic.Level;
import picocli.CommandLine.ITypeConverter;

/**
 * picocli 옵션 값 변환에 쓰는 {@link ITypeConverter} 모음.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Picoclies {
	private Picoclies() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
	/**
	 * 문자열을 {@link UnitUtils#parseDuration(String)}으로 {@link Duration}으로 바꾸는 변환기.
	 * 접미사({@code ms}, {@code s}, {@code m}, {@code h}, {@code d})가 없는 값은 받지 않는다.
	 */
	public static class DurationConverter implements ITypeConverter<Duration> {
		/**
		 * 변환기를 생성한다.
		 */
		public DurationConverter() { }

		@Override
		public Duration convert(String value) throws Exception {
			return UnitUtils.parseDuration(value);
		}
	}
	/**
	 * 문자열을 {@link Level#toLevel(String)}으로 Logback {@link Level}로 바꾸는 변환기.
	 * <p>
	 * <b>주의:</b> 알 수 없는 레벨 이름이면 오류 없이 {@link Level#DEBUG}가 된다.
	 */
	public static class LogLevelConverter implements ITypeConverter<Level> {
		/**
		 * 변환기를 생성한다.
		 */
		public LogLevelConverter() { }

	    @Override
	    public Level convert(String value) throws Exception {
	        return Level.toLevel(value);
	    }

	}
}
