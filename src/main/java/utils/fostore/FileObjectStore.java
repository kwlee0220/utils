package utils.fostore;

import static utils.Utilities.checkNotNullArgument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.func.FOption;
import utils.stream.FStream;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileObjectStore<K,T> {
    private static final Logger s_logger = LoggerFactory.getLogger(FileObjectStore.class);
    
	private final File m_rootDir;
	private final FileObjectHandler<K,T> m_handler;
	
	/**
	 * 파일 객체 저장소를 생성한다.
	 * 
	 * @param rootDir	저장소가 사용할 최상위 디렉토리.
	 * @param handler	저장될 파일 객체의 인터페이스.
	 * @throws IOException	 파일 객체 저장소 생성 중 오류가 발생한 경우.
	 * 					특히 저장소 디렉토리 생성에 실패한 경우.
	 */
    public FileObjectStore(File rootDir, FileObjectHandler<K,T> handler) throws IOException {
    	checkNotNullArgument(rootDir, "Root directory of this FileObjectStore");
    	checkNotNullArgument(handler, "FileObjectHandler");
    	
    	m_rootDir = rootDir;
    	if ( !m_rootDir.exists() ) {
    		FileUtils.forceMkdir(m_rootDir);
    	}
    	
    	m_handler = handler;
    }
    
    /**
     * 최상위 디렉토리의 <code>File</code> 객체를 반환한다.
     * 
     * @return	최상위 파일(또는 디렉토리)의 <code>File</code> 객체.
     */
    public File getRootDir() {
    	return m_rootDir;
    }
    
    /**
     * 주어진 식별자에 해당하는 파일 객체의 존재 여부를 반환한다.
     * 
     * @param key	대상 파일 객체의 식별자.
     * @return	존재 여부.
     */
    public boolean exists(K key) {
    	checkNotNullArgument(key, "FileObject key");

		return m_handler.toFile(key).exists();
    }
    
    /**
     * 식별자에 해당하는 파일 객체를 반환한다.
     * 
     * @param key		검색 대상 식별자.
     * @return 파일 객체
     * @throws IOException	파일 객체 읽기에 실패한 경우.
     */
    public FOption<T> get(K key) throws IOException  {
    	return getFile(key).mapOrThrow(m_handler::readFileObject);
    }

    /**
     * 식별자에 해당하는 {@link File}을 반환한다.
     * 
     * @param key		검색 대상 식별자.
     * @return 파일 객체
     */
    public FOption<File> getFile(K key) {
    	checkNotNullArgument(key, "FileObject key");

		File file = m_handler.toFile(key);
		return file.exists() ? FOption.of(file) : FOption.empty();
    }
    
    public File insert(K key, T fObj) throws IOException {
        return insert(key, fObj, false);
    }
    
    public File insert(K key, T fObj, boolean updateIfExists) throws IOException {
    	checkNotNullArgument(key, "FileObject key");
    	checkNotNullArgument(fObj, "FileObject");

    	File file = m_handler.toFile(key);
    	if ( file.exists() ) {
    		if ( updateIfExists ) {
    			file.delete();
    		}
    		else {
    	        throw new FileExistsException("File[id=" + key + ", path="
												+ file.getAbsolutePath() + "]");
    		}
    	}
    	else {
        	FileUtils.forceMkdirParent(file);
    	}
    	m_handler.writeFileObject(fObj, file);
    	
    	return file;
    }

    /**
     * 주어진 식별자에 해당하는 파일 객체를 삭제한다.
     * 
     * @param key	대상 파일 객체의 식별자.
     * @return	삭제여부.
     * @throws IOException	파일 객체 삭제가 실패한 경우
     */
    public boolean remove(K key) throws IOException {
    	checkNotNullArgument(key, "FileObject key");

		File file = m_handler.toFile(key);
		if ( !file.exists() ) {
			return false;
		}
		
		if ( file.delete() ) {
			return true;
		}
		else {
			throw new IOException("fails to delete file: path=" + file);
		}
    }
    
    public void removeAll() throws IOException {
		for ( File file: m_rootDir.listFiles() ) {
			FileUtils.deleteDirectory(file);
		}
    }
    
    public Set<K> getFileObjectKeyAll() throws IOException {
    	return streamFileObjectAll().map(m_handler::toFileObjectKey).toSet();
    }
    
    public Set<K> findFileObjectKeyAll(Predicate<K> pred) throws IOException {
    	return streamFileObjectAll()
    				.map(m_handler::toFileObjectKey)
    				.filter(pred)
    				.toSet();
    }
    
    public List<File> getFileObjectAll() throws IOException {
		return streamFileObjectAll().toList();
    }
    
    private FStream<File> streamFileObjectAll() throws IOException {
		return utils.io.FileUtils.walk(m_rootDir).filter(m_handler::isVallidFile);
    }
    
    public Stream<K> traverse() throws IOException {
    	return Files.walk(m_rootDir.toPath())
		    		.map(Path::toFile)
		    		.filter(m_handler::isVallidFile)
		    		.map(m_handler::toFileObjectKey);
    }
}