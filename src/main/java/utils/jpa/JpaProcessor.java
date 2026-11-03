package utils.jpa;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.base.Preconditions;

import utils.func.CheckedSupplierX;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

/**
 * 작업마다 새 {@link EntityManager}를 생성하고 트랜잭션 안에서 작업을 수행하는 처리기.
 * <p>
 * 모든 메소드는 {@link EntityManager}를 생성하여 트랜잭션을 시작한 뒤 작업을 수행하고,
 * 작업이 정상 종료되면 커밋한다. 작업이 예외를 던져 트랜잭션이 활성 상태로 남으면
 * 롤백하며, 어느 경우든 마지막에 {@link EntityManager}를 닫는다.
 * 작업에서 발생한 예외는 그대로 전파된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JpaProcessor {
	private final EntityManagerFactory m_emFact;
	
	/**
	 * 처리기를 생성한다.
	 * <p>
	 * 인자의 null 여부는 검사하지 않는다.
	 *
	 * @param emFact	작업마다 {@link EntityManager}를 생성할 팩토리.
	 */
	public JpaProcessor(EntityManagerFactory emFact) {
		m_emFact = emFact;
	}
	
	/**
	 * 새 {@link EntityManager}의 트랜잭션 안에서 주어진 작업을 수행한다.
	 *
	 * @param task	{@link EntityManager}를 인자로 받아 수행할 작업.
	 * @throws IllegalArgumentException	{@code task}가 {@code null}인 경우.
	 */
	public void run(Consumer<EntityManager> task) {
		Preconditions.checkArgument(task != null, "task is null");
		
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		
		try {
			task.accept(em);
			tx.commit();
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			em.close();
		}
	}
	
	/**
	 * 새 {@link EntityManager}의 트랜잭션 안에서 주어진 작업을 수행하고 그 결과를 반환한다.
	 *
	 * @param <T>	작업 결과 타입.
	 * @param task	{@link EntityManager}를 인자로 받아 결과를 반환하는 작업.
	 * @return	작업 결과.
	 * @throws IllegalArgumentException	{@code task}가 {@code null}인 경우.
	 */
	public <T> T get(Function<EntityManager,T> task) {
		Preconditions.checkArgument(task != null, "task is null");
		
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		
		try {
			T result = task.apply(em);
			tx.commit();
			return result;
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			em.close();
		}
	}
	
//	public <M extends JpaModule,T> T get(JpaModuleFactory<M> moduleFact, Function<M,T> task) {
//		EntityManager em = m_emFact.createEntityManager();
//		EntityTransaction tx = em.getTransaction();
//		tx.begin();
//		
//		M jmodule = moduleFact.newInstance(em);
//		try {
//			T result = task.apply(jmodule);
//			tx.commit();
//			
//			return result;
//		}
//		finally {
//			if ( tx.isActive() ) {
//				tx.rollback();
//			}
//			
//			if ( jmodule instanceof AutoCloseable ) {
//				AutoCloseable ac = (AutoCloseable)jmodule;
//				Try.run(ac::close);
//			}
//			em.close();
//		}
//	}
//	
//	public <M extends JpaModule> void run(JpaModuleFactory<M> moduleFact, Consumer<M> task) {
//		EntityManager em = m_emFact.createEntityManager();
//		EntityTransaction tx = em.getTransaction();
//		tx.begin();
//		
//		M jmodule = moduleFact.newInstance(em);
//		try {
//			task.accept(jmodule);
//			tx.commit();
//		}
//		finally {
//			if ( tx.isActive() ) {
//				tx.rollback();
//			}
//			
//			if ( jmodule instanceof AutoCloseable ) {
//				AutoCloseable ac = (AutoCloseable)jmodule;
//				Try.run(ac::close);
//			}
//			em.close();
//		}
//	}
//	
	
	/**
	 * 새 {@link EntityManager}를 주어진 {@link ThreadLocal}에 설정한 상태에서
	 * 트랜잭션 안에서 작업을 수행하고 그 결과를 반환한다.
	 * <p>
	 * 작업이 끝나면 {@code holder}에서 {@link EntityManager}를 제거한다.
	 * 인자의 null 여부는 검사하지 않는다.
	 *
	 * @param <T>	작업 결과 타입.
	 * @param holder	작업 동안 {@link EntityManager}를 보관할 {@link ThreadLocal}.
	 * @param task	수행할 작업.
	 * @return	작업 결과.
	 */
	public <T> T get(ThreadLocal<EntityManager> holder, Supplier<T> task) {
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		
		holder.set(em);
		try {
			T result = task.get();
			tx.commit();
			
			return result;
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			
			holder.remove();
			em.close();
		}
	}
	
	/**
	 * 새 {@link EntityManager}를 주어진 {@link ThreadLocal}에 설정한 상태에서
	 * 트랜잭션 안에서 작업을 수행한다.
	 * <p>
	 * 작업이 끝나면 {@code holder}에서 {@link EntityManager}를 제거한다.
	 * 인자의 null 여부는 검사하지 않는다.
	 *
	 * @param holder	작업 동안 {@link EntityManager}를 보관할 {@link ThreadLocal}.
	 * @param task	수행할 작업.
	 */
	public void run(ThreadLocal<EntityManager> holder, Runnable task) {
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		
		holder.set(em);
		try {
			task.run();
			tx.commit();
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			
			holder.remove();
			em.close();
		}
	}
	
	/**
	 * 새 {@link EntityManager}를 주어진 {@link JpaModule}에 설정한 상태에서
	 * 트랜잭션 안에서 작업을 수행하고 그 결과를 반환한다.
	 * <p>
	 * 작업이 끝나면 모듈의 {@link EntityManager}를 {@code null}로 되돌린다.
	 * <p>
	 * <b>주의:</b> {@code jmodule}이 {@code null}이면 인자 검사를 통과한 뒤
	 * {@link NullPointerException}이 발생한다. 또한 작업 중 모듈의 {@link EntityManager}가
	 * 다른 것으로 바뀌면 종료 처리 과정에서 {@link IllegalStateException}이 발생하며,
	 * 이 예외가 작업에서 발생한 예외를 대체하고 모듈의 {@link EntityManager}는 해제되지 않는다.
	 *
	 * @param <J>	모듈 타입.
	 * @param <T>	작업 결과 타입.
	 * @param <X>	작업이 던질 수 있는 예외 타입.
	 * @param jmodule	작업 동안 {@link EntityManager}를 설정할 모듈.
	 * @param task	수행할 작업.
	 * @return	작업 결과.
	 * @throws X	작업 수행 중 예외가 발생한 경우.
	 * @throws IllegalArgumentException	모듈에 이미 {@link EntityManager}가 설정된 경우.
	 * @throws IllegalStateException	작업 중 모듈의 {@link EntityManager}가 교체된 경우.
	 */
	public <J extends JpaModule, T,X extends Throwable> T get(J jmodule, CheckedSupplierX<T,X> task) throws X {
		Preconditions.checkArgument(jmodule == null || jmodule.getEntityManager() == null,
									"JpaModule has been allocated already: module=" + jmodule);
		
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();

		jmodule.setEntityManager(em);
		try {
			T result = task.get();
			tx.commit();
			
			return result;
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			em.close();
			
			Preconditions.checkState(em == jmodule.getEntityManager(),
									"JpaModule has been replaced: module=" + jmodule);
			jmodule.setEntityManager(null);
		}
	}
	
	/**
	 * 새 {@link EntityManager}를 주어진 {@link JpaModule}에 설정한 상태에서
	 * 트랜잭션 안에서 작업을 수행한다.
	 * <p>
	 * 작업이 끝나면 모듈의 {@link EntityManager}를 {@code null}로 되돌린다.
	 * <p>
	 * <b>주의:</b> 작업 중 모듈의 {@link EntityManager}가 다른 것으로 바뀌면 종료 처리 과정에서
	 * {@link IllegalStateException}이 발생하며, 이 예외가 작업에서 발생한 예외를 대체하고
	 * 모듈의 {@link EntityManager}는 해제되지 않는다.
	 *
	 * @param <J>	모듈 타입.
	 * @param jmodule	작업 동안 {@link EntityManager}를 설정할 모듈.
	 * @param task	수행할 작업.
	 * @throws IllegalArgumentException	{@code jmodule} 또는 {@code task}가 {@code null}이거나,
	 * 					모듈에 이미 {@link EntityManager}가 설정된 경우.
	 * @throws IllegalStateException	작업 중 모듈의 {@link EntityManager}가 교체된 경우.
	 */
	public <J extends JpaModule> void run(J jmodule, Runnable task) {
		Preconditions.checkArgument(jmodule != null, "JpaModule is null");
		Preconditions.checkArgument(jmodule.getEntityManager() == null,
									"JpaModule has been allocated already: module=" + jmodule);
		Preconditions.checkArgument(task != null, "task is null");
		
		EntityManager em = m_emFact.createEntityManager();
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		
		jmodule.setEntityManager(em);
		try {
			task.run();
			tx.commit();
		}
		finally {
			if ( tx.isActive() ) {
				tx.rollback();
			}
			em.close();
			
			Preconditions.checkState(em == jmodule.getEntityManager(),
									"JpaModule has been replaced: module=" + jmodule);
			jmodule.setEntityManager(null);
		}
	}
	
//	/**
//	 * JpaModule을 활용한 작업을 수행한다.
//	 * <p>
//	 * 작업을 수행하기 전에 JpaModule에 대한 EntityManager를 할당하고, 작업이 완료되면
//	 * 할당된 EntityManager를 해제한다.
//	 * 
//	 * @param jmodule  JpaModule 객체
//	 * @param consumer JpaModule에 대한 작업을 수행하는 Consumer 객체
//	 * @throws IllegalArgumentException JpaModule에 대한 EntityManager가 이미 할당된 경우
//	 */
//	public <J extends JpaModule> void run(J jmodule, Consumer<J> consumer) {
//		Preconditions.checkArgument(jmodule != null, "JpaModule is null");
//		Preconditions.checkArgument(jmodule.getEntityManager() == null,
//									"JpaModule has been allocated already: module=" + jmodule);
//		Preconditions.checkArgument(consumer != null, "Consumer is null");
//		
//		EntityManager em = m_emFact.createEntityManager();
//		EntityTransaction tx = em.getTransaction();
//		tx.begin();
//		
//		jmodule.setEntityManager(em);
//		try {
//			consumer.accept(jmodule);
//			tx.commit();
//		}
//		finally {
//			if ( tx.isActive() ) {
//				tx.rollback();
//			}
//			em.close();
//			
//			jmodule.setEntityManager(null);
//		}
//	}
//	
//	
//	public <T> T get(Function<EntityManager,T> task) {
//		EntityManager em = m_emFact.createEntityManager();
//		EntityTransaction tx = em.getTransaction();
//		tx.begin();
//		
//		try {
//			T result = task.apply(em);
//			tx.commit();
//			
//			return result;
//		}
//		finally {
//			if ( tx.isActive() ) {
//				tx.rollback();
//			}
//			em.close();
//		}
//	}
}
