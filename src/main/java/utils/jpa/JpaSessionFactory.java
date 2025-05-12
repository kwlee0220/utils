package utils.jpa;

import java.util.function.Consumer;

import com.google.common.base.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JpaSessionFactory {
	public EntityManagerFactory getEntityManagerFactory();
	
	public default JpaSession allocateSession() {
		EntityManager em = getEntityManagerFactory().createEntityManager();
		return JpaContext.allocate(em);
	}
	
	public default <T> T getInJpaSession(Function<EntityManager, T> supplier) {
		try ( JpaSession session = allocateSession() ) {
			return supplier.apply(session.getEntityManager());
		}
	}
	
	public default void runInJpaSession(Consumer<EntityManager> consumer) {
		try ( JpaSession session = allocateSession() ) {
			consumer.accept(session.getEntityManager());
		}
	}
}
