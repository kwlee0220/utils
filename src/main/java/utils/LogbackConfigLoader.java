package utils;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;


/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class LogbackConfigLoader {
    public static void loadLogbackConfigFromFile(String configFilePath) throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // 기존 설정을 초기화

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
		configurator.doConfigure(configFilePath); // 설정 파일 적용
    }
    
    public static void loadLogbackConfigFromClass(Class<?> configFileResource) throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset(); // 기존 설정을 초기화

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
		configurator.doConfigure(configFileResource.getResourceAsStream("/logback.xml")); // 설정 파일 적용
    }
    
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

    public static void main(String[] args) throws Exception {
        // 예제: 특정 Logback 설정 파일 사용
    	loadLogbackConfigFromFile("/home/kwlee/mdt/mdt-client/logback.xml");

        // 로그 테스트
        org.slf4j.Logger logger = LoggerFactory.getLogger(LogbackConfigLoader.class);
        logger.info("Logback 설정 적용 완료!");
    }
}
