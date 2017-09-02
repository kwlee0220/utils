package utils.fostore;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileObjectCache<T extends FileObject> {
	private final Cache<String,T> m_cache;
	
	public static final <T extends FileObject> FileObjectCache<T> create(String name, int size) {
		Cache<String,T> cache = CacheBuilder.newBuilder()
											.maximumSize(size)
											.expireAfterAccess(300, TimeUnit.SECONDS)
											.build();
		
		return new FileObjectCache<T>(cache);
	}
	
	private FileObjectCache(Cache<String,T> cache) {
		m_cache = cache;
		
//		RegisteredEventListeners listeners = m_cache.getCacheEventNotificationService();
//		listeners.registerListener(new EvictHandler());
	}
	
	@SuppressWarnings("unchecked")
	public T get(String key) {
		return m_cache.getIfPresent(key);
	}
	
	public void put(String key, T fobj) {
		m_cache.put(key, fobj);
	}
	
	public void remove(String key) {
		m_cache.invalidate(key);
	}
	
	public void removeAll() {
		m_cache.invalidateAll();
	}
}
