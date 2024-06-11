package utils.jdbc.crud;

import java.util.List;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface DaoList<T> extends List<T> {
	public T newElementInstance();
}
