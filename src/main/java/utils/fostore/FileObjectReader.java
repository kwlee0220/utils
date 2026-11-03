package utils.fostore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import javax.tools.FileObject;

/**
 * 파일의 내용을 읽어 파일 객체를 생성하는 인터페이스.
 *
 * @param <K>	파일 객체 식별자 타입.
 * @param <T>	파일 객체 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObjectReader<K,T> {
	/**
	 * 주어진 파일에 해당하는 파일 객체{@link FileObject}를 생성한다.
	 * 
	 * @param file	대상 파일
	 * @return	해당 파일 객체.
	 * @throws IOException 파일 객체 생성 중 오류가 발생된 경우
	 * @throws ExecutionException	파일 내용에서 객체를 생성하는 과정에서 예외가 발생된 경우.
	 */
	public T readFileObject(File file) throws IOException, ExecutionException;
}
