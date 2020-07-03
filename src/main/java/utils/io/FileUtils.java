package utils.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.apache.commons.io.FilenameUtils;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import utils.func.CheckedSupplierX;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileUtils {
	private FileUtils() {
		throw new AssertionError("Should not be called: class=" + FileUtils.class.getName());
	}
	
	public static String getExtension(File file) {
		return FilenameUtils.getExtension(file.getAbsolutePath());
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
	
	public static Observable<WatchEvent<?>> watchDir(FileSystem fs, Path dir,
													WatchEvent.Kind<?>... events) {
		return Observable.create(new ObservableOnSubscribe<WatchEvent<?>>() {
			@Override
			public void subscribe(ObservableEmitter<WatchEvent<?>> emitter) throws Exception {
				try ( WatchService watch = fs.newWatchService() ) {
					dir.register(watch, events);
					
			        WatchKey key;
					while ( true ) {
						key = watch.take();
						if ( emitter.isDisposed() ) {
							return;
						}
						
						for ( WatchEvent<?> ev : key.pollEvents() ) {
							emitter.onNext(ev);
						}
						key.reset();
					}
				}
				catch ( ClosedWatchServiceException | InterruptedException e ) {
					emitter.onComplete();
				}
				catch ( Throwable e ) {
					emitter.onError(e);
				}
			}
		});
	}
	
	public static Observable<WatchEvent<?>> watchFile(FileSystem fs, Path file,
														WatchEvent.Kind<?>... events) {
		return watchDir(fs, file.getParent(), events)
				.filter(ev -> Files.isSameFile(file, (Path)ev.context()));
	}
}