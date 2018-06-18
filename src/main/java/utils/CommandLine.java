package utils;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandLine {
	private final String m_msgPrefix;
	private final List<String> m_argNames;
	private final Options m_options;
	private final org.apache.commons.cli.CommandLine m_cl;
	
	CommandLine(String msgPrefix, List<String> argNames, Options options,
						org.apache.commons.cli.CommandLine cl) {
		m_msgPrefix = msgPrefix;
		m_argNames = argNames;
		m_options = options;
		m_cl = cl;
	}
	
	public int getArgumentCount() {
		return m_cl.getArgs().length;
	}
	
	public String[] getArgumentAll() {
		return m_cl.getArgs();
	}
	
	public String getArgument(int index) {
		String[] args = m_cl.getArgs();
		if ( index < 0 || index >= m_argNames.size() ) {
			throw new IllegalArgumentException("invalid argument index=" + index);
		}
		
		if ( index >= args.length ) {
			throw new IllegalArgumentException(String.format("undefined argument: '%s'",
															m_argNames.get(index)));
		}
		
		return args[index];
	}
	
	public String getArgument(String name) {
		int idx = m_argNames.indexOf(name);
		if ( idx < 0 ) {
			throw new IllegalArgumentException("unknown argument: name=" + name);
		}
		
		String[] args = m_cl.getArgs();
		if ( idx >= args.length ) {
			throw new IllegalArgumentException(String.format("undefined argument: '%s'",
															m_argNames.get(idx)));
		}
		
		return args[idx];
	}

    public String getString(String optId) {
    	String value = m_cl.getOptionValue(optId);
    	if ( value == null ) {
    		throw new IllegalArgumentException("option[" + optId + "] is not given");
    	}
    	
    	return value;
    }

    public Option<String> getOptionString(String optId) {
        String value = m_cl.getOptionValue(optId);
        return (value != null) ? Option.some(value) : Option.none();
    }
    
    public int getInt(String optId) {
    	return Integer.parseInt(getString(optId));
    }
    
    public Option<Integer> getOptionInt(String optId) {
        String value = m_cl.getOptionValue(optId);
        return value != null ? Option.some(Integer.parseInt(value)) : Option.none();
    }
    
    public long getLong(String optId) {
    	return Long.parseLong(getString(optId));
    }
    
    public Option<Long> getOptionLong(String optId) {
        String value = m_cl.getOptionValue(optId);
        return value != null ? Option.some(Long.parseLong(value)) : Option.none();
    }
    
    public double getDouble(String optId) {
    	return Double.parseDouble(getString(optId));
    }
    
    public Option<Double> getOptionDouble(String optId) {
        String value = m_cl.getOptionValue(optId);
        return value != null ? Option.some(Double.parseDouble(value)) : Option.none();
    }
    
    public File getFile(String optId) {
    	return Paths.get(getString(optId)).toFile();
    }
    
    public Option<File> getOptionFile(String optId) {
        String value = m_cl.getOptionValue(optId);
        return value != null ? Option.some(Paths.get(value).toFile()) : Option.none();
    }

    public boolean hasOptionValue(String optId) {
        return m_cl.getOptionValue(optId) != null;
    }

    public boolean hasOption(String optId) {
        return m_cl.hasOption(optId);
    }
    
	public void exitWithUsage(int exitCode) {
		CommandLineParser.exitWithUsage(m_msgPrefix, m_argNames, m_options, exitCode);
    }
    
	public void printUsage() {
		StringBuilder argsStr = new StringBuilder();
		for ( int i =0; i < m_argNames.size(); ++i ) {
			String argName = m_argNames.get(i);
			
			argsStr.append('<').append(argName).append('>');
			if ( i < m_argNames.size()-1 ) {
				argsStr.append(' ');
			}
		}
			
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(200);
		formatter.printHelp(m_msgPrefix + " [options] "
							+ argsStr, m_options);
    }
    
	public void printUsage(PrintStream pw) {
		StringBuilder argsStr = new StringBuilder();
		for ( int i =0; i < m_argNames.size(); ++i ) {
			String argName = m_argNames.get(i);
			
			argsStr.append('<').append(argName).append('>');
			if ( i < m_argNames.size()-1 ) {
				argsStr.append(' ');
			}
		}
		
		PrintStream orgOut = System.out;
		try {
			System.setOut(pw);
			
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(200);
			formatter.printHelp(m_msgPrefix + " [options] " + argsStr, m_options);
		}
		finally {
			System.setOut(orgOut);
		}
    }
}
