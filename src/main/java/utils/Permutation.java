package utils;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Permutation implements Iterator<int[]> {
	private final int[] m_numbers;
	private boolean m_done = false;
	
	public static Iterable<int[]> create(final int[] numbers) {
		return new Iterable<int[]>() {
			@Override
			public Iterator<int[]> iterator() {
				return new Permutation(numbers);
			}
		};
	}
	
	public Permutation(int[] numbers) {
		m_numbers = Arrays.copyOf(numbers, numbers.length);
		m_done = numbers.length == 0;
	}

	@Override
	public boolean hasNext() {
		return !m_done;
	}

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
	
	public static final void main(String[] args) throws Exception {
		int[] data = new int[]{ 3, 2, 7};
		
		for ( int[] p: Permutation.create(data) ) { 
			System.out.println(Arrays.toString(p));
		}
	}
}
