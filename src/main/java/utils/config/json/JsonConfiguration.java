package utils.config.json;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.text.StrSubstitutor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import utils.config.ConfigNode;
import utils.config.Configuration;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JsonConfiguration implements Configuration {
	private ConfigNode m_root;
	private Properties m_variables;
	
	private JsonConfiguration() {
	}

	@Override
	public ConfigNode getRoot() {
		return m_root;
	}

	@Override
	public Properties getVariables() {
		return m_variables;
	}

	@Override
	public void addVariable(String name, String value) {
		m_variables.put(name, value);
	}

	public static JsonConfiguration load(File configFile) throws IOException {
		JsonConfiguration conf = new JsonConfiguration();
		
		try ( FileReader reader = new FileReader(configFile) ) {
			JsonElement root = new JsonParser().parse(reader);
			if ( !(root instanceof JsonObject) ) {
				throw new IllegalArgumentException("invalid configuration file: path=" + configFile);
			}
			
			Properties variables = new Properties();
			variables.put("config_file", configFile.getAbsolutePath());
			
			Map<String,String> envVars = System.getenv();
			for ( Map.Entry<String,String> e: envVars.entrySet() ) {
				variables.put(e.getKey(), StrSubstitutor.replace(e.getValue(), variables));
			}
			
			JsonElement configElm = ((JsonObject)root).get("config_variables");
			if ( configElm != null && configElm instanceof JsonObject ) {
				JsonObject config = (JsonObject)configElm;
				
				config.entrySet().stream()
						.forEach(ent -> {
							String value = ent.getValue().getAsString();
							value = StrSubstitutor.replace(value, variables);
							variables.put(ent.getKey(), value);
						});
			}
			
			conf.m_root = new JsonConfigNode(conf, null, "", root);
			conf.m_variables = variables;
			
			return conf;
		}
	}
}
