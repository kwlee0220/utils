package utils.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JpaSession implements AutoCloseable {
	private final EntityManager m_em;
	private final EntityTransaction m_tx;
	private boolean m_failed = false;
	
	JpaSession(EntityManager em) {
		m_em = em;
		m_tx = em.getTransaction();
		m_tx.begin();
	}
	
	@Override
	public void close() {
		if ( m_failed ) {
			m_tx.rollback();
		}
		else {
			m_tx.commit();
		}
		m_em.clear();
	}
	
	public EntityManager getEntityManager() {
		return m_em;
	}
	
	public void markFailed() {
		m_failed = true;
	}
}