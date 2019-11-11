package utils.jni;

import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractJniObjectProxy implements JniObjectProxy, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractJniObjectProxy.class);
	
	private final ReentrantLock m_jniLock = new ReentrantLock();
	@GuardedBy("m_jniPtrMutex") protected int m_jniPtr;	// open indicator (0: closed, otherwise: opened)
	
	private volatile Logger m_logger = s_logger;
	
	protected abstract void deleteJniObjectInGuard(int jniPtr);

	@Override
	public int getJniPointer() {
		return m_jniPtr;
	}
	
	public void setJniPointer(int jniPtr) {
		m_jniLock.lock();
		try {
			m_jniPtr = jniPtr;
		}
		finally {
			m_jniLock.unlock();
		}
	}

	@Override
	public void close() {
		m_jniLock.lock();
		try {
			if ( m_jniPtr != 0 ) {
				deleteJniObjectInGuard(m_jniPtr);
			}
		}
		finally {
			m_jniPtr = 0;
			m_jniLock.unlock();
		}
	}
	
	@Override
	public final Logger getLogger() {
		return m_logger;
	}

	@Override
	public final void setLogger(Logger logger) {
		m_logger = (logger != null) ? logger : s_logger;
	}
	
	protected final ReentrantLock getJniLock() {
		return m_jniLock;
	}
	
	protected final void checkJniModuleAllocatedInGuard() throws JniRuntimeException {
		if ( m_jniPtr == 0 ) {
			throw new JniRuntimeException(getClass().getName() + " has not been allocated");
		}
	}
	
	protected final boolean isJniModuleAllocatedInGuard() throws JniRuntimeException {
		return m_jniPtr != 0;
	}
}
