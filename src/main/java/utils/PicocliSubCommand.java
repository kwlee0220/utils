package utils;

import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class PicocliSubCommand<T> implements PicocliCommand<T> {
	@ParentCommand private PicocliCommand<T> m_parent;
	@Spec private CommandSpec m_spec;
	@Mixin private UsageHelp m_help;
	
	abstract protected void run(T initialContext) throws Exception;
	
	@Override
	public T getInitialContext() throws Exception {
		return m_parent.getInitialContext();
	}
	
	public PicocliCommand<T> getParent() {
		return m_parent;
	}
	
	public CommandLine getCommandLine() {
		return m_spec.commandLine();
	}
	
	@Override
	public void run() {
		ParseResult sub = m_spec.commandLine().getParseResult().subcommand();
		try {
			if ( sub != null ) {
				@SuppressWarnings("unchecked")
				PicocliCommand<T> subC = (PicocliCommand<T>)sub.commandSpec().userObject();
				subC.run();
			}
			else {
				try {
					T server = m_parent.getInitialContext();
					run(server);
				}
				catch ( Exception e ) {
					System.err.printf("failed: %s%n%n", e);
					
					m_spec.commandLine().usage(System.out, Ansi.OFF);
				}
			}
		}
		catch ( Exception e ) {
			System.err.printf("failed: %s%n%n", e);
			
//				m_spec.commandLine().usage(System.out, Ansi.OFF);
		}
	}
}