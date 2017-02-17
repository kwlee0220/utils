package utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import utils.io.ResourceUtils;


/**
 * 
 * @author Kang-Woo Lee
 */
public class Log4jConfigurator {
	public static void configure(Properties props) {
		PropertyConfigurator.configure(props);
	}
	
	public static void configure(File file) throws IOException {
		PropertyConfigurator.configure(file.getAbsolutePath());
//		XProperties props = new XProperties();
//		props.load(file);
//		
//		configure(props);
	}
	
	public static void configure(String resourceLocation)
		throws FileNotFoundException, IOException {
		configure(ResourceUtils.getFile(resourceLocation));
	}
	
	public static void configure(File file, long interval) {
		PropertyConfigurator.configureAndWatch(file.getAbsolutePath(), interval);
	}
	
	public static void configure(String resourceLocation, long interval)
		throws FileNotFoundException {
		configure(ResourceUtils.getFile(resourceLocation), interval);
	}
	
	@SuppressWarnings("unchecked")
	public static void setLevelAll(Level level) {
		Collections.list(LogManager.getCurrentLoggers())
					.forEach(logger -> ((Logger)logger).setLevel(level));
	}
}
