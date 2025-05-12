package utils.jpa;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.stream.FStream;

import jakarta.persistence.EntityManager;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class JpaContext implements AutoCloseable {
	private static ThreadLocal<JpaContext> s_context = new ThreadLocal<>();
	
	private final List<JpaSession> m_sessionStack = Lists.newArrayList();
	
	public static JpaContext get() {
		return s_context.get();
	}
	
	@Override
	public void close() {
		FStream.from(m_sessionStack).forEach(JpaSession::close);
		m_sessionStack.clear();
    }
	
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
