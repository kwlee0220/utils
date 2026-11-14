package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import utils.func.UncheckedFunction;
import utils.func.UncheckedPredicate;
import utils.stream.FStream;

/**
 * 파일 또는 디렉토리를 가리키는 경로 proxy 인터페이스이다.
 * 파일 정보 조회, 하위 파일 탐색, 이동/삭제, 입출력 스트림 생성 기능을 제공한다.
 * @author Kang-Woo Lee (ETRI)
 */
public interface FilePath {
	/**
	 * 본 파일의 이름을 반환한다.
	 *
	 * @return 파일 이름.
	 */
	public String getName();
	
	/**
	 * 본 파일의 경로를 반환한다.
	 * 
	 * @return	파일 경로명.
	 */
	public String getPath();
	
	/**
	 * 본 파일의 절대 경로를 반환한다.
	 *
	 * @return 절대 경로.
	 */
	public String getAbsolutePath();
	
	/**
	 * 본 경로와 같은 파일 시스템에서 주어진 경로명에 해당하는 proxy 파일을 생성한다.
	 * <p>
	 * 경로명은 본 경로를 기준으로 해석하지 않는다(본 경로는 파일 시스템을 정하는 데만 쓰인다). 하위 경로가
	 * 필요하면 {@link #getChild(String)}를 쓴다.
	 *
	 * @param path	경로명.
	 * @return	proxy 객체.
	 */
	public FilePath path(String path);
	
	/**
	 * 본 파일 proxy에 해당하는 파일의 실제 존재 여부를 반환한다.
	 *
	 * @return	존재하는 경우에는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 */
	public boolean exists();
	
	/**
	 * 본 파일의 디렉토리 여부를 반환한다.
	 *
	 * @return	본 proxy에 해당하는 파일이 디렉토리인 경우는 {@code true},
	 * 			그렇지 않은 경우는 {@code false}가 반환됨.
	 */
	public boolean isDirectory();
	
	/**
	 * 본 경로가 숨김 경로가 아닌지 여부를 반환한다.
	 * <p>
	 * 파일 종류(일반 파일/디렉토리)와는 무관하다. Hadoop의 규칙을 따라 이름이 {@code _}나 {@code .}로 시작하면
	 * 숨김 경로(예: {@code _SUCCESS}, {@code .crc} 파일)로 보고 {@code false}를 반환한다. 파일 존재 여부도
	 * 확인하지 않는다.
	 *
	 * @return	이름이 {@code _}나 {@code .}로 시작하지 않으면 {@code true}.
	 */
	public boolean isRegular();
	
	/**
	 * 본 경로가 디렉토리가 아니면서 숨김 경로도 아닌지({@link #isRegular()}) 여부를 반환한다.
	 *
	 * @return	디렉토리가 아니고 이름이 {@code _}나 {@code .}로 시작하지 않으면 {@code true}.
	 */
	public default boolean isRegularFile() {
		return !isDirectory() && isRegular();
	}
	
	/**
	 * Parent 파일 객체를 반환한다.
	 * 경로에 parent가 없는 경우(루트나, 부모 없는 상대 경로)에는 {@code null}을 반환한다. parent의 실제 존재
	 * 여부는 확인하지 않는다.
	 *
	 * @return	parent 파일 객체. parent가 없으면 {@code null}.
	 */
	public @Nullable FilePath getParent();
	
	/**
	 * 현 디렉토리의 주어진 이름의 하위 파일 객체를 반환한다. 
	 *
	 * @param childName		파일 이름.
	 * @return	하위 파일 객체.
	 */
	public FilePath getChild(String childName);
	
	/**
	 * 현 디렉토리에 바로 속한 하위 경로들의 스트림을 반환한다. {@code null}은 반환하지 않는다.
	 *
	 * @return	하위 경로 스트림. 빈 디렉토리면 빈 스트림.
	 * @throws java.nio.file.NoSuchFileException	현 경로가 존재하지 않는 경우.
	 * @throws java.nio.file.NotDirectoryException	현 경로가 디렉토리가 아닌 경우.
	 * @throws IOException	그 밖의 I/O 오류가 발생된 경우.
	 */
	public FStream<FilePath> streamChildFilePaths() throws IOException;
	
	/**
	 * 인자로 주어진 파일 proxy의 경로로 현 파일을 이동시킨다.
	 *
	 * @param dstFile	이동시킬 대상 경로.
	 * @param replaceExisting	대상 경로의 파일이 존재하는 경우, 기존 파일 제거하고 이동할지 여부.
	 * 					{@code true}인 경우는 기존 파일을 제거하고, {@code false}인 경우는 예외를 발생시킴.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public void renameTo(FilePath dstFile, boolean replaceExisting) throws IOException;
	
	/**
	 * 현 파일 proxy에 해당하는 파일을 삭제한다.
	 *
	 * @return	대상 파일이 삭제된 경우는 {@code true}, 그렇지 않은 경우에는 {@code false}.
	 */
	public boolean delete();
	
	/**
	 * 본 파일을 삭제하고, 부모 디렉토리에 대해 {@link #deleteIfEmptyDirectory()}를 호출하여
	 * 비게 된 상위 디렉토리들을 차례로 삭제한다.
	 * 본 파일의 삭제 실패({@link #delete()}가 {@code false}를 반환한 경우)는 무시된다.
	 *
	 * @throws IOException	상위 디렉토리의 하위 파일 목록 조회 중 I/O 오류가 발생한 경우.
	 */
	public default void deleteUpward() throws IOException {
		delete();
		
		FilePath parent = getParent();
		if ( parent != null ) {
			parent.deleteIfEmptyDirectory();
		}
	}
	
	/**
	 * 본 경로가 비어 있는 디렉토리이면 삭제하고, 이어서 부모 디렉토리에 대해서도
	 * 같은 작업을 재귀적으로 수행한다. 디렉토리가 아니거나 비어 있지 않으면 아무것도 하지 않는다.
	 * 디렉토리 삭제 실패는 무시된다.
	 *
	 * @throws IOException	하위 파일 목록 조회 중 I/O 오류가 발생한 경우.
	 */
	public default void deleteIfEmptyDirectory() throws IOException {
		if ( isDirectory() && !streamChildFilePaths().exists() ) {
			delete();
			
			FilePath parent = getParent();
			if ( parent != null ) {
				parent.deleteIfEmptyDirectory();
			}
		}
	}
	
	/**
	 * 현 파일 proxy에 해당하는 디렉토리를 생성한다.
	 *
	 * @return	본 메소드로 디렉토리가 생성된 경우는 {@code true}가 반환되고,
	 * 			그렇지 않으면 {@code false} 반환됨.
	 */
	public boolean mkdirs();
	
	/**
	 * 현 파일 proxy에 해당하는 파일의 길이를 byte 단위로 반환한다.
	 *
	 * @return	파일 byte 크기.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public long getLength() throws IOException;
	
	/**
	 * 본 디렉토리 아래의 모든 디렉토리가 아닌 파일({@link #walkRegularFileTree()})의 길이 합을 byte 단위로 반환한다.
	 *
	 * @return	총 byte 크기.
	 * @throws IOException	하위 파일 탐색이나 개별 파일의 {@link #getLength()} 중 I/O 오류가 발생한 경우.
	 * 						일부 파일을 빼고 합계를 내지 않는다.
	 */
	public default long getTotalLength() throws IOException {
		// 개별 파일의 IOException은 원소를 꺼낼 때 그대로(감싸지 않고) 전파된다.
		return walkRegularFileTree().mapOrThrow(FilePath::getLength)
									.mapToLong(v -> v)
									.sum();
	}
	
	/**
	 * 현 파일을 읽기 위한 {@link InputStream}을 반환한다.
	 *
	 * @return	입력 스트림 객체.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public InputStream read() throws IOException;

	/**
	 * 현 경로에 해당하는 파일을 생성하고, 데이터를 기록하기 위한 {@link OutputStream}을 반환한다.
	 * 파일이 이미 있는 경우, {@code overwrite}가 {@code true}이면 기존 내용을 지우고 처음부터 기록하고,
	 * {@code false}이면 {@code IOException}을 던진다.
	 *
	 * @param overwrite	파일이 이미 있을 때 덮어쓸지 여부.
	 * @return	출력 스트림 객체.
	 * @throws IOException	{@code overwrite}가 {@code false}이고 파일이 이미 있는 경우, 또는 I/O 오류가 발생된 경우.
	 */
	public OutputStream create(boolean overwrite) throws IOException;
	
	/**
	 * 현 파일에 데이터를 추가하기 위한 {@link OutputStream}을 반환한다.
	 * 
	 * @return	출력 스트림 객체.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public OutputStream append() throws IOException;

	/**
	 * 본 디렉토리 아래의 모든 하위 파일과 디렉토리를 깊이 우선(전위 순서)으로 나열하는 스트림을 반환한다.
	 * 하위 디렉토리의 탐색은 스트림을 소비하는 시점에 수행되며, 그 과정에서 발생한
	 * {@link IOException}은 검사되지 않는 예외처럼(sneaky throw) 그대로 전파된다.
	 * <p>
	 * 본 경로는 디렉토리여야 한다. 디렉토리가 아니면 {@link #streamChildFilePaths()}와 같이
	 * {@link java.nio.file.NotDirectoryException}을 던진다.
	 *
	 * @param includeCurrent	결과 스트림의 맨 앞에 본 경로 자신을 포함할지 여부.
	 * @return	하위 경로 스트림.
	 * @throws IOException	본 디렉토리의 하위 파일 목록 조회 중 I/O 오류가 발생한 경우.
	 */
	public default FStream<FilePath> walkTree(boolean includeCurrent) throws IOException {
		Function<FilePath,FStream<FilePath>> walkDown
			= UncheckedFunction.sneakyThrow(fp ->  fp.isDirectory() ? fp.walkTree(true) : FStream.of(fp));
		FStream<FilePath> descendants = streamChildFilePaths().flatMap(walkDown);
		if ( includeCurrent ) {
			return FStream.of(this).concatWith(descendants);
		}
		else {
			return descendants;
		}
	}
	
	/**
	 * 본 디렉토리 아래의 모든 디렉토리가 아닌 파일들을 나열하는 스트림을 반환한다.
	 * 본 경로 자신은 포함되지 않으며, 탐색 방식과 제약은 {@link #walkTree(boolean)}와 같다.
	 *
	 * @return	하위 파일 스트림.
	 * @throws IOException	본 디렉토리의 하위 파일 목록 조회 중 I/O 오류가 발생한 경우.
	 */
	public default FStream<FilePath> walkRegularFileTree() throws IOException {
		return walkTree(false).filterNot(UncheckedPredicate.sneakyThrow(FilePath::isDirectory));
	}
}
