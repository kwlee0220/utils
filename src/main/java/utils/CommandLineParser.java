package utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CommandLineParser {
	private final String m_msgPrefix;
	private final List<String> m_argNames = new ArrayList<String>();
	private final Options m_options = new Options();
	private boolean m_stopAtNonOption = false;
	
	public CommandLineParser(String msgPrefix) {
		m_msgPrefix = msgPrefix;
	}
	
	public CommandLine parseArgs(String[] args) throws ParseException {
    	BasicParser parser = new BasicParser();
	    return new CommandLine(m_msgPrefix, m_argNames, m_options,
	    							parser.parse(m_options, args, m_stopAtNonOption));
	}
	
	public CommandLineParser stopAtNonOption(boolean flag) {
		m_stopAtNonOption = flag;
		return this;
	}
	
	public void addArgumentName(String name) {
		m_argNames.add(name);
	}

    public void addArgOption(String id, String longOpt, String name, String desc, boolean required) {
    	m_options.addOption(OptionBuilder.withArgName(name)
    									.hasArg()
    									.withDescription(desc)
										.isRequired(required)
										.withLongOpt(longOpt)
										.create(id));
    }

    public void addArgOption(String id, String name, String desc, boolean required) {
    	m_options.addOption(OptionBuilder.withArgName(name)
    									.hasArg()
    									.withDescription(desc)
										.isRequired(required)
										.create(id));
    }

    public void addArgOption(String id, String name, String desc) {
        addArgOption(id, name, desc, false);
    }
    
    public void addOption(String id, String longOpt, String desc, boolean required) {
    	m_options.addOption(OptionBuilder.withDescription(desc)
    				.isRequired(required)
					.withLongOpt(longOpt)
    				.create(id));
    }
    
    public void addOption(String id, String desc, boolean required) {
    	m_options.addOption(OptionBuilder.withDescription(desc)
    				.isRequired(required)
    				.create(id));
    }

    public void addOption(String id, String desc) {
        addOption(id, desc, false);
    }

    public void addOption(Option option) {
        m_options.addOption(option);
    }
    
	public void printUsage() {
		printUsage(m_msgPrefix, m_argNames, m_options);
    }
    
	public static void printUsage(String msgPrefix, List<String> argNames, Options options) {
		StringBuilder argHelp = new StringBuilder();
		for ( int i =0; i < argNames.size(); ++i ) {
			String argName = argNames.get(i);
			
			argHelp.append('<').append(argName).append('>');
			if ( i < argNames.size()-1 ) {
				argHelp.append(' ');
			}
		}
		
		String prefix = msgPrefix;
		if ( options.getOptions().size() > 0 ) {
			prefix += "[options] ";
		}
		
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(200);
		formatter.printHelp(prefix + argHelp.toString(), options);
    }
    
	public void exitWithUsage(int exitCode) {
		exitWithUsage(m_msgPrefix, m_argNames, m_options, exitCode);
    }
    
	public static void exitWithUsage(String msgPrefix, List<String> argNames, Options options,
									int exitCode) {
		StringBuilder argHelp = new StringBuilder();
		for ( int i =0; i < argNames.size(); ++i ) {
			String argName = argNames.get(i);
			
			argHelp.append('<').append(argName).append('>');
			if ( i < argNames.size()-1 ) {
				argHelp.append(' ');
			}
		}
		
		String prefix = msgPrefix;
		if ( options.getOptions().size() > 0 ) {
			prefix += "[options] ";
		}
		
		HelpFormatter formatter = new HelpFormatter();
		formatter.setWidth(200);
		formatter.printHelp(prefix + argHelp.toString(), options);
        System.exit(exitCode);
    }
}
