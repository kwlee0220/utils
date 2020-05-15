package utils;

import picocli.CommandLine.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class UsageHelp {
	@Option(names = {"-h", "-help"}, usageHelp=true, description="display help message")
	private boolean m_help;
}
