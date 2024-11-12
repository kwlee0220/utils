package utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;

import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileUtils {
	private FileUtils() {
		throw new AssertionError("Should not be called: class=" + FileUtils.class.getName());
	}
	
	/**
	 * 현재 작업 디렉토리 파일 객체를 반환한다.
	 * 
	 * @return	파일 객체.
	 */
	public static File getCurrentWorkingDirectory() {
		return new File(System.getProperty("user.dir"));
	}
	
	/**
	 * 사용자 홈 디렉토리 파일 객체를 반환한다.
	 * 
	 * @return	파일 객체
	 */
	public static File getUserHomeDir() {
		return new File(System.getProperty("user.home"));
	}
	
	public static File path(File initial, String... children) {
		return FStream.of(children)
						.fold(initial, File::new);
	}
	
	public static File path(String... names) {
		return FStream.of(names)
						.fold(new File("."), File::new);
	}
	
	public static void copy(File srcFile, File tarFile, CopyOption... opts) throws IOException {
		Files.copy(srcFile.toPath(), tarFile.toPath(), opts);
	}
	
	public static void move(File srcFile, File tarFile, CopyOption... opts) throws IOException {
		Files.move(srcFile.toPath(), tarFile.toPath(), opts);
	}
	
	public static void createDirectories(File dir, FileAttribute<?>... opts) throws IOException {
		Files.createDirectories(dir.toPath(), opts);
	}
	
	public static void deleteDirectory(File dir) throws IOException {
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}
	
	public static FStream<File> walk(File start, String glob) throws IOException {
		return walk(start.toPath(), glob).map(Path::toFile);
	}
	
	public static FStream<Path> walk(Path start, String glob) throws IOException {
		PathMatcher matcher = FileSystems.getDefault()
										.getPathMatcher("glob:" + glob);
		return FStream.from(Files.walk(start, FileVisitOption.FOLLOW_LINKS)
								.filter(matcher::matches));
	}
	
	public static FStream<File> walk(File start) throws IOException {
		return walk(start.toPath()).map(Path::toFile);
	}
	
	public static FStream<Path> walk(Path start) throws IOException {
		return FStream.from(Files.walk(start));
	}
	
	public static String findExecutable(String execName) {
		for ( String dir: System.getenv("PATH").split(File.pathSeparator) ) {
			File file = new File(dir, execName);
			if ( file.isFile() && file.canExecute() ) {
				return file.getAbsolutePath();
			}
		}
		
		throw new IllegalArgumentException("cannot find the executable file: " + execName);
	}
	
	public static void touch(File file, boolean updateLastModified) throws IOException {
		if ( !file.exists() ) {
			file.createNewFile();
		}
		else {
			long now = System.currentTimeMillis();
			Files.setAttribute(file.toPath(), "lastAccessTime", FileTime.fromMillis(now));
			if ( updateLastModified ) {
				file.setLastModified(now);
			}
		}
	}
}