package utils.fostore;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FileObjectCache<T extends FileObject> {
	private final Cache m_cache;
	
	public static final <T extends FileObject> FileObjectCache<T> create(String name, int size) {
		CacheManager manager = CacheManager.create();
		
		Cache cache = new Cache(name, size, MemoryStoreEvictionPolicy.LFU,
								false,	// overflow to disk
								null,	// diskStorePath
								true,	// eternal
								600,	// time to live (in seconds)
								300,	// time to idle (in seconds)
								false,	// disk persistent
								120,	// disk expiry thread interval (in seconds)
								null,	// registered event listeners
								null,	// bootstrapCacheLoader
								0);		// maxElementsonDisk
		manager.addCache(cache);
		
		return new FileObjectCache<T>(cache);
	}
	
	private FileObjectCache(Cache cache) {
		m_cache = cache;
		
//		RegisteredEventListeners listeners = m_cache.getCacheEventNotificationService();
//		listeners.registerListener(new EvictHandler());
	}
	
	@SuppressWarnings("unchecked")
	public T get(String key) {
		Element entry = m_cache.get(key);
		if ( entry != null ) {
			return (T)entry.getObjectValue();
		}
		else {
			return null;
		}
	}
	
	public void put(String key, T fobj) {
		m_cache.put(new Element(key, fobj));
	}
	
	public void remove(String key) {
		m_cache.remove(key);
	}
	
	public void removeAll() {
		m_cache.removeAll();
	}
}
