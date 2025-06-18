package utils;

import java.time.Duration;

import lombok.experimental.UtilityClass;

import ch.qos.logback.classic.Level;
import picocli.CommandLine.ITypeConverter;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
@UtilityClass
public class Picoclies {
	public static class DurationConverter implements ITypeConverter<Duration> {
		@Override
		public Duration convert(String value) throws Exception {
			return UnitUtils.parseDuration(value);
		}
	}
	public static class LogLevelConverter implements ITypeConverter<Level> {
	    @Override
	    public Level convert(String value) throws Exception {
	        return Level.toLevel(value);
	    }

	}
}
