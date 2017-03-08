package utils.config.json;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.text.StrSubstitutor;

import com.google.gson.Gson;
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
		try ( FileReader reader = new FileReader(configFile) ) {
			Properties variables = new Properties();
			variables.put("config_dir", configFile.getParentFile().getAbsolutePath());
			
			return load(new JsonParser().parse(reader), variables);
		}
	}

	public static JsonConfiguration load(String configStr) {
		Properties variables = new Properties();
		return load(new JsonParser().parse(configStr), variables);
	}

	private static JsonConfiguration load(JsonElement root, Properties variables) {
		JsonConfiguration config = new JsonConfiguration();
		
		Map<String,String> envVars = System.getenv();
		for ( Map.Entry<String,String> e: envVars.entrySet() ) {
			variables.put(e.getKey(), StrSubstitutor.replace(e.getValue(), variables));
		}
		
		JsonElement varsElm = ((JsonObject)root).get("config_variables");
		if ( varsElm != null && varsElm instanceof JsonObject ) {
			JsonObject objElm = (JsonObject)varsElm;
			
			objElm.entrySet().stream()
					.forEach(ent -> {
						String value = ent.getValue().getAsString();
						value = StrSubstitutor.replace(value, variables);
						variables.put(ent.getKey(), value);
					});
		}
		
		config.m_root = new JsonConfigNode(config, null, "", root);
		config.m_variables = variables;
		
		return config;
	}

	@Override
	public void write(Object value, File file) throws IOException {
		try ( FileWriter writer = new FileWriter(file) ) {
			new Gson().toJson(value, writer);
		}
	}
}
