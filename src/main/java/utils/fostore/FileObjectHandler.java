package utils.fostore;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObjectHandler<T> {
	public void save(T fobj, File file) throws IOException;
	public void destroy(T fobj);
	
	/**
	 * 주어진 파일에 해당하는 파일 객체{@link FileObject}를 생성한다.
	 * 
	 * @param file	대상 파일
	 * @return	해당 파일 객체.
	 * @throws IOException 파일 객체 생성 중 오류가 발생된 경우
	 */
	public T newFileObject(File file) throws IOException;
	
	/**
	 * 주어진 식별자에 해당하는 파일 객체가 저장된 파일을 반환한다.
	 *
	 * @param id	대상 파일 객체의 식별자.
     * @return	대상 파일 객체가 저장된 파일.
	 */
    public File toFile(String id);

    /**
     * 주어진 파일에 해당하는 파일 객체의 식별자를 반환한다.
     *
     * @param file	대상 파일
     * @return	대상 객체의 식별자.
     */
    public String toFileObjectId(File file);

    /**
     * 주어진 파일이 파일 객체 저장소에서 관리되는 유효한 파일인지 여부를 반환한다.
     *
     * @param file	판별 대상 파일
     * @return	유효성 여부.
     */
    public boolean isVallidFile(File file);
}
