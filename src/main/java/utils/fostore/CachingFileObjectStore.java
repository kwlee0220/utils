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

import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.UncheckedExecutionException;

import utils.Preconditions;


/**
 * 다른 {@link FileObjectStore}를 감싸고 {@link LoadingCache}로 파일 객체를 캐싱하는 저장소.
 * <p>
 * {@link #get(Object)}는 캐시를 통해 객체를 얻고, 삽입/갱신/삭제 연산은 원본 저장소에
 * 반영한 뒤 캐시를 함께 갱신(또는 무효화)한다. 그 밖의 연산은 원본 저장소에 그대로 위임한다.
 * 캐시의 로더는 호출자가 구성하며, 대상 객체가 없을 때 {@link NoSuchElementException}을
 * 던지도록 구성하면 {@link #get(Object)}가 빈 {@link Optional}을 반환한다.
 *
 * @param <K>	파일 객체 식별자 타입.
 * @param <T>	파일 객체 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public class CachingFileObjectStore<K, T> implements FileObjectStore<K, T> {
	private final FileObjectStore<K, T> m_store;
	private final LoadingCache<K, T> m_cache;
	
	/**
	 * 캐싱 파일 객체 저장소를 생성한다.
	 * <p>
	 * 인자의 null 여부는 검사하지 않는다.
	 *
	 * @param store	실제 파일 객체를 저장하는 원본 저장소.
	 * @param cache	파일 객체 캐시. 로더는 보통 {@code store}에서 객체를 읽도록 구성한다.
	 */
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
		Preconditions.checkNotNullArgument(key, "key is null");
		
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
		Preconditions.checkNotNullArgument(key, "key is null");
		
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
		Preconditions.checkNotNullArgument(key, "key is null");
		
		return m_store.getFile(key);
	}

	@Override
	public Optional<File> insert(K key, T fObj) throws IOException, ExecutionException {
		Preconditions.checkNotNullArgument(key, "key is null");

		Optional<File> inserted = m_store.insert(key, fObj);
		if ( inserted.isPresent() ) {
			m_cache.put(key, fObj);
		}
		
		return inserted;
	}

	@Override
	public File insertOrUpdate(K key, T fObj) throws IOException, ExecutionException {
		Preconditions.checkNotNullArgument(key, "key is null");

		File inserted = m_store.insertOrUpdate(key, fObj);
		m_cache.put(key, fObj);
		
		return inserted;
	}

	@Override
	public boolean remove(K key) throws IOException {
		Preconditions.checkNotNullArgument(key, "key is null");
		
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
		Preconditions.checkNotNullArgument(pred, "pred is null");
		
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
