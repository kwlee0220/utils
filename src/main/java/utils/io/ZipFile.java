package utils.io;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
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
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import utils.Preconditions;

/**
 * ZIP 파일을 NIO ZIP {@link FileSystem}을 통해 압축/해제/탐색하는 유틸리티 클래스이다.
 * <p>
 * 인스턴스는 특정 ZIP 파일 경로를 가리키며({@code jar:} URI로 보관), 그 ZIP의 항목 나열
 * ({@link #listEntries()} / {@link #traverse(Consumer)})과 해제({@link #unzip(Path)})를
 * 수행한다. 새 ZIP을 만들려면 정적 팩토리 {@link #zip(Path, String, List)} 또는
 * {@link #zipDirectory(Path, Path)}를 사용한다. 압축/해제 매 호출마다 ZIP {@code FileSystem}을
 * 열고 닫으므로 별도의 자원 해제는 필요 없다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ZipFile {
	private static final Logger s_logger = LoggerFactory.getLogger(ZipFile.class);

	private final URI m_uri;

	/**
	 * 주어진 경로의 ZIP 파일을 가리키는 {@code ZipFile} 인스턴스를 생성한다.
	 * <p>
	 * 생성 시점에는 파일을 열거나 검증하지 않으며, 실제 접근은
	 * {@link #listEntries()} / {@link #traverse(Consumer)} / {@link #unzip(Path)} 호출 시 이뤄진다.
	 *
	 * @param path	대상 ZIP 파일 경로. {@code null}이면 안 된다.
	 */
	public ZipFile(Path path) {
		Preconditions.checkNotNullArgument(path, "path is null");

		m_uri = URI.create("jar:" + path.toUri());
	}

	/**
	 * ZIP 파일 안의 모든 항목(파일과 디렉토리)의 경로 문자열을 나열한다.
	 * <p>
	 * ZIP {@link FileSystem}은 호출이 끝나면 닫히므로, 그 안의 {@link Path} 객체를 그대로 돌려주면
	 * 이후 접근 시 {@code ClosedFileSystemException}이 발생한다. 따라서 재사용 가능한 경로 문자열
	 * (예: {@code "/dir/file.txt"})을 반환한다.
	 *
	 * @return	ZIP 내부 기준의 항목 경로 문자열 목록.
	 * @throws IOException	ZIP 파일을 열거나 탐색하는 중 오류가 발생한 경우.
	 */
	public List<String> listEntries() throws IOException {
		final List<String> nameList = Lists.newArrayList();
		traverse(path -> nameList.add(path.toString()));

		return nameList;
	}

	/**
	 * ZIP 파일 안의 모든 항목을 순회하며 주어진 {@code visitor}에 전달한다.
	 * <p>
	 * 파일과 디렉토리를 모두 방문하며, 전달되는 {@link Path}는 ZIP 내부 {@link FileSystem}에
	 * 속한 경로이다. ZIP {@code FileSystem}은 순회가 끝나면 닫히므로, {@code visitor}가 받은
	 * 경로를 호출 종료 이후까지 보관하여 다시 접근해서는 안 된다.
	 *
	 * @param visitor	각 항목 경로를 받을 consumer. {@code null}이면 안 된다.
	 * @throws IOException	ZIP 파일을 열거나 탐색하는 중 오류가 발생한 경우.
	 */
	public void traverse(Consumer<Path> visitor) throws IOException {
		Preconditions.checkNotNullArgument(visitor, "visitor is null");

		withZipFs(m_uri, Map.of(), zipFs -> {
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
		});
	}

	/**
	 * 디렉토리의 내용을 압축하여 새 ZIP 파일을 만든다.
	 * <p>
	 * {@code dir}의 직속 항목들을 ZIP 루트에 담는다. 즉 {@code dir} 자체는 ZIP에 포함되지 않고
	 * 그 안의 파일/하위 디렉토리가 루트 바로 아래에 들어간다. 하위 디렉토리는 자신의 이름을 유지한 채
	 * 재귀적으로 압축된다.
	 *
	 * @param zipFile	생성할 ZIP 파일 경로. {@code null}이면 안 된다.
	 * @param dir		압축할 대상 디렉토리. {@code null}이면 안 된다.
	 * @return	생성된 ZIP 파일을 가리키는 {@code ZipFile} 인스턴스.
	 * @throws IOException	{@code dir}이 디렉토리가 아니거나({@code NotDirectoryException}),
	 * 						압축 중 입출력 오류가 발생한 경우.
	 */
	public static ZipFile zipDirectory(Path zipFile, Path dir) throws IOException {
		Preconditions.checkNotNullArgument(zipFile, "zipFile is null");
		Preconditions.checkNotNullArgument(dir, "dir is null");

		// dir.toFile().listFiles()는 null을 반환할 수 있고 default FileSystem만 지원하므로
		// Files.list()로 대체. Path가 디렉토리가 아니면 NotDirectoryException이 의미 있게 전파된다.
		try ( Stream<Path> entries = Files.list(dir) ) {
			List<Path> files = entries.toList();
			return zip(zipFile, "", files);
		}
	}

	/**
	 * 주어진 파일/디렉토리 목록을 압축하여 새 ZIP 파일을 만든다.
	 * <p>
	 * 모든 항목은 ZIP 내부의 {@code baseName} 디렉토리 아래에 담긴다({@code baseName}이 빈 문자열이면
	 * 루트). 각 항목의 추가 규칙은 다음과 같다.
	 * <ul>
	 *   <li><b>파일</b> — 경로의 마지막 이름(파일명)만 사용하여 {@code baseName} 바로 아래에 추가한다.
	 *       즉 원본의 상위 디렉토리 구조는 보존되지 않고 평탄화된다.</li>
	 *   <li><b>디렉토리</b> — 디렉토리 자신의 이름을 prefix로 유지한 채 그 하위 트리 전체를 재귀적으로
	 *       추가한다.</li>
	 * </ul>
	 * 같은 이름의 항목이 중복되면 나중 항목이 기존 것을 덮어쓴다.
	 *
	 * @param zipFile	생성할 ZIP 파일 경로. {@code null}이면 안 된다.
	 * @param baseName	ZIP 내부에서 항목들을 담을 기준 디렉토리 이름. 빈 문자열이면 루트. {@code null}이면 안 된다.
	 * @param files		압축할 파일/디렉토리 목록. {@code null}이면 안 된다.
	 * @return	생성된 ZIP 파일을 가리키는 {@code ZipFile} 인스턴스.
	 * @throws IOException	압축 중 입출력 오류가 발생한 경우.
	 */
	public static ZipFile zip(Path zipFile, String baseName, List<Path> files) throws IOException {
		Preconditions.checkNotNullArgument(zipFile, "zipFile is null");
		Preconditions.checkNotNullArgument(baseName, "baseName is null");
		Preconditions.checkNotNullArgument(files, "files is null");

		final String base = baseName.trim();
		URI uri = URI.create("jar:" + zipFile.toUri());
		withZipFs(uri, Map.of("create", "true"), zipFs -> {
			final Path root = (base.length() > 0) ? zipFs.getPath("/", base)
													: zipFs.getPath("/");
			if ( Files.notExists(root) ) {
				Files.createDirectories(root);
			}

			for ( Path src: files ) {
				Preconditions.checkArgument(src.getNameCount() > 0, "cannot zip a root path: " + src);
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
		});

		return new ZipFile(zipFile);
	}
	
	/**
	 * ZIP 파일의 전체 내용을 주어진 디렉토리 아래로 해제한다.
	 * <p>
	 * ZIP 내부의 디렉토리 구조를 그대로 보존하여 {@code destTopDir} 밑에 재현한다.
	 * 기존에 같은 이름의 파일이 있으면 덮어쓴다.
	 *
	 * @param destTopDir	해제 결과를 담을 최상위 디렉토리.
	 * @throws IOException	ZIP 파일을 열거나 해제하는 중 오류가 발생한 경우.
	 */
	public void unzip(Path destTopDir) throws IOException {
		// 해제는 읽기 작업이므로 create 옵션을 주지 않는다(없는 ZIP을 빈 파일로 만들어 오류를 가리지 않도록).
		withZipFs(m_uri, Map.of(), zipFs -> {
			Files.walkFileTree(zipFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
					final Path dest = resolveDest(destTopDir, file);
					// 디렉토리 엔트리가 명시되지 않은 ZIP을 대비해 부모 디렉토리를 보장한다.
					Files.createDirectories(dest.getParent());
					Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
					throws IOException {
					Files.createDirectories(resolveDest(destTopDir, dir));

					return FileVisitResult.CONTINUE;
				}
			});
		});
	}

	/**
	 * ZIP 내부 엔트리 경로를 해제 대상 경로로 변환하되, {@code destTopDir} 밖으로 벗어나지 않는지 검증한다.
	 * <p>
	 * 신뢰할 수 없는 ZIP의 {@code ../} 류 경로가 대상 디렉토리 밖에 파일을 쓰는 이른바 "Zip Slip"을
	 * 방지한다. 변환된 경로가 {@code destTopDir} 하위가 아니면 {@link IOException}을 던진다.
	 *
	 * @param destTopDir	해제 최상위 디렉토리.
	 * @param zipEntry		ZIP 내부 엔트리 경로(루트 기준, 예: {@code "/dir/file"}).
	 * @return	검증된 해제 대상 경로.
	 * @throws IOException	변환 경로가 {@code destTopDir}를 벗어나는 경우.
	 */
	private static Path resolveDest(Path destTopDir, Path zipEntry) throws IOException {
		Path base = destTopDir.toAbsolutePath().normalize();
		Path dest = Paths.get(destTopDir.toString(), zipEntry.toString()).toAbsolutePath().normalize();
		if ( !dest.startsWith(base) ) {
			throw new IOException("ZIP entry escapes target directory: " + zipEntry);
		}

		return dest;
	}

	@FunctionalInterface
	private interface ZipFsTask {
		void run(FileSystem zipFs) throws IOException;
	}

	/**
	 * 주어진 URI의 ZIP {@link FileSystem}을 열어 {@code task}를 수행하고 닫는다.
	 * <p>
	 * 이미 같은 URI의 FileSystem이 열려 있으면(JDK zipfs는 URI당 하나만 허용한다) 새로 열지 않고
	 * 기존 것을 재사용하며, 이 경우 닫지 않는다(소유자가 닫도록 둔다). 이로써 같은 ZIP에 대한 중첩
	 * 호출에서 발생하던 {@link FileSystemAlreadyExistsException}을 방지한다. 단, 서로 다른 스레드가
	 * 동시에 같은 ZIP을 다루는 경우의 안전까지 보장하지는 않는다.
	 */
	private static void withZipFs(URI uri, Map<String,String> env, ZipFsTask task) throws IOException {
		FileSystem zipFs;
		boolean owns;
		try {
			zipFs = FileSystems.newFileSystem(uri, env);
			owns = true;
		}
		catch ( FileSystemAlreadyExistsException e ) {
			zipFs = FileSystems.getFileSystem(uri);
			owns = false;
		}

		try {
			task.run(zipFs);
		}
		finally {
			if ( owns ) {
				zipFs.close();
			}
		}
	}
}
