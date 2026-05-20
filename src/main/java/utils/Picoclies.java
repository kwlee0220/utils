package utils;

import java.time.Duration;

import ch.qos.logback.classic.Level;
import picocli.CommandLine.ITypeConverter;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Picoclies {
	private Picoclies() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
	}
	
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
