package utils.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import utils.KeyValue;
import utils.StrSubstitutor;
import utils.Tuple;
import utils.Utilities;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class EnvironmentFileLoader {
	private final File m_envFile;
	
	public static EnvironmentFileLoader from(File envFile) throws IOException {
		return new EnvironmentFileLoader(envFile);
	}
	
	private EnvironmentFileLoader(File envFile) {
		Preconditions.checkArgument(envFile != null, "envFile is null");
		
		m_envFile = envFile;
	}
	
	public LinkedHashMap<String,String> load() throws IOException {
		List<KeyValue<String, String>> keyValues = readEnvironmentFile(m_envFile);
		return StrSubstitutor.replaceIncrementally(keyValues, Map.of());
	}
	
	public void updateSystemProperties() throws IOException {
		for ( Map.Entry<String,String> e : load().entrySet() ) {
			System.setProperty(e.getKey(), e.getValue());
		}
	}
	
	private List<KeyValue<String,String>> readEnvironmentFile(File envFile) throws IOException {
		if ( !envFile.exists() ) {
			throw new FileNotFoundException("Cannot find the environment file: " + envFile.getAbsolutePath());
		}

		List<KeyValue<String,String>> keyValues = Lists.newArrayList();
		int count = 0;
		for ( String line: Files.readAllLines(envFile.toPath()) ) {
			++count;
			
			line = line.trim();
			if ( line.isEmpty() || line.startsWith("#") ) {
				continue;
			}
			
			Tuple<String,String> splits = Utilities.split(line, '=');
			if ( splits == null ) {
				throw new IOException("Ill-formed line in the environment file: " + line + ", line" + count);
			}
			String value = splits._2;
			
			// 따옴표 제거
			if ( value.startsWith("\"") && value.endsWith("\"") && value.length() > 1 ) {
				value = value.substring(1, value.length() - 1);
			}
			keyValues.add(KeyValue.of(splits._1, value));
		}
		
		return keyValues;
	}
}