package utils;

import picocli.CommandLine.Option;

/**
 * picocli 명령에 {@code @Mixin}으로 넣어 {@code --help}/{@code -h} 사용법 도움말 옵션을 추가하는 클래스.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public class UsageHelp {
	@Option(names = {"--help", "-h"}, usageHelp=true, description="display help message")
	private boolean m_help;

	/**
	 * 도움말 옵션 mixin을 생성한다. picocli가 리플렉션으로 호출한다.
	 */
	public UsageHelp() { }
}
