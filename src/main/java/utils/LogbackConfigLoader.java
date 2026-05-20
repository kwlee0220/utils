package utils;

import java.io.File;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;


/**
 * Logback 로깅 설정을 외부 파일 또는 classpath 리소스에서 동적으로 적재하는 유틸리티.
 * <p>
 * 모든 적재 메서드는 호출 시점에 현재 {@link LoggerContext}를 {@code reset()}하여 기존 설정을
 * 폐기한 뒤, 새 설정 파일을 {@link JoranConfigurator}로 다시 구성한다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogbackConfigLoader {
	/**
	 * 주어진 {@link File}로부터 Logback 설정을 적재한다.
	 * <p>
	 * 현재 {@link LoggerContext}를 reset한 뒤 설정 파일을 적용한다.
	 *
	 * @param configFile Logback XML 설정 파일.
	 * @throws JoranException 설정 파일 파싱/적용 중 오류가 발생한 경우.
	 */
    public static void loadLogbackConfigFromFile(File configFile) throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // 기존 설정을 초기화

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
		configurator.doConfigure(configFile); // 설정 파일 적용
    }

	/**
	 * 주어진 파일 경로로부터 Logback 설정을 적재한다.
	 * <p>
	 * {@link #loadLogbackConfigFromFile(File)}에 {@code new File(configFilePath)}을 전달하는 것과 같다.
	 *
	 * @param configFilePath Logback XML 설정 파일 경로.
	 * @throws JoranException 설정 파일 파싱/적용 중 오류가 발생한 경우.
	 */
    public static void loadLogbackConfigFromFile(String configFilePath) throws JoranException {
    	loadLogbackConfigFromFile(new File(configFilePath));
    }

	/**
	 * 주어진 클래스의 classpath에서 {@code /logback.xml}을 찾아 Logback 설정을 적재한다.
	 * <p>
	 * 현재 {@link LoggerContext}를 reset한 뒤 {@code configFileResource.getResourceAsStream("/logback.xml")}로
	 * 읽은 스트림을 설정에 사용한다. 해당 리소스가 없으면 스트림이 {@code null}이 되며 동작이 정의되지 않는다.
	 *
	 * @param configFileResource classpath 조회의 기준이 되는 클래스.
	 * @throws JoranException 설정 파싱/적용 중 오류가 발생한 경우.
	 */
    public static void loadLogbackConfigFromClass(Class<?> configFileResource, String resourceName)
    	throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // 기존 설정을 초기화

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

		configurator.doConfigure(configFileResource.getResourceAsStream(resourceName)); // 설정 파일 적용
    }

	/**
	 * 주어진 외부 파일에서 Logback 설정을 시도하고, 실패 시 fallback 클래스의 classpath에서
	 * {@code /logback.xml}을 적재한다.
	 * <p>
	 * 외부 파일 적용이 {@link JoranException}을 던지고 {@code fallbackClass}가 {@code null}이 아니면
	 * fallback 리소스로 재시도한다. {@code fallbackClass}가 {@code null}이면 원본 예외를 그대로 던진다.
	 *
	 * @param configFilePath	1차 적재 대상 Logback XML 설정 파일 경로.
	 * @param fallbackClass		1차 적재 실패 시 classpath {@code /logback.xml} 조회 기준 클래스. {@code null} 가능.
	 * @throws JoranException 1차 적재가 실패하고 fallback도 사용할 수 없거나 fallback도 실패한 경우.
	 */
    public static void loadLogbackConfig(String configFilePath, Class<?> fallbackClass) throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // 기존 설정을 초기화

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);

        try {
			configurator.doConfigure(configFilePath); // 설정 파일 적용
		}
		catch ( JoranException e ) {
			if ( fallbackClass != null ) {
                configurator.doConfigure(fallbackClass.getResourceAsStream("/logback.xml"));
			}
			else {
				throw e;
			}
		}
    }

	/**
	 * 개발/테스트용 진입점. 하드코딩된 logback.xml 경로로 설정을 적재하여 로그 출력을 확인한다.
	 */
    public static void main(String[] args) throws Exception {
        // 예제: 특정 Logback 설정 파일 사용
    	loadLogbackConfigFromFile("/home/kwlee/mdt/mdt-client/logback.xml");

        // 로그 테스트
        org.slf4j.Logger logger = LoggerFactory.getLogger(LogbackConfigLoader.class);
        logger.info("Logback 설정 적용 완료!");
    }
}
