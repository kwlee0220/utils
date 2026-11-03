package utils.jpa;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.stream.FStream;

import jakarta.persistence.EntityManager;


/**
 * 쓰레드별로 할당된 {@link JpaSession}들을 스택 형태로 관리하는 컨텍스트.
 * <p>
 * 컨텍스트는 {@link ThreadLocal}에 저장되며, {@link #allocate(EntityManager)}가
 * 처음 호출될 때 현재 쓰레드에 생성된다.
 * <p>
 * <b>주의:</b> {@link JpaSession#close()}는 스택에서 세션을 제거하지 않으며,
 * {@link #close()}도 {@link ThreadLocal}에서 컨텍스트를 제거하지 않는다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JpaContext implements AutoCloseable {
	private static ThreadLocal<JpaContext> s_context = new ThreadLocal<>();
	
	private final List<JpaSession> m_sessionStack = Lists.newArrayList();
	
	/**
	 * 빈 세션 스택을 갖는 컨텍스트를 생성한다.
	 * <p>
	 * 생성된 컨텍스트는 현재 쓰레드에 등록되지 않는다. 쓰레드별 컨텍스트는
	 * {@link #allocate(EntityManager)}를 통해 생성된다.
	 */
	public JpaContext() { }

	/**
	 * 현재 쓰레드에 등록된 컨텍스트를 반환한다.
	 *
	 * @return	현재 쓰레드의 컨텍스트. 등록된 것이 없으면 {@code null}.
	 */
	public static JpaContext get() {
		return s_context.get();
	}
	
	@Override
	public void close() {
		FStream.from(m_sessionStack).forEach(JpaSession::close);
		m_sessionStack.clear();
    }
	
	/**
	 * 주어진 {@link EntityManager}로 새 {@link JpaSession}을 생성하여 현재 쓰레드
	 * 컨텍스트의 세션 스택 최상단에 넣는다.
	 * <p>
	 * 현재 쓰레드에 컨텍스트가 없으면 새로 생성하여 등록한다. 생성된 세션은
	 * 즉시 트랜잭션을 시작한다.
	 *
	 * @param em	세션이 사용할 {@link EntityManager}.
	 * @return	생성된 세션.
	 * @throws IllegalArgumentException	{@code em}이 {@code null}인 경우.
	 */
	public static JpaSession allocate(EntityManager em) {
		Preconditions.checkArgument(em != null, "EntityManager is null");
		
		JpaContext context = s_context.get();
		if ( context == null ) {
			context = new JpaContext();
			s_context.set(context);
		}
		
		JpaSession session = new JpaSession(em);
		context.m_sessionStack.add(0, session);
		
		return session;
	}
	
	/**
	 * 세션 스택 최상단의(가장 최근에 할당된) 세션을 반환한다.
	 *
	 * @return	최상단 세션.
	 * @throws IllegalStateException	세션 스택이 비어 있는 경우.
	 */
	public JpaSession top() {
		Preconditions.checkState(m_sessionStack != null, "JpaContext is not initialized");
		Preconditions.checkState(m_sessionStack.size() > 0, "JpaSession stack is empty");

		return m_sessionStack.get(0);
	}
	
	@Override
	public String toString() {
		return String.format("JpaContext: sessions=%s", m_sessionStack);
	}
}
