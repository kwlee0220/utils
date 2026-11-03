package utils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * 정수 배열의 순열을 사전순으로 하나씩 만들어 내는 반복자.
 * <p>
 * 첫 번째 결과는 입력 배열 그대로이고, 이어서 사전순으로 바로 다음 순열을 차례로 만든다. 따라서 입력보다 사전순으로
 * 뒤에 오는 순열만 만들어진다. <b>모든 순열을 얻으려면 입력을 오름차순으로 정렬해서 넘긴다.</b> 예를 들어
 * {@code [3, 2, 7]}을 넘기면 {@code [3,2,7]}, {@code [3,7,2]}, {@code [7,2,3]}, {@code [7,3,2]} 네 개만 나온다.
 * 같은 값이 여러 번 있으면 중복된 순열은 한 번만 만든다. 빈 배열이면 아무것도 만들지 않는다.
 * <p>
 * 생성할 때 입력 배열을 복사하므로 이후 입력 배열을 바꿔도 영향이 없다. {@link #next()}가 반환하는 배열도 매번 새로
 * 복사한 것이다. {@link #remove()}는 지원하지 않는다.
 * <pre>{@code
 * for ( int[] p: Permutation.create(new int[]{1, 2, 3}) ) {
 *     System.out.println(Arrays.toString(p));    // [1, 2, 3], [1, 3, 2], ..., [3, 2, 1]
 * }
 * }</pre>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class Permutation implements Iterator<int[]> {
	private final int[] m_numbers;
	private boolean m_done = false;
	
	/**
	 * 주어진 배열의 순열을 순회하는 {@link Iterable}을 반환한다.
	 * <p>
	 * {@code iterator()}를 부를 때마다 처음부터 다시 순회하는 새 {@link Permutation}을 만든다. 순열의 범위는 클래스
	 * 설명과 같다(입력보다 사전순으로 뒤에 오는 순열만, 모든 순열을 원하면 오름차순으로 정렬해서 넘긴다).
	 *
	 * @param numbers	순열을 만들 정수 배열. {@code iterator()}를 부를 때 복사된다.
	 * @return	순열을 순회하는 {@link Iterable}.
	 */
	public static Iterable<int[]> create(final int[] numbers) {
		return new Iterable<int[]>() {
			@Override
			public Iterator<int[]> iterator() {
				return new Permutation(numbers);
			}
		};
	}
	
	/**
	 * 주어진 배열에서 시작하는 순열 반복자를 생성한다.
	 *
	 * @param numbers	순열을 만들 정수 배열. 복사해서 보관한다.
	 */
	public Permutation(int[] numbers) {
		m_numbers = Arrays.copyOf(numbers, numbers.length);
		m_done = numbers.length == 0;
	}

	/**
	 * 아직 반환하지 않은 순열이 남아 있는지 반환한다.
	 *
	 * @return	남은 순열이 있으면 {@code true}.
	 */
	@Override
	public boolean hasNext() {
		return !m_done;
	}

	/**
	 * 다음 순열을 반환한다.
	 *
	 * @return	다음 순열. 매번 새로 복사한 배열이다.
	 * @throws NoSuchElementException	남은 순열이 없는 경우.
	 */
	@Override
	public int[] next() {
		if ( !m_done ) {
			int[] tmp = Arrays.copyOf(m_numbers, m_numbers.length);
			m_done = !nextPermutation(m_numbers);
			
			return tmp;
		}
		else {
			throw new NoSuchElementException();
		}
	}

	/**
	 * 지원하지 않는다.
	 *
	 * @throws UnsupportedOperationException	항상 발생한다.
	 */
	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	private static boolean nextPermutation(int[] p) {
		for (int a = p.length - 2; a >= 0; --a)
			if (p[a] < p[a + 1])
				for (int b = p.length - 1;; --b)
					if (p[b] > p[a]) {
						int t = p[a];
						p[a] = p[b];
						p[b] = t;
						for (++a, b = p.length - 1; a < b; ++a, --b) {
							t = p[a];
							p[a] = p[b];
							p[b] = t;
						}
						return true;
					}
		return false;
	}
	
	/**
	 * 사용 예: {@code [3, 2, 7]}에서 시작하는 순열을 출력한다.
	 *
	 * @param args	사용하지 않는다.
	 * @throws Exception	사용하지 않는다.
	 */
	public static final void main(String[] args) throws Exception {
		int[] data = new int[]{ 3, 2, 7};
		
		for ( int[] p: Permutation.create(data) ) { 
			System.out.println(Arrays.toString(p));
		}
	}
}
