package utils.io;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class ZipFile {
	private static final Logger s_logger = LoggerFactory.getLogger(ZipFile.class);
	
	private final URI m_uri;
	
	public ZipFile(Path path) {
		m_uri = URI.create("jar:file:" + path.toUri().getPath());
	}
	
	public List<Path> listEntries() throws IOException {
		final List<Path> pathList = Lists.newArrayList();
		traverse(path -> pathList.add(path));
		
		return pathList;
	}
	
	public void traverse(Consumer<Path> visitor) throws IOException {
		final Map<String,String> env = Maps.newHashMap();
		
		try ( FileSystem zipFs = FileSystems.newFileSystem(m_uri, env) ) {
			Files.walkFileTree(zipFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
					visitor.accept(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					visitor.accept(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
	
	public static ZipFile zip(Path zipFile, Path dir) throws IOException {
		String baseName = dir.getParent().getFileName().toString();
		return zip(zipFile, baseName, dir);
	}
	
	public static ZipFile zip(Path zipFile, String baseName, Path... files) throws IOException {
		final Map<String,String> env = Maps.newHashMap();
		env.put("create", "true");

		URI uri = URI.create("jar:file:" + zipFile.toUri().getPath());
		try ( FileSystem zipFs = FileSystems.newFileSystem(uri, env) ) {
			baseName = baseName.trim();
			final Path root = (baseName.length() > 0) ? zipFs.getPath("/", baseName)
														: zipFs.getPath("/");
			if ( Files.notExists(root) ) {
				Files.createDirectories(root);
			}
			
			for ( Path src: files ) {
				int prefixNameCnt = src.getNameCount()-1;
				
				//add a file to the zip file system
				if ( !Files.isDirectory(src) ) {
					Path suffix = src.subpath(prefixNameCnt, prefixNameCnt+1);
					final Path dest = zipFs.getPath(root.toString(), suffix.toString());
					
					s_logger.debug("adding {} -> {}#{}", src, zipFile, dest);
					Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
				}
				else{
					//for directories, walk the file tree
					Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
							throws IOException {
							Path suffix = file.subpath(prefixNameCnt, file.getNameCount());
							final Path dest = zipFs.getPath(root.toString(), suffix.toString());

							s_logger.debug("adding {} -> {}#{}", file, zipFile, dest);
							Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
							return FileVisitResult.CONTINUE;
						}
						 
						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
							throws IOException {
							Path suffix = dir.subpath(prefixNameCnt, dir.getNameCount());
										
							final Path dest = zipFs.getPath(root.toString(), suffix.toString());
							if ( Files.notExists(dest) ) {
								Files.createDirectories(dest);
							}
							return FileVisitResult.CONTINUE;
						}
					});
				}
			}
		}
		
		return new ZipFile(zipFile);
	}
	
	public void unzip(Path destTopDir) throws IOException {
		final Map<String,String> env = Maps.newHashMap();
		env.put("create", "true");
		
		try ( FileSystem zipFs = FileSystems.newFileSystem(m_uri, env) ) {
			Files.walkFileTree(zipFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
					final Path dest = Paths.get(destTopDir.toString(), file.toString());
					Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
					
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					final Path destDir = Paths.get(destTopDir.toString(), dir.toString());
					if ( Files.notExists(destDir) ) {
						Files.createDirectory(destDir);
					}
					
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}
