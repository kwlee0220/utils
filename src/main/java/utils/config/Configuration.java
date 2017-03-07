package utils.config;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface Configuration {
	public ConfigNode getRoot();
	
	public Properties getVariables();
	public void addVariable(String name, String value);
	
	public default ConfigNode traverse(String path) {
		return getRoot().traverse(path);
	}
	
	public void write(Object value, File file) throws IOException;
}
