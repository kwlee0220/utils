package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.stream.FStream;

/**
 *
 * @author Kang-Woo Lee
 */
public class Utilities {
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private Utilities() {
		throw new AssertionError("Should not be invoked!!: class=" + Utilities.class.getName());
	}
	
	public static Optional<String> getEnvironmentVariable(String envVarName) {
		return Optional.ofNullable(System.getenv(envVarName));
	}
	
	/**
	 * 주어진 환경 변수에 이름에 기술된 파일 객체를 반환한다.
	 * <p>
	 * 만일 환경 변수에 지정된 경로의 파일이 존재하지 않는 경우에는 {@link Optional#empty()}가 반환된다.
	 * 
	 * @param	envVarName	환경 변수 이름
	 * @return	{@link Optional} 객체.
	 */
	public static Optional<File> getEnvironmentVariableFile(String envVarName) {
		return getEnvironmentVariable(envVarName)
					.flatMap(path -> {
						File file = new File(path);
						if ( file.exists() ) {
							return Optional.of(file);
						}
						else {
							String msg = String.format("EnvironmentVariable does not have a valid file: %s=%s",
														envVarName, path);
							throw new IllegalArgumentException(msg);
						}
					});
	}
	
	public static String getLineSeparator() {
		return LINE_SEPARATOR;
	}

	public static <T> Stream<T> stream(Iterable<T> it) {
		return StreamSupport.stream(it.spliterator(), false);
	}
	
	public static <T> Stream<T> stream(Iterator<T> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
	}
	
	public static Logger getAndAppendLoggerName(Object obj, String suffix) {
    	if ( obj instanceof LoggerSettable ) {
    		Logger prev = ((LoggerSettable)obj).getLogger();
    		Logger logger = LoggerFactory.getLogger(prev.getName() + suffix);
    		((LoggerSettable)obj).setLogger(logger);
    		
    		return prev;
    	}
    	else {
    		return null;
    	}
    }
	
	public static <T> List<T> shuffle(List<T> list) {
		return selectRandomly(list, list.size());
	}
	
	public static <T> List<T> selectRandomly(List<T> list, int count) {
		Random rand = new Random(System.currentTimeMillis());
		
		List<T> selecteds = new ArrayList<>();
		for ( int i =0; i < count; ++i ) {
			while ( true ) {
				if ( list.isEmpty() ) {
					return selecteds;
				}
				
				T selected = selectOne(list, rand);
				if ( selected != null ) {
					selecteds.add(selected);
					break;
				}
				
				list = FStream.from(list)
								.filter(v -> v != null)
								.toList();
			}
		}
		
		return selecteds;
	}
	
	private static <T> T selectOne(List<T> list, Random rand) {
		for ( int i =0; i < 5; ++i ) {
			int idx = rand.nextInt(list.size());
			T selected = list.get(idx);
			if ( selected != null ) {
				list.set(idx, null);
				
				return selected;
			}
		}
		
		return null;
	}

	//	private static final Pattern KV_PAT = Pattern.compile("(\\w+)=\"*((?<=\")[^\"]+(?=\")|([^\\s]+))\"*");
//	private static final Pattern KV_PAT = Pattern.compile("(\\S+)\\s*=\\s*\"*(((?<=\\\")([^\\\"]*)(?=\\\"))|([^;\\s][^;\\s]*))\"*;?");
	public static List<KeyValue<String,String>> parseKeyValues(String expr, char delim) {
		return CSV.parseCsv(expr, delim, '"')
					.map(String::trim)
					.map(KeyValue::parse)
					.toList();
	}
	
	public static Map<String,String> parseKeyValueMap(String expr, char delim) {
		return CSV.parseCsv(expr, ';', '"')
					.map(String::trim)
					.toKeyValueStream(KeyValue::parse)
					.toMap();
	}
	
	public static boolean isWindowsOS() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
}