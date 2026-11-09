package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * 환경 변수 조회, {@link Iterable}·{@link Iterator}의 {@link Stream} 변환, 무작위 선택,
 * OS 판별 등 여러 곳에서 쓰는 잡다한 정적 유틸리티.
 *
 * @author Kang-Woo Lee
 */
public class Utilities {
	private Utilities() {
		throw new AssertionError("Should not be invoked!!: class=" + Utilities.class.getName());
	}
	
	/**
	 * 주어진 이름의 환경 변수 값을 반환한다.
	 *
	 * @param envVarName	환경 변수 이름.
	 * @return	환경 변수 값. 정의되어 있지 않으면 {@link Optional#empty()}.
	 * @throws NullPointerException	{@code envVarName}이 {@code null}인 경우.
	 */
	public static Optional<String> getEnvironmentVariable(String envVarName) {
		return Optional.ofNullable(System.getenv(envVarName));
	}
	
	/**
	 * 환경 변수 값을 경로로 보아 그 {@link File}을 반환한다.
	 * <p>
	 * 환경 변수가 정의되어 있지 않으면 {@link Optional#empty()}가 반환된다. 환경 변수는 있지만 그 경로가 존재하지
	 * 않으면 예외가 발생한다. 존재 여부({@link File#exists()})만 확인하므로 일반 파일과 디렉터리를 가리지 않는다.
	 * 디렉터리여야 한다면(예: {@code MDT_CLIENT_HOME}) 호출자가 {@link File#isDirectory()}로 따로 확인한다.
	 * 상대 경로는 현재 작업 디렉터리를 기준으로 해석된다.
	 * 
	 * @param	envVarName	환경 변수 이름
	 * @return	경로의 {@link File}. 환경 변수가 없으면 {@link Optional#empty()}.
	 * @throws NullPointerException	{@code envVarName}이 {@code null}인 경우.
	 * @throws IllegalArgumentException	환경 변수에 지정된 경로가 존재하지 않는 경우.
	 */
	public static Optional<File> getEnvironmentVariableFile(String envVarName) {
		return getEnvironmentVariable(envVarName)
					.flatMap(path -> {
						File file = new File(path);
						if ( file.exists() ) {
							return Optional.of(file);
						}
						else {
							String msg = String.format("EnvironmentVariable does not have a valid file: %s=%s",
														envVarName, path);
							throw new IllegalArgumentException(msg);
						}
					});
	}
	
	/**
	 * {@link Iterable}을 순차 {@link Stream}으로 변환한다.
	 * <p>
	 * {@link Iterable}과 {@link Iterator}를 모두 구현한 객체를 넘기면 {@link #stream(Iterator)}와의 오버로드가
	 * 모호해 컴파일되지 않으므로, 이때는 원하는 타입으로 형변환해서 넘긴다.
	 *
	 * @param <T>	원소의 타입.
	 * @param it	변환할 {@link Iterable}.
	 * @return	순차 스트림.
	 */
	public static <T> Stream<T> stream(Iterable<T> it) {
		return StreamSupport.stream(it.spliterator(), false);
	}
	
	/**
	 * {@link Iterator}를 순서가 있는 순차 {@link Stream}으로 변환한다.
	 * 스트림을 소비하면 주어진 반복자도 함께 소비된다. 오버로드 모호성은 {@link #stream(Iterable)}을 참조한다.
	 *
	 * @param <T>	원소의 타입.
	 * @param it	변환할 {@link Iterator}.
	 * @return	순차 스트림.
	 */
	public static <T> Stream<T> stream(Iterator<T> it) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false);
	}
	
	/**
	 * 컬렉션의 원소를 무작위 순서로 섞은 새 리스트를 반환한다. 입력은 바꾸지 않으며 {@code null} 원소도 그대로 포함한다.
	 * <p>
	 * 난수 생성기는 매번 새로 만든 {@link Random}이다. 결과를 재현하려면 {@link #shuffle(Collection, Random)}을 쓴다.
	 *
	 * @param <T>	원소의 타입.
	 * @param coll	섞을 원소들.
	 * @return	섞인 새 리스트(수정 가능).
	 * @throws IllegalArgumentException	{@code coll}이 {@code null}인 경우.
	 */
	public static <T> List<T> shuffle(Collection<? extends T> coll) {
		return shuffle(coll, new Random());
	}

	/**
	 * 주어진 난수 생성기로 컬렉션의 원소를 무작위 순서로 섞은 새 리스트를 반환한다. 입력은 바꾸지 않는다.
	 *
	 * @param <T>	원소의 타입.
	 * @param coll	섞을 원소들.
	 * @param rand	사용할 난수 생성기.
	 * @return	섞인 새 리스트(수정 가능).
	 * @throws IllegalArgumentException	{@code coll}이나 {@code rand}가 {@code null}인 경우.
	 */
	public static <T> List<T> shuffle(Collection<? extends T> coll, Random rand) {
		Preconditions.checkNotNullArgument(coll, "collection is null");
		Preconditions.checkNotNullArgument(rand, "random is null");

		List<T> shuffled = new ArrayList<>(coll);
		Collections.shuffle(shuffled, rand);
		return shuffled;
	}
	
	/**
	 * 컬렉션에서 중복 없이 {@code count}개의 원소를 무작위로 골라 새 리스트로 반환한다.
	 * <p>
	 * 입력은 바꾸지 않으며 {@code null} 원소도 고를 수 있다. 같은 원소를 두 번 고르지 않는다(위치 기준). {@code count}가
	 * 원소 수보다 크면 모든 원소를 무작위 순서로 반환한다. 난수 생성기는 매번 새로 만든 {@link Random}이다.
	 *
	 * @param <T>	원소의 타입.
	 * @param coll	원소를 고를 컬렉션.
	 * @param count	고를 원소 수.
	 * @return	고른 원소의 새 리스트(수정 가능, 고른 순서).
	 * @throws IllegalArgumentException	{@code coll}이 {@code null}이거나 {@code count}가 음수인 경우.
	 */
	public static <T> List<T> selectRandomly(Collection<? extends T> coll, int count) {
		return selectRandomly(coll, count, new Random());
	}

	/**
	 * 주어진 난수 생성기로 컬렉션에서 중복 없이 {@code count}개의 원소를 무작위로 골라 새 리스트로 반환한다.
	 * <p>
	 * 입력을 복사한 뒤 앞쪽 {@code count}개 자리에 대해서만 Fisher–Yates 섞기를 하므로, 복사 외의 비용은
	 * {@code count}에 비례한다.
	 *
	 * @param <T>	원소의 타입.
	 * @param coll	원소를 고를 컬렉션.
	 * @param count	고를 원소 수.
	 * @param rand	사용할 난수 생성기.
	 * @return	고른 원소의 새 리스트(수정 가능, 고른 순서).
	 * @throws IllegalArgumentException	{@code coll}이나 {@code rand}가 {@code null}이거나 {@code count}가 음수인 경우.
	 */
	public static <T> List<T> selectRandomly(Collection<? extends T> coll, int count, Random rand) {
		Preconditions.checkNotNullArgument(coll, "collection is null");
		Preconditions.checkNotNullArgument(rand, "random is null");
		Preconditions.checkArgument(count >= 0, "count must not be negative: %s", count);

		List<T> pool = new ArrayList<>(coll);
		int n = Math.min(count, pool.size());
		for ( int i = 0; i < n; ++i ) {
			Collections.swap(pool, i, i + rand.nextInt(pool.size() - i));
		}
		return new ArrayList<>(pool.subList(0, n));
	}

	/**
	 * 시스템 속성 {@code os.name}이 {@code "windows"}로 시작하는지(대소문자 무시)로 Windows 여부를 판단한다.
	 *
	 * @return	Windows이면 {@code true}. {@code os.name}이 없으면 {@code false}.
	 */
	public static boolean isWindowsOS() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}
}