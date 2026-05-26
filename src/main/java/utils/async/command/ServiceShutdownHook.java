package utils.async.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.Service;

import utils.LoggerSettable;
import utils.Preconditions;
import utils.func.Optionals;

/**
 * Guava {@link Service}를 JVM 종료 시 자동으로 정리하는 shutdown hook.
 * <p>
 * {@link #register(String, Service)}로 hook을 등록하고, 반환된 {@link Registration}을
 * 통해 정상 경로에서 {@link Registration#unregister()}로 hook을 해제할 수 있다.
 * 정상 종료 경로에서 hook을 해제하지 않으면 JVM 종료 시 이미 TERMINATED 상태인
 * service에 다시 {@code stopAsync()}가 호출되어 잡음 로그가 출력된다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ServiceShutdownHook implements Runnable, LoggerSettable {
	private static final Logger s_logger = LoggerFactory.getLogger(ServiceShutdownHook.class);

	private final Service m_service;
	private Logger m_logger = null;

	/**
	 * 주어진 service를 위한 JVM shutdown hook을 등록한다.
	 *
	 * @param name    hook thread 이름의 prefix (실제 이름은 {@code name + "-ShutdownHook"}).
	 * @param service JVM 종료 시 정리할 service.
	 * @return 등록된 hook의 핸들. 정상 경로에서 {@link Registration#unregister()}로 해제 가능.
	 * @throws IllegalArgumentException {@code name} 또는 {@code service}가 null인 경우.
	 */
	public static Registration register(String name, Service service) {
		Preconditions.checkNotNullArgument(name, "name is null");
		Preconditions.checkNotNullArgument(service, "service is null");

		Thread hook = new Thread(new ServiceShutdownHook(service), name + "-ShutdownHook");
		Runtime.getRuntime().addShutdownHook(hook);
		return new Registration(hook);
	}

	private ServiceShutdownHook(Service service) {
		Preconditions.checkNotNullArgument(service, "service is null");
		m_service = service;
	}
	
	@Override
	public void run() {
		getLogger().info("Shutting down Service due to JVM shutdown: {}", m_service);

		m_service.stopAsync();
		try {
			// 정상적으로 종료될 때까지 잠시 대기
			m_service.awaitTerminated(3, java.util.concurrent.TimeUnit.SECONDS);
		}
		catch ( Exception e ) {
			getLogger().warn("Failed to cleanly shutdown {}, cause={}", m_service, ""+e);
		}
	}

	@Override
	public Logger getLogger() {
		return Optionals.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}

	/**
	 * {@link ServiceShutdownHook#register(String, Service)}로 등록된 hook의 핸들.
	 * <p>
	 * 정상 종료 경로에서 {@link #unregister()} 또는 {@link #close()}를 호출하여
	 * 등록된 hook을 JVM으로부터 제거할 수 있다. JVM 종료가 이미 진행 중인 경우
	 * {@link Runtime#removeShutdownHook(Thread)}가 {@link IllegalStateException}을
	 * 던지지만, 본 구현은 이를 무시한다 (JVM 종료 시점이므로 hook 정리가 무의미).
	 * <p>
	 * 동일 핸들에 대한 {@code unregister}의 중복 호출은 안전하다.
	 */
	public static final class Registration implements AutoCloseable {
		private final Thread m_hookThread;

		private Registration(Thread hookThread) {
			m_hookThread = hookThread;
		}

		/**
		 * 등록된 hook을 JVM에서 제거한다.
		 * <p>
		 * 이미 제거되었거나 JVM 종료가 진행 중이면 무동작으로 반환한다.
		 */
		public void unregister() {
			try {
				Runtime.getRuntime().removeShutdownHook(m_hookThread);
			}
			catch ( IllegalStateException ignored ) {
				// JVM이 이미 종료 중인 경우: hook 정리가 의미 없으므로 무시.
			}
		}

		@Override
		public void close() {
			unregister();
		}
	}
}
