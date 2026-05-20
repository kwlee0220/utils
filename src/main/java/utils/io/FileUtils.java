package utils.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;

import com.google.common.base.Preconditions;

import utils.stream.FStream;


/**
 * 파일/디렉토리 조작을 위한 정적 유틸리티 모음.
 * <p>
 * 주요 기능:
 * <ul>
 *   <li>경로 합성({@link #path(File, String...) path}) 및 시스템 디렉토리 조회
 *       ({@link #getCurrentWorkingDirectory()}, {@link #getUserHomeDir()})</li>
 *   <li>파일/디렉토리 복사, 이동, 생성, 삭제 — 내부적으로
 *       {@code org.apache.commons.io.FileUtils}와 {@code java.nio.file.Files}에 위임한다.</li>
 *   <li>디렉토리 트리 탐색({@link #walk(File) walk}) — 결과를 {@link FStream}으로 반환하여
 *       함수형 파이프라인과 자연스럽게 연결된다.</li>
 *   <li>{@code PATH} 환경변수 기반 실행 파일 검색({@link #findExecutable(String)})</li>
 *   <li>{@link #touch(File, boolean) touch} — 파일이 없으면 생성하고, 있으면 접근/수정 시간을 갱신.</li>
 * </ul>
 * 인스턴스화할 수 없으며, 모든 메소드는 {@code static}이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class FileUtils {
	private FileUtils() {
		throw new AssertionError("Should not be called: class=" + getClass().getName());
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
	
	/**
	 * 기준 디렉토리에 자식 경로들을 차례로 결합한 {@link File}을 반환한다.
	 * <p>
	 * 예: {@code path(new File("/tmp"), "a", "b", "c")} → {@code /tmp/a/b/c}.
	 * {@code children}이 비어 있으면 {@code initial}이 그대로 반환된다.
	 *
	 * @param initial  기준 디렉토리.
	 * @param children 차례로 결합할 자식 경로 이름들.
	 * @return         결합된 {@link File}.
	 */
	public static File path(File initial, String... children) {
		return FStream.of(children)
						.fold(initial, File::new);
	}

	/**
	 * 경로 이름들을 차례로 결합한 {@link File}을 반환한다.
	 * <p>
	 * 첫 번째 이름이 루트 경로로 사용되며 나머지가 그 아래로 누적된다.
	 * 예: {@code path("/tmp", "a", "b")} → {@code /tmp/a/b}.
	 *
	 * @param names 결합할 경로 이름들. 최소 한 개 이상이어야 한다.
	 * @return      결합된 {@link File}.
	 * @throws IllegalArgumentException {@code names}가 비어 있는 경우.
	 */
	public static File path(String... names) {
		Preconditions.checkArgument(names.length > 0);
		return FStream.of(names)
						.drop(1)
						.fold(new File(names[0]), File::new);
	}

	/**
	 * 파일을 복사한다.
	 * <p>
	 * {@code org.apache.commons.io.FileUtils#copyFile(File, File)}에 위임한다. 대상 파일이 이미
	 * 존재하면 덮어쓰며, 대상 디렉토리가 없으면 생성된다.
	 *
	 * @param srcFile 원본 파일.
	 * @param tarFile 대상 파일.
	 * @throws IOException 복사 중 입출력 오류가 발생한 경우.
	 */
	public static void copyFile(File srcFile, File tarFile) throws IOException {
		org.apache.commons.io.FileUtils.copyFile(srcFile, tarFile);
	}

	/**
	 * 디렉토리를 재귀적으로 복사한다.
	 * <p>
	 * {@code org.apache.commons.io.FileUtils#copyDirectory(File, File)}에 위임한다. 하위 파일·디렉토리가
	 * 모두 복사되며, 대상 디렉토리가 없으면 생성된다.
	 *
	 * @param srcDir 원본 디렉토리.
	 * @param tarDir 대상 디렉토리.
	 * @throws IOException 복사 중 입출력 오류가 발생한 경우.
	 */
	public static void copyDirectory(File srcDir, File tarDir) throws IOException {
		org.apache.commons.io.FileUtils.copyDirectory(srcDir, tarDir);
	}

	/**
	 * 파일을 이동(또는 이름 변경)한다.
	 * <p>
	 * {@code org.apache.commons.io.FileUtils#moveFile(File, File)}에 위임한다. 대상 파일이 이미
	 * 존재하면 오류가 발생한다.
	 *
	 * @param srcFile 이동할 파일.
	 * @param tarFile 이동 후 위치.
	 * @throws IOException 이동 중 입출력 오류가 발생한 경우.
	 */
	public static void moveFile(File srcFile, File tarFile) throws IOException {
		org.apache.commons.io.FileUtils.moveFile(srcFile, tarFile);
	}

	/**
	 * 디렉토리를 이동(또는 이름 변경)한다.
	 * <p>
	 * {@code org.apache.commons.io.FileUtils#moveDirectory(File, File)}에 위임한다. 대상 디렉토리가
	 * 이미 존재하면 오류가 발생한다.
	 *
	 * @param srcDir 이동할 디렉토리.
	 * @param tarDir 이동 후 위치.
	 * @throws IOException 이동 중 입출력 오류가 발생한 경우.
	 */
	public static void moveDirectory(File srcDir, File tarDir) throws IOException {
		org.apache.commons.io.FileUtils.moveDirectory(srcDir, tarDir);
	}

	/**
	 * 디렉토리를 생성한다. 중간 경로가 없으면 함께 생성된다.
	 * <p>
	 * {@link Files#createDirectories(Path, FileAttribute...)}에 위임한다. 디렉토리가 이미 존재하면
	 * 아무 일도 일어나지 않는다.
	 *
	 * @param dir  생성할 디렉토리.
	 * @param opts 디렉토리 생성 시 적용할 파일 속성.
	 * @throws IOException 생성 중 입출력 오류가 발생한 경우.
	 */
	public static void createDirectory(File dir, FileAttribute<?>... opts) throws IOException {
		Files.createDirectories(dir.toPath(), opts);
	}

	/**
	 * 디렉토리와 그 하위 내용물을 모두 삭제한다.
	 * <p>
	 * {@code org.apache.commons.io.FileUtils#deleteDirectory(File)}에 위임한다.
	 *
	 * @param dir 삭제할 디렉토리.
	 * @throws IOException 삭제 중 입출력 오류가 발생한 경우.
	 */
	public static void deleteDirectory(File dir) throws IOException {
		org.apache.commons.io.FileUtils.deleteDirectory(dir);
	}
	
	/**
	 * 주어진 파일을 삭제한다. 만일 파일이 디렉토리인 경우 해당 디렉토리를 삭제한다.
	 * 
	 * @param file 삭제할 파일 객체
	 * @throws IOException 파일 삭제 중 오류가 발생된 경우.
	 */
	public static void deleteAnyway(File file) throws IOException {
		if ( file.isDirectory() ) {
			deleteDirectory(file);
		}
		else {
			file.delete();
		}
	}
	
	/**
	 * 주어진 디렉토리 아래를 재귀적으로 순회하며 glob 패턴과 일치하는 파일들의 스트림을 반환한다.
	 * <p>
	 * 패턴 매칭은 각 항목의 <b>파일 이름</b>({@link Path#getFileName()})에만 적용되므로, 경로 구분자
	 * (예: {@code dir/*.txt})를 포함하는 패턴은 의도대로 동작하지 않는다. 심볼릭 링크는 따라간다.
	 *
	 * @param start 순회를 시작할 디렉토리.
	 * @param glob  파일 이름에 적용할 glob 패턴(예: {@code *.txt}).
	 * @return      매칭된 파일들의 {@link FStream}.
	 * @throws IOException 순회 도중 입출력 오류가 발생한 경우.
	 */
	public static FStream<File> walk(File start, String glob) throws IOException {
		return walk(start.toPath(), glob).map(Path::toFile);
	}

	/**
	 * {@link #walk(File, String)}의 {@link Path} 버전.
	 *
	 * @param start 순회를 시작할 디렉토리 경로.
	 * @param glob  파일 이름에 적용할 glob 패턴.
	 * @return      매칭된 경로들의 {@link FStream}.
	 * @throws IOException 순회 도중 입출력 오류가 발생한 경우.
	 */
	public static FStream<Path> walk(Path start, String glob) throws IOException {
		PathMatcher matcher = FileSystems.getDefault()
										.getPathMatcher("glob:" + glob);
		return FStream.from(Files.walk(start, FileVisitOption.FOLLOW_LINKS)
								.filter(path -> matcher.matches(path.getFileName())));
	}

	/**
	 * 주어진 디렉토리 아래의 모든 파일을 재귀적으로 순회하는 스트림을 반환한다.
	 * <p>
	 * 필터링은 적용되지 않으며 심볼릭 링크 처리는 {@link Files#walk(Path, FileVisitOption...)}의
	 * 기본 동작을 따른다(즉, 링크를 따라가지 않음).
	 *
	 * @param start 순회를 시작할 디렉토리.
	 * @return      모든 파일/디렉토리의 {@link FStream}.
	 * @throws IOException 순회 도중 입출력 오류가 발생한 경우.
	 */
	public static FStream<File> walk(File start) throws IOException {
		return walk(start.toPath()).map(Path::toFile);
	}

	/**
	 * {@link #walk(File)}의 {@link Path} 버전.
	 *
	 * @param start 순회를 시작할 디렉토리 경로.
	 * @return      모든 경로의 {@link FStream}.
	 * @throws IOException 순회 도중 입출력 오류가 발생한 경우.
	 */
	public static FStream<Path> walk(Path start) throws IOException {
		return FStream.from(Files.walk(start));
	}

	/**
	 * {@code PATH} 환경 변수에 등록된 디렉토리들에서 실행 가능한 파일을 찾아 그 절대 경로를 반환한다.
	 * <p>
	 * {@code PATH}를 {@link File#pathSeparator}로 분리한 뒤 등록 순서대로 검색하여, 일반 파일이면서
	 * 실행 권한이 있는 첫 번째 항목을 반환한다.
	 *
	 * @param execName 찾을 실행 파일 이름(예: {@code python}).
	 * @return         발견된 실행 파일의 절대 경로.
	 * @throws IllegalArgumentException {@code PATH}에서 해당 실행 파일을 찾을 수 없는 경우.
	 */
	public static String findExecutable(String execName) {
		for ( String dir: System.getenv("PATH").split(File.pathSeparator) ) {
			File file = new File(dir, execName);
			if ( file.isFile() && file.canExecute() ) {
				return file.getAbsolutePath();
			}
		}

		throw new IllegalArgumentException("cannot find the executable file: " + execName);
	}

	/**
	 * 파일이 없으면 빈 파일을 생성하고, 있으면 접근 시간(과 옵션에 따라 수정 시간)을 현재 시각으로
	 * 갱신한다. (Unix {@code touch} 명령과 유사하다.)
	 * <p>
	 * 파일이 새로 생성된 경우 시간 갱신은 수행되지 않는다 (이미 현재 시각으로 생성됨).
	 *
	 * @param file               touch 대상 파일.
	 * @param updateLastModified {@code true}이면 마지막 수정 시간도 함께 갱신한다.
	 *                           {@code false}이면 접근 시간만 갱신된다.
	 * @throws IOException 파일 생성 또는 속성 변경 중 입출력 오류가 발생한 경우.
	 */
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