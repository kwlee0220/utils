/**
 * 
 */
package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Function;

import utils.func.CheckedPredicate;
import utils.func.Unchecked;
import utils.stream.FStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileProxy {
	/**
	 * Returns the name of the file or directory denoted by this abstract pathname.
	 *
	 * @return The name of the file or directory denoted by this abstract pathname,
	 * or the empty string if this pathname's name sequence is empty
	 */
	public String getName();
	
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
	public FileProxy proxy(String path);
	
	/**
	 * 본 파일 proxy에 해당하는 파일의 실제 존재 여부를 반환한다.
	 *
	 * @return	존재하는 경우에는 {@code true}, 그렇지 않은 경우는 {@code false}.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public boolean exists() throws IOException;
	
	/**
	 * 본 파일의 디렉토리 여부를 반환한다.
	 *
	 * @return	디렉토리 여부
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public boolean isDirectory() throws IOException;
	
	/**
	 * Parent 파일 객체를 반환한다.
	 * 만일 parent 파일이 존재하지 않는 경우에는 {@code null}을 반환한다.
	 *
	 * @return	parent 파일 객체 또는 parent 파일이 존재하지 않는 경우에는 {@code null}.
	 */
	public FileProxy getParent();
	
	/**
	 * 현 디렉토리의 주어진 이름의 하위 파일 객체를 반환한다. 
	 *
	 * @param childName		파일 이름.
	 * @return	하위 파일 객체.
	 */
	public FileProxy getChild(String childName);
	
	/**
	 * 현 디렉토리에 속한 파일들의 proxy 리스트를 반환한다.
	 * 만일 현 파일이 디렉토리가 아닌 경우에는 {@code null}을 반환한다.
	 *
	 * @return	파일 proxy 리스트.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public List<FileProxy> listFiles() throws IOException;
	
	/**
	 * 인자로 주어진 파일 proxy의 경로로 현 파일을 이동시킨다.
	 *
	 * @param dstFile	이동시킬 대상 경로.
	 * @param replaceExisting	대상 경로의 파일이 존재하는 경우, 기존 파일 제거하고 이동할지 여부.
	 * 					{@code true}인 경우는 기존 파일을 제거하고, {@code false}인 경우는 예외를 발생시킴.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public void renameTo(FileProxy dstFile, boolean replaceExisting) throws IOException;
	
	/**
	 * 현 파일 proxy에 해당하는 파일을 삭제한다.
	 *
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public void delete() throws IOException;
	
	public default void deleteUpward() throws IOException {
		delete();
		
		FileProxy parent = getParent();
		if ( parent != null ) {
			parent.delete();
		}
		parent.deleteIfEmptyDirectory();
	}
	
	public default void deleteIfEmptyDirectory() throws IOException {
		List<FileProxy> subFiles = listFiles();
		if ( subFiles != null && subFiles.size() == 0 ) {
			delete();
			
			FileProxy parent = getParent();
			if ( parent != null ) {
				parent.deleteIfEmptyDirectory();
			}
		}
	}
	
	/**
	 * 현 파일 proxy에 해당하는 디렉토리를 생성한다.
	 *
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public void mkdir() throws IOException;
	
	/**
	 * 현 파일 proxy에 해당하는 파일의 길이를 byte 단위로 반환한다.
	 *
	 * @return	파일 byte 크기.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public long length() throws IOException;
	
	/**
	 * 현 파일을 읽기 위한 {@link InputStream}을 반환한다.
	 *
	 * @return	입력 스트림 객체.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public InputStream openInputStream() throws IOException;
	
	/**
	 * 현 파일을 쓰기 위한 {@link OutputStream}을 반환한다.
	 * {@code append}가 {@code true}인 경우에는 기존 파일이 존재하는 경우에는 해당 파일 뒤에 추가하기
	 * 위한 스트림을 반환하고,  {@code false}인 경우 동일 경로의 파일이 존재하는 경우에는 예외를 발생시킨다.
	 *
	 * @param append	추가여부.
	 * @return	출력 스트림 객체.
	 * @throws IOException	I/O 오류가 발생된 경우.
	 */
	public OutputStream openOutputStream(boolean append) throws IOException;

	public default FStream<FileProxy> walkTree(boolean includeCurrent) throws IOException {
		Function<FileProxy,FStream<FileProxy>> walkDown
			= Unchecked.sneakyThrow((FileProxy fp) ->  {
										if ( fp.isDirectory() ) {
											return fp.walkTree(includeCurrent);
										}
										else {
											return FStream.of(fp);
										}
									});
		FStream<FileProxy> descendents = FStream.from(listFiles())
												.flatMap(walkDown);
		if ( includeCurrent ) {
			return FStream.concat(FStream.of(this), descendents);
		}
		else {
			return descendents;
		}
	}
	
	public default FStream<FileProxy> walkRegularFileTree() throws IOException {
		CheckedPredicate<FileProxy> isRegularFile = fp -> !fp.isDirectory(); 
		return walkTree(false).filter(Unchecked.sneakyThrow(isRegularFile));
	}
}
