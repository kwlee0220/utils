package utils.fostore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;


/**
 * 파일 객체 저장소({@link FileObjectStore})에서 식별자와 파일 사이의 대응 및
 * 파일 객체의 읽기/쓰기를 담당하는 인터페이스.
 *
 * @param <K>	파일 객체 식별자 타입.
 * @param <T>	파일 객체 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObjectHandler<K,T> extends FileObjectReader<K,T> {
	/**
	 * 주어진 파일 객체의 내용을 파일에 저장시킨다.
	 * 
	 * @param obj	파일 객체.
	 * @param file	저장할 파일
	 * @throws IOException 파일 저장 중 입출력 오류가 발생된 경우.
	 * @throws ExecutionException	파일 객체를 파일 내용으로 변환하는 과정에서 예외가 발생된 경우.
	 */
	public void writeFileObject(T obj, File file) throws IOException, ExecutionException;
	
	/**
	 * 주어진 식별자에 해당하는 파일 객체가 저장된 파일을 반환한다.
	 *
	 * @param key	대상 파일 객체의 식별자.
     * @return	대상 파일 객체가 저장된 파일.
	 */
    public File toFile(K key);

    /**
     * 주어진 파일에 해당하는 파일 객체의 식별자를 반환한다.
     *
     * @param file	대상 파일
     * @return	대상 객체의 식별자.
     */
    public K toFileObjectKey(File file);

    /**
     * 주어진 파일이 파일 객체 저장소에서 관리되는 유효한 파일인지 여부를 반환한다.
     *
     * @param file	판별 대상 파일
     * @return	유효성 여부.
     */
    public boolean isVallidFile(File file);
}
