package utils.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;


/**
 * 하나의 {@link EntityManager}와 그 트랜잭션을 묶은 JPA 세션.
 * <p>
 * 세션은 생성 시 트랜잭션을 시작하고, {@link #close()} 시 실패 표시 여부에 따라
 * 커밋 또는 롤백한다. 세션은 {@link JpaContext#allocate(EntityManager)}를 통해 생성된다.
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
	
	/**
	 * 세션을 종료한다.
	 * <p>
	 * {@link #markFailed()}가 호출된 경우 트랜잭션을 롤백하고, 그렇지 않으면 커밋한다.
	 * 이후 {@link EntityManager#clear()}를 호출한다.
	 * <p>
	 * <b>주의:</b> {@link EntityManager}를 닫지 않으며, {@link JpaContext}의 세션 스택에서
	 * 이 세션을 제거하지도 않는다.
	 */
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
	
	/**
	 * 이 세션이 사용하는 {@link EntityManager}를 반환한다.
	 *
	 * @return	{@link EntityManager} 객체.
	 */
	public EntityManager getEntityManager() {
		return m_em;
	}
	
	/**
	 * 세션을 실패로 표시한다. 표시된 세션은 {@link #close()} 시 트랜잭션을 롤백한다.
	 */
	public void markFailed() {
		m_failed = true;
	}
}