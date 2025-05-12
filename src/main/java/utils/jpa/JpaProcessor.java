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
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class JpaProcessor {
	private final EntityManagerFactory m_emFact;
	
	public JpaProcessor(EntityManagerFactory emFact) {
		m_emFact = emFact;
	}
	
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
