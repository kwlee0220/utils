package utils.jpa;

import jakarta.persistence.EntityManager;


/**
 * 주어진 {@link EntityManager}를 사용하는 {@link JpaModule} 객체를 생성하는 팩토리.
 *
 * @param <T>	생성할 {@link JpaModule} 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public interface JpaModuleFactory<T extends JpaModule> {
	/**
	 * 주어진 {@link EntityManager}를 사용하는 {@link JpaModule} 객체를 생성한다.
	 *
	 * @param em	생성될 모듈이 사용할 {@link EntityManager}.
	 * @return	생성된 모듈 객체.
	 */
	public T newInstance(EntityManager em);
}
