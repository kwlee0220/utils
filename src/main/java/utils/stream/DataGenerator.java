package utils.stream;

import utils.Suppliable;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface DataGenerator<T> {
	public void generate(Suppliable<T> outChannel) throws Exception;
}
