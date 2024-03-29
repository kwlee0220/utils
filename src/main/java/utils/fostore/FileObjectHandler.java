package utils.fostore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObjectHandler<K,T> extends FileObjectReader<K,T> {
	/**
	 * 주어진 파일 객체의 내용을 파일에 저장시킨다.
	 * 
	 * @param obj	파일 객체.
	 * @param file	저장할 파일
	 * @throws IOException 파일 객체 생성 중 오류가 발생된 경우일
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
