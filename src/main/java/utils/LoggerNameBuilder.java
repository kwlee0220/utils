package utils;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.func.Funcs;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LoggerNameBuilder {
	private final List<String> m_parts;
	
	private LoggerNameBuilder(List<String> parts) {
		m_parts = parts;
	}
	
	public static LoggerNameBuilder from(Logger logger) {
		return from(logger.getName());
	}
	
	public static LoggerNameBuilder from(String name) {
		return from(CSV.parseCsvAsArray(name, '.'));
	}
	
	public static LoggerNameBuilder from(String[] nameParts) {
		return from(Arrays.asList(nameParts));
	}
	
	public static LoggerNameBuilder from(Iterable<String> nameParts) {
		return new LoggerNameBuilder(Lists.newArrayList(nameParts));
	}
	
	public static LoggerNameBuilder from(Class<?> cls) {
		return from(cls.getName());
	}
	
	public static LoggerNameBuilder from(Package pkg) {
		return from(pkg.getName());
	}
	
	public static Logger plus(Logger logger, String suffix) {
		return from(logger.getName() + "." + suffix).getLogger();
	}
	
	public Logger getLogger() {
		return LoggerFactory.getLogger(FStream.from(m_parts).join('.'));
	}
	
	public LoggerNameBuilder append(String namePart) {
		return new LoggerNameBuilder(Funcs.addCopy(m_parts, namePart));
	}
	
	public LoggerNameBuilder skipPrefix(int count) {
		return new LoggerNameBuilder(m_parts.subList(count, m_parts.size()));
	}
	
	public LoggerNameBuilder dropSuffix(int count) {
		int end = Math.max(m_parts.size() - count, 0);
		return new LoggerNameBuilder(m_parts.subList(0, end));
	}
}
