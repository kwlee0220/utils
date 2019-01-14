package utils.fostore;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
	 */
    public FileObjectStore(File rootDir, FileObjectHandler<K,T> handler) {
    	Objects.requireNonNull(rootDir, "Root directory of this FileObjectStore");
    	Objects.requireNonNull(handler, "FileObjectHandler");
    	
    	m_rootDir = rootDir;
    	if ( !m_rootDir.exists() ) {
    		try {
        		FileUtils.forceMkdir(m_rootDir);
			}
			catch ( IOException e ) {
				throw new FileObjectStoreException(e);
			}
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
    public synchronized boolean exists(K key) {
    	Objects.requireNonNull(key, "FileObject key");

		return m_handler.toFile(key).exists();
    }
    
    /**
     * 식별자에 해당하는 파일 객체를 반환한다.
     * 
     * @param key		검색 대상 식별자.
     * @return 파일 객체
     * @throws IOException	파일 객체 획득에 실패한 경우.
     */
    public synchronized FOption<T> get(K key) throws IOException  {
    	Objects.requireNonNull(key, "FileObject key");

		File file = m_handler.toFile(key);
		if ( file.exists() ) {
			return FOption.of(m_handler.readFileObject(file));
		}
		else {
			return FOption.empty();
		}
    }
    
    public T getOrNull(K key) throws IOException {
    	return get(key).getOrNull();
    }
    
    public synchronized FOption<File> getFile(K key) {
    	Objects.requireNonNull(key, "FileObject key");
    	
		File file = m_handler.toFile(key);
		return file.exists() ? FOption.of(file) : FOption.empty();
    }
    
    public File insert(K key, T fObj) throws IOException, FileObjectExistsException {
    	Objects.requireNonNull(key, "FileObject key");
    	Objects.requireNonNull(fObj, "FileObject");
    	
        return insert(key, fObj, false);
    }
    
    public synchronized File insert(K key, T fObj, boolean updateIfExists)
    	throws IOException, FileObjectExistsException {
    	Objects.requireNonNull(key, "FileObject key");
    	Objects.requireNonNull(fObj, "FileObject");
		
    	File file = m_handler.toFile(key);
    	if ( file.exists() && !updateIfExists ) {
	        throw new FileObjectExistsException("File[id=" + key + ", path="
	        									+ file.getAbsolutePath() + "]");
    	}
    	
    	FileUtils.forceMkdirParent(file);
    	m_handler.writeFileObject(fObj, file);
    	
    	return file;
    }
    
    public synchronized void remove(K key) {
    	Objects.requireNonNull(key, "FileObject key");
		
		File file = m_handler.toFile(key);
		if ( !file.exists() ) {
			return;
		}
		
		if ( !file.delete() ) {
			s_logger.warn("fails to delete file " + file);
		}
    }
    
    public synchronized void removeAll() throws IOException {
		for ( File file: m_rootDir.listFiles() ) {
			FileUtils.deleteDirectory(file);
		}
    }
    
    public FStream<K> getFileObjectKeyAll() {
    	return getObjectFileAll().map(m_handler::toFileObjectKey);
    }
    
    public FStream<File> getObjectFileAll() {
    	try {
			return utils.io.FileUtils.walk(m_rootDir);
		}
		catch ( Exception e ) {
			throw new FileObjectExistsException("" + e);
		}
    }
    
    public void traverse(FileObjectVisitor<K> visitor) {
    	Objects.requireNonNull(visitor, "FileObjectVisitor is null");
		
    	traverseDirectory(m_rootDir, visitor);
    }
    
    public Stream<K> traverse() throws IOException {
    	return Files.walk(m_rootDir.toPath())
		    		.map(Path::toFile)
		    		.filter(m_handler::isVallidFile)
		    		.map(m_handler::toFileObjectKey);
    }
    
    private void traverseDirectory(File dir, FileObjectVisitor<K> visitor) {
		final List<File> subDirList = new ArrayList<File>();
		File[] files = dir.listFiles(new FileFilter() {
										public boolean accept(File path) {
											if ( path.isDirectory() ) {
												subDirList.add(path);
												return false;
											}
											else {
												return m_handler.isVallidFile(path);
											}
										}
									});
		assert files != null;
		
		for ( int i =0; i < files.length; ++i ) {
			visitor.visit(m_handler.toFileObjectKey(files[i]));
		}
		for ( int i =0; i < subDirList.size(); ++i ) {
			File subDir = (File)subDirList.get(i);
			
			traverseDirectory(subDir, visitor);
		}
    }
}