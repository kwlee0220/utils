package utils.jpa;

import java.util.function.Consumer;

import com.google.common.base.Function;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * {@link EntityManagerFactory}를 이용해 {@link JpaSession}을 할당하는 팩토리 인터페이스.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface JpaSessionFactory {
	/**
	 * 세션 생성에 사용할 {@link EntityManagerFactory}를 반환한다.
	 *
	 * @return	{@link EntityManagerFactory} 객체.
	 */
	public EntityManagerFactory getEntityManagerFactory();
	
	/**
	 * 새 {@link EntityManager}를 생성하고 이를 사용하는 {@link JpaSession}을 할당한다.
	 * <p>
	 * 할당된 세션은 현재 쓰레드의 {@link JpaContext} 세션 스택에 추가되며,
	 * 트랜잭션이 시작된 상태로 반환된다.
	 *
	 * @return	할당된 세션.
	 */
	public default JpaSession allocateSession() {
		EntityManager em = getEntityManagerFactory().createEntityManager();
		return JpaContext.allocate(em);
	}
	
	/**
	 * 새로 할당한 세션 안에서 작업을 수행하고 그 결과를 반환한다. 작업이 끝나면 세션을 닫는다.
	 * <p>
	 * <b>주의:</b> 작업이 예외를 던져도 세션을 실패로 표시하지 않으므로, 세션 종료 시
	 * 트랜잭션은 커밋된다.
	 *
	 * @param <T>	작업 결과 타입.
	 * @param supplier	{@link EntityManager}를 인자로 받아 결과를 반환하는 작업.
	 * @return	작업 결과.
	 */
	public default <T> T getInJpaSession(Function<EntityManager, T> supplier) {
		try ( JpaSession session = allocateSession() ) {
			return supplier.apply(session.getEntityManager());
		}
	}
	
	/**
	 * 새로 할당한 세션 안에서 작업을 수행한다. 작업이 끝나면 세션을 닫는다.
	 * <p>
	 * <b>주의:</b> 작업이 예외를 던져도 세션을 실패로 표시하지 않으므로, 세션 종료 시
	 * 트랜잭션은 커밋된다.
	 *
	 * @param consumer	{@link EntityManager}를 인자로 받아 수행할 작업.
	 */
	public default void runInJpaSession(Consumer<EntityManager> consumer) {
		try ( JpaSession session = allocateSession() ) {
			consumer.accept(session.getEntityManager());
		}
	}
}
