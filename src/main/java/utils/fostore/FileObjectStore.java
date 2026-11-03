package utils.fostore;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;


/**
 * 식별자로 구분되는 객체(파일 객체)를 파일 단위로 저장/관리하는 저장소 인터페이스.
 *
 * @param <K>	파일 객체 식별자 타입.
 * @param <T>	파일 객체 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObjectStore<K,T> {
    /**
     * 최상위 디렉토리의 <code>File</code> 객체를 반환한다.
     * 
     * @return	최상위 파일(또는 디렉토리)의 <code>File</code> 객체.
     */
    public File getRootDir();
    
    /**
     * 주어진 식별자에 해당하는 파일 객체의 존재 여부를 반환한다.
     * 
     * @param key	대상 파일 객체의 식별자.
     * @return	존재 여부.
     */
    public boolean exists(K key);
    
    /**
     * 식별자에 해당하는 파일 객체를 반환한다.
     * 
     * @param key		검색 대상 식별자.
     * @return 파일 객체
     * @throws IOException	파일 객체 읽기에 실패한 경우.
	 * @throws ExecutionException	파일 내용에서 객체를 생성하는 과정에서 예외가 발생된 경우.
     */
    public Optional<T> get(K key) throws IOException, ExecutionException;

    /**
     * 식별자에 해당하는 {@link File}을 반환한다.
     * 
     * @param key		검색 대상 식별자.
     * @return 파일 객체
     */
    public Optional<File> getFile(K key);
    
    /**
     * 주어진 식별자로 파일 객체를 새로 저장한다.
     * <p>
     * 동일 식별자의 파일이 이미 존재하면 저장하지 않고 빈 {@link Optional}을 반환한다.
     *
     * @param key	저장할 파일 객체의 식별자.
     * @param fObj	저장할 파일 객체.
     * @return	저장된 경우 해당 파일, 이미 존재하여 저장하지 않은 경우 빈 {@link Optional}.
     * @throws IOException	파일 저장 중 입출력 오류가 발생한 경우.
     * @throws ExecutionException	파일 객체를 파일 내용으로 변환하는 과정에서 예외가 발생된 경우.
     */
    public Optional<File> insert(K key, T fObj) throws IOException, ExecutionException;

    /**
     * 주어진 식별자로 파일 객체를 저장한다. 이미 존재하면 새 내용으로 대체한다.
     *
     * @param key	저장할 파일 객체의 식별자.
     * @param fObj	저장할 파일 객체.
     * @return	파일 객체가 저장된 파일.
     * @throws IOException	파일 저장 중 입출력 오류가 발생한 경우.
     * @throws ExecutionException	파일 객체를 파일 내용으로 변환하는 과정에서 예외가 발생된 경우.
     */
    public File insertOrUpdate(K key, T fObj) throws IOException, ExecutionException;

    /**
     * 주어진 식별자에 해당하는 파일 객체를 삭제한다.
     * 
     * @param key	대상 파일 객체의 식별자.
     * @return	삭제여부.
     * @throws IOException	파일 객체 삭제가 실패한 경우
     */
    public boolean remove(K key) throws IOException;
    
    /**
     * 저장소의 모든 파일 객체를 삭제한다.
     *
     * @throws IOException	파일 삭제 중 오류가 발생한 경우.
     */
    public void removeAll() throws IOException;
    
    /**
     * 저장소에 저장된 모든 파일 객체의 식별자를 반환한다.
     *
     * @return	식별자 집합.
     * @throws IOException	저장소 디렉토리 탐색 중 오류가 발생한 경우.
     */
    public Set<K> getFileObjectKeyAll() throws IOException;

    /**
     * 저장소에 저장된 파일 객체 중 식별자가 주어진 조건을 만족하는 것들의 식별자를 반환한다.
     *
     * @param pred	식별자 선택 조건.
     * @return	조건을 만족하는 식별자 집합.
     * @throws IOException	저장소 디렉토리 탐색 중 오류가 발생한 경우.
     */
    public Set<K> findFileObjectKeyAll(Predicate<K> pred) throws IOException;

    /**
     * 저장소에 저장된 모든 파일 객체를 읽어 반환한다.
     *
     * @return	파일 객체 리스트.
     * @throws IOException	저장소 탐색 또는 파일 읽기 중 오류가 발생한 경우.
     * @throws ExecutionException	파일 내용에서 객체를 생성하는 과정에서 예외가 발생된 경우.
     */
    public List<T> getFileObjectAll() throws IOException, ExecutionException;
    
    /**
     * 저장소에 저장된 파일 객체들의 식별자를 차례로 제공하는 스트림을 반환한다.
     *
     * @return	식별자 스트림.
     * @throws IOException	저장소 디렉토리 탐색 중 오류가 발생한 경우.
     */
    public Stream<K> traverseKeys() throws IOException;
}