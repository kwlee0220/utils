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
 * 
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
    
    public Optional<File> insert(K key, T fObj) throws IOException, ExecutionException;
    public File insertOrUpdate(K key, T fObj) throws IOException, ExecutionException;

    /**
     * 주어진 식별자에 해당하는 파일 객체를 삭제한다.
     * 
     * @param key	대상 파일 객체의 식별자.
     * @return	삭제여부.
     * @throws IOException	파일 객체 삭제가 실패한 경우
     */
    public boolean remove(K key) throws IOException;
    
    public void removeAll() throws IOException;
    
    public Set<K> getFileObjectKeyAll() throws IOException;
    public Set<K> findFileObjectKeyAll(Predicate<K> pred) throws IOException;
    public List<T> getFileObjectAll() throws IOException, ExecutionException;
    
    public Stream<K> traverseKeys() throws IOException;
}