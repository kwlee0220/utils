package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

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
	 * 본 파일의 이름을 반환한다..
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
	 * 주어진 경로에 해당하는 proxy 파일을 생성한다.
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
	 * 본 경로에 해당하는 파일이 일반 파일인지 여부를 반환한다.
	 *
	 * @return	일반 파일인 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 */
	public boolean isRegular();
	
	/**
	 * 본 경로가 디렉토리가 아니면서 {@link #isRegular()}가 {@code true}인지 여부를 반환한다.
	 *
	 * @return	디렉토리가 아닌 일반 파일인 경우는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 */
	public default boolean isRegularFile() {
		return !isDirectory() && isRegular();
	}
	
	/**
	 * Parent 파일 객체를 반환한다.
	 * 만일 parent 파일이 존재하지 않는 경우에는 {@code null}을 반환한다.
	 *
	 * @return	parent 파일 객체 또는 parent 파일이 존재하지 않는 경우에는 {@code null}.
	 */
	public FilePath getParent();
	
	/**
	 * 현 디렉토리의 주어진 이름의 하위 파일 객체를 반환한다. 
	 *
	 * @param childName		파일 이름.
	 * @return	하위 파일 객체.
	 */
	public FilePath getChild(String childName);
	
	/**
	 * 현 디렉토리에 속한 {@link FilePath} 리스트를 반환한다.
	 * 만일 현 파일이 디렉토리가 아닌 경우에는 {@code null}을 반환한다.
	 *
	 * @return	{@link FilePath} 리스트.
	 * @throws IOException	I/O 오류가 발생된 경우.
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
	 * 본 디렉토리 아래의 모든 일반 파일({@link #walkRegularFileTree()})의 길이 합을 byte 단위로 반환한다.
	 * <p>
	 * <b>주의:</b> 개별 파일의 {@link #getLength()}가 실패하면 해당 파일은 오류 없이 합계에서 제외된다.
	 *
	 * @return	총 byte 크기.
	 * @throws IOException	하위 파일 탐색 중 I/O 오류가 발생한 경우.
	 */
	public default long getTotalLength() throws IOException {
		return walkRegularFileTree()
					.mapToTry(FilePath::getLength)
					.flatMapTry(v -> v)
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
	 * 현 경로에 해당하는 파일을 생성하고, 데이터를 추가하기 위한 {@link OutputStream}을 반환한다.
	 * 주어진 경로에 해당하는 파일이 이미 존재하는 경우에는 {@code overwrite} 값에 따라
	 * {@code true}인 경우는 기존 파일을 삭제하고 새 파일을 생성하고, {@code false}인 경우에는
	 * {@code IOException} 예외를 생성한다. 
	 * 
	 * @param overwrite	기존 파일이 존재하는 경우 해당 파일의 삭제 여부.
	 * @return	출력 스트림 객체.
	 * @throws IOException	I/O 오류가 발생된 경우.
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
	 * <b>주의:</b> 본 경로는 디렉토리여야 한다. 디렉토리가 아니면 {@link #streamChildFilePaths()}가
	 * {@code null}을 반환하여 {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param includeCurrent	결과 스트림의 맨 앞에 본 경로 자신을 포함할지 여부.
	 * @return	하위 경로 스트림.
	 * @throws IOException	본 디렉토리의 하위 파일 목록 조회 중 I/O 오류가 발생한 경우.
	 */
	public default FStream<FilePath> walkTree(boolean includeCurrent) throws IOException {
		Function<FilePath,FStream<FilePath>> walkDown
			= UncheckedFunction.sneakyThrow(fp ->  fp.isDirectory() ? fp.walkTree(true) : FStream.of(fp));
		FStream<FilePath> descendents = streamChildFilePaths().flatMap(walkDown);
		if ( includeCurrent ) {
			return FStream.of(this).concatWith(descendents);
		}
		else {
			return descendents;
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
