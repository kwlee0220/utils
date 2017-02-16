package utils.fostore;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import utils.ExceptionUtils;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileObjectStore<T extends FileObject> {
    private static final Logger s_logger = Logger.getLogger(FileObjectStore.class);
    
	private final File m_rootDir;
	private final FileObjectHandler<T> m_handler;
	private final FileObjectCache<T> m_cache;
	
	/**
	 * 파일 객체 저장소를 생성한다.
	 * 
	 * @param rootDir	저장소가 사용할 최상위 디렉토리.
	 * @param handler	저장될 파일 객체의 인터페이스.
	 * @param mapper	파일 객체의 식별자와 해당 파일 이름 사이의 매퍼 객체.
	 * @param cacheSize	파일 객체 캐쉬. 음수 또는 0인 경우는 캐쉬를 사용하지 않는 것을 간주한다.
	 */
    public FileObjectStore(File rootDir, FileObjectHandler<T> handler, int cacheSize) {
    	m_rootDir = rootDir;
    	m_handler = handler;
    	if ( cacheSize > 0 ) {
    		String cacheName = "focache" + System.identityHashCode(this);
    		m_cache = FileObjectCache.create(cacheName, cacheSize);
    	}
    	else {
    		m_cache = null;
    	}
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
     * @param id	대상 파일 객체의 식별자.
     * @return	존재 여부.
     * @throws IllegalArgumentException	<code>id</code>가 <code>null</code>인 경우.
     */
    public boolean exists(String id) {
    	if ( id == null ) {
    		throw new IllegalArgumentException("Object id was null");
    	}

		return m_handler.toFile(id).exists();
    }
    
    /**
     * 식별자에 해당하는 파일 객체를 반환한다.
     * 
     * @param id		검색 대상 식별자.
     * @return 파일 객체
     * @throws IllegalArgumentException	<code>id</code>가 <code>null</code>인 경우.
     * @throws FileObjectStoreException	파일 객체 획득에 실패한 경우. 자세한 원인은
     * 								{@link FileObjectStoreException#getCause()}를 통해
     * 								원인 예외 객체를 획득할 수 있다.
     * @throws FileObjectNotFoundException 식별자에 해당하는 파일 객체가 없는 경우.
     */
    public T get(String id) throws FileObjectNotFoundException, FileObjectStoreException  {
    	if ( id == null ) {
    		throw new IllegalArgumentException("Object id was null");
    	}

		T fObj = (m_cache != null) ? m_cache.get(id) : null;
		if ( fObj == null ) {
			File file = m_handler.toFile(id);
			if ( !file.exists() ) {
				throw new FileObjectNotFoundException("id=" + id);
			}
			
			try {
				fObj = m_handler.newFileObject(file);
			}
			catch ( Exception e ) {
				throw new FileObjectStoreException(ExceptionUtils.unwrapThrowable(e));
			}
			
			if ( m_cache != null ) {
				m_cache.put(id, fObj);
			}
		}

		return fObj;
    }
    
    public File getFile(String id) throws FileObjectNotFoundException {
    	if ( id == null ) {
    		throw new IllegalArgumentException("Object id was null");
    	}
    	
		File file = m_handler.toFile(id);
		if ( !file.exists() ) {
			throw new FileObjectNotFoundException("id=" + id);
		}
		
		return file;
    }
    
    public void insert(String id, T fObj) throws IOException, FileObjectExistsException {
        insert(id, fObj, false);
    }
    
    public void insert(String id, T fObj, boolean updateIfExists)
    	throws IOException, FileObjectExistsException {
		if ( id == null ) {
			throw new IllegalArgumentException("Object id was null");
		}
		if ( fObj == null ) {
			throw new IllegalArgumentException("FileObject was null");
		}
		
    	File file = m_handler.toFile(id);
    	if ( file.exists() ) {
    	    if ( updateIfExists && m_cache != null ) {
    	    	m_cache.put(id, fObj);
    	    }
    	    else {
    	        throw new FileObjectExistsException("File[id=" + id + ", path="
    	        									+ file.getAbsolutePath() + "]");
    	    }
    	}
    	
    	Files.createFile(file.toPath());
    	fObj.save(file);
    }
    
    public void remove(String id) {
		if ( id == null ) {
			throw new IllegalArgumentException("Object id was null");
		}
		
		if ( m_cache != null ) {
			m_cache.remove(id);
		}
		
		File file = m_handler.toFile(id);
		if ( !file.exists() ) {
			return;
		}
		
		try {
			FileObject fObj = m_handler.newFileObject(file);
			fObj.destroy();
		}
		catch ( Exception e ) {
			s_logger.warn("fails to destroy FileObject: id=" + id, ExceptionUtils.unwrapThrowable(e));
		}
		
		boolean done = file.delete();
		if ( !done ) {
			s_logger.warn("fails to delete file " + file);
		}
    }
    
    public void removeAll() throws IOException {
		if ( m_cache != null ) {
			m_cache.removeAll();
		}
		
		for ( File file: m_rootDir.listFiles() ) {
			FileUtils.deleteDirectory(file);
		}
    }
    
    public void traverse(FileObjectVisitor visitor) {
		if ( visitor == null ) {
			throw new NullPointerException("FileObject visitor was null");
		}
		
    	traverseDirectory(m_rootDir, visitor);
    }
    
    private void traverseDirectory(File dir, FileObjectVisitor visitor) {
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
			visitor.visit(m_handler.toFileObjectId(files[i]));
		}
		for ( int i =0; i < subDirList.size(); ++i ) {
			File subDir = (File)subDirList.get(i);
			
			traverseDirectory(subDir, visitor);
		}
    }
}