package utils.jpa;

import jakarta.persistence.EntityManager;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JpaModuleFactory<T extends JpaModule> {
	public T newInstance(EntityManager em);
}
