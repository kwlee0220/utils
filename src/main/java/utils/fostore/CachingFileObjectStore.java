package utils.fostore;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileObjectStore<K, T> implements FileObjectStore<K, T> {
	private final FileObjectStore<K, T> m_store;
	private final LoadingCache<K, T> m_cache;
	
	public CachingFileObjectStore(FileObjectStore<K,T> store, LoadingCache<K,T> cache) {
		m_store = store;
		m_cache = cache;
	}

	@Override
	public File getRootDir() {
		return m_store.getRootDir();
	}

	@Override
	public boolean exists(K key) {
		Preconditions.checkNotNull(key);
		
		T cached = m_cache.getIfPresent(key);
		if ( cached != null ) {
			return true;
		}
		else {
			return m_store.exists(key);
		}
	}

	@Override
	public Optional<T> get(K key) throws IOException, ExecutionException {
		Preconditions.checkNotNull(key);
		
		try {
			return Optional.of(m_cache.get(key));
		}
		catch ( UncheckedExecutionException e ) {
			if ( e.getCause() instanceof NoSuchElementException ) {
				return Optional.empty();
			}
			else {
				throw new ExecutionException(e.getCause());
			}
		}
	}

	@Override
	public Optional<File> getFile(K key) {
		Preconditions.checkNotNull(key);
		
		return m_store.getFile(key);
	}

	@Override
	public Optional<File> insert(K key, T fObj) throws IOException, ExecutionException {
		Preconditions.checkNotNull(key);

		Optional<File> inserted = m_store.insert(key, fObj);
		if ( inserted.isPresent() ) {
			m_cache.put(key, fObj);
		}
		
		return inserted;
	}

	@Override
	public File insertOrUpdate(K key, T fObj) throws IOException, ExecutionException {
		Preconditions.checkNotNull(key);

		File inserted = m_store.insertOrUpdate(key, fObj);
		m_cache.put(key, fObj);
		
		return inserted;
	}

	@Override
	public boolean remove(K key) throws IOException {
		Preconditions.checkNotNull(key);
		
		m_cache.invalidate(key);
		return m_store.remove(key);
	}

	@Override
	public void removeAll() throws IOException {
		m_cache.invalidateAll();
		m_store.removeAll();
	}

	@Override
	public Set<K> getFileObjectKeyAll() throws IOException {
		return m_store.getFileObjectKeyAll();
	}

	@Override
	public Set<K> findFileObjectKeyAll(Predicate<K> pred) throws IOException {
		Preconditions.checkNotNull(pred);
		
		return m_store.findFileObjectKeyAll(pred);
	}

	@Override
	public List<T> getFileObjectAll() throws IOException, ExecutionException {
		Set<K> keys = m_store.getFileObjectKeyAll();
		ImmutableMap<K, T> cacheds = m_cache.getAllPresent(keys);
		
		List<T> valueList = Lists.newArrayList();
		for ( K key: keys ) {
			T cached = cacheds.get(key);
			if ( cached != null ) {
				valueList.add(cached);
			}
			else {
				Optional<T> obj = m_store.get(key);
				if ( obj.isPresent() ) {
					valueList.add(obj.get());
				}
			}
		}
		
		return valueList;
	}

	@Override
	public Stream<K> traverseKeys() throws IOException {
		return m_store.traverseKeys();
	}
}
