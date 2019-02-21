package utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileUtils {
	private FileUtils() {
		throw new AssertionError("Should not be called: class=" + FileUtils.class.getName());
	}
	
	public static FStream<File> walk(File start, String glob) throws IOException {
		return walk(start.toPath(), glob).map(Path::toFile);
	}
	
	public static FStream<Path> walk(Path start, String glob) throws IOException {
		PathMatcher matcher = FileSystems.getDefault()
										.getPathMatcher("glob:" + glob);
		return FStream.from(Files.walk(start)
								.filter(matcher::matches));
	}
	
	public static FStream<File> walk(File start) throws IOException {
		return walk(start.toPath()).map(Path::toFile);
	}
	
	public static FStream<Path> walk(Path start) throws IOException {
		return FStream.from(Files.walk(start));
	}
}