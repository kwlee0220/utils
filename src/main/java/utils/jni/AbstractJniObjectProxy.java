package utils.jni;

import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.LoggerSettable;



/**
 * {@link JniObjectProxy}의 기본 구현을 제공하는 추상 클래스.
 * <p>
 * native 객체 포인터({@link #m_jniPtr})를 내부 {@link ReentrantLock}으로 보호하며,
 * {@link #close()} 호출 시 포인터가 0이 아니면 {@link #deleteJniObjectInGuard(int)}를 호출하여
 * native 객체를 해제한 뒤 포인터를 0으로 설정한다.
 * 이름이 {@code InGuard}로 끝나는 메소드는 {@link #getJniLock()}으로 얻은 lock을 획득한 상태에서
 * 호출되어야 한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class AbstractJniObjectProxy implements JniObjectProxy, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(AbstractJniObjectProxy.class);
	
	private final ReentrantLock m_jniLock = new ReentrantLock();
	/**
	 * native 객체 포인터 값. 0이면 native 객체가 할당되지 않았거나 해제된 상태를 의미한다.
	 * 이 필드는 {@link #getJniLock()}이 반환하는 lock으로 보호된다.
	 */
	@GuardedBy("m_jniPtrMutex") protected int m_jniPtr;	// open indicator (0: closed, otherwise: opened)
	
	private volatile Logger m_logger = s_logger;
	
	/**
	 * 객체를 생성한다. 생성 직후 native 객체 포인터는 0(미할당)이다.
	 */
	public AbstractJniObjectProxy() { }
	
	/**
	 * 주어진 포인터에 해당하는 native 객체를 해제한다.
	 * <p>
	 * {@link #close()}에서 JNI lock을 획득한 상태로, 포인터가 0이 아닌 경우에만 호출된다.
	 * 이 메소드가 예외를 발생시키더라도 포인터는 0으로 설정된다.
	 *
	 * @param jniPtr	해제할 native 객체 포인터 값.
	 */
	protected abstract void deleteJniObjectInGuard(int jniPtr);

	@Override
	public int getJniPointer() {
		return m_jniPtr;
	}
	
	/**
	 * native 객체 포인터 값을 설정한다.
	 * <p>
	 * JNI lock을 획득한 상태에서 값을 설정하며, 기존 포인터가 가리키던 native 객체는 해제하지 않는다.
	 *
	 * @param jniPtr	설정할 native 객체 포인터 값.
	 */
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
	
	/**
	 * native 객체 포인터를 보호하는 lock을 반환한다.
	 *
	 * @return	JNI lock 객체.
	 */
	protected final ReentrantLock getJniLock() {
		return m_jniLock;
	}
	
	/**
	 * native 객체가 할당되어 있는지 확인한다.
	 * <p>
	 * JNI lock을 획득한 상태에서 호출되어야 한다.
	 *
	 * @throws JniRuntimeException	native 객체 포인터가 0인 경우.
	 */
	protected final void checkJniModuleAllocatedInGuard() throws JniRuntimeException {
		if ( m_jniPtr == 0 ) {
			throw new JniRuntimeException(getClass().getName() + " has not been allocated");
		}
	}
	
	/**
	 * native 객체가 할당되어 있는지 여부를 반환한다.
	 * <p>
	 * JNI lock을 획득한 상태에서 호출되어야 한다. 선언과 달리 예외를 발생시키지 않는다.
	 *
	 * @return	native 객체 포인터가 0이 아니면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws JniRuntimeException	실제로는 발생하지 않는다.
	 */
	protected final boolean isJniModuleAllocatedInGuard() throws JniRuntimeException {
		return m_jniPtr != 0;
	}
}
