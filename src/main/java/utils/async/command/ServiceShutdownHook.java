package utils.async.command;

import org.slf4j.Logger;

import com.google.common.util.concurrent.Service;

import utils.LoggerSettable;
import utils.func.FOption;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class ServiceShutdownHook implements Runnable, LoggerSettable {
	private static final Logger s_logger = org.slf4j.LoggerFactory.getLogger(ServiceShutdownHook.class);
	
	private final Service m_service;
	private Logger m_logger = null;
	
	public static void register(String name, Service service) {
		Runtime.getRuntime().addShutdownHook(new Thread(new ServiceShutdownHook(service), name + "-ShutdownHook"));
	}
	
	public ServiceShutdownHook(Service service) {
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
		return FOption.getOrElse(m_logger, s_logger);
	}

	@Override
	public void setLogger(Logger logger) {
		m_logger = logger;
	}
}
