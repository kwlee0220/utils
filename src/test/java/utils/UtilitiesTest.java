package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * {@link Utilities}의 무작위 선택과 OS 판별 테스트.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UtilitiesTest {
	// --- shuffle / selectRandomly ---

	/**
	 * 입력을 바꾸지 않고, null 원소도 보존한다(2026-11-02 이전에는 입력을 null로 덮어쓰고 null 원소를 버렸다).
	 */
	@Test
	public void shuffle_keepsInputAndNulls() {
		List<String> input = new ArrayList<>(Arrays.asList("a", null, "c", "d"));
		List<String> copy = new ArrayList<>(input);

		List<String> shuffled = Utilities.shuffle(input);
		Assertions.assertEquals(copy, input);
		Assertions.assertEquals(4, shuffled.size());
		Assertions.assertEquals(new HashSet<>(copy), new HashSet<>(shuffled));

		// 수정 불가능한 입력도 받는다.
		Assertions.assertEquals(2, Utilities.shuffle(List.of("x", "y")).size());
	}

	/**
	 * 같은 시드의 난수 생성기를 주면 결과를 재현할 수 있다.
	 */
	@Test
	public void shuffle_reproducibleWithRandom() {
		List<Integer> input = List.of(1, 2, 3, 4, 5, 6, 7, 8);
		Assertions.assertEquals(Utilities.shuffle(input, new Random(42)), Utilities.shuffle(input, new Random(42)));
	}

	@Test
	public void selectRandomly_picksDistinctWithoutTouchingInput() {
		List<String> input = new ArrayList<>(List.of("a", "b", "c", "d", "e"));
		List<String> copy = new ArrayList<>(input);

		List<String> picked = Utilities.selectRandomly(input, 3, new Random(7));
		Assertions.assertEquals(copy, input);
		Assertions.assertEquals(3, picked.size());
		Assertions.assertEquals(3, new HashSet<>(picked).size());
		Assertions.assertTrue(input.containsAll(picked));

		// count가 원소 수보다 크면 전부, 0이면 빈 리스트.
		Assertions.assertEquals(new HashSet<>(input), new HashSet<>(Utilities.selectRandomly(input, 10)));
		Assertions.assertEquals(List.of(), Utilities.selectRandomly(input, 0));
		Assertions.assertEquals(List.of(), Utilities.selectRandomly(List.of(), 3));
	}

	@Test
	public void selectRandomly_rejectsInvalidArguments() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> Utilities.selectRandomly(List.of("a"), -1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Utilities.selectRandomly(null, 1));
		Assertions.assertThrows(IllegalArgumentException.class, () -> Utilities.shuffle(List.of("a"), null));
	}

	// --- isWindowsOS ---

	/**
	 * os.name이 "windows"로 시작할 때만 Windows로 본다(2026-11-02 이전에는 "win"이 포함되면 참이었다).
	 */
	@Test
	public void isWindowsOS_checksPrefix() {
		String saved = System.getProperty("os.name");
		try {
			System.setProperty("os.name", "Windows 11");
			Assertions.assertTrue(Utilities.isWindowsOS());
			System.setProperty("os.name", "Darwin");
			Assertions.assertFalse(Utilities.isWindowsOS());
			System.setProperty("os.name", "Linux");
			Assertions.assertFalse(Utilities.isWindowsOS());
		}
		finally {
			System.setProperty("os.name", saved);
		}
	}
}
