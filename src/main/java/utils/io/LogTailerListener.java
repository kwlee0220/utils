package utils.io;

import java.io.File;
import java.time.Duration;

/**
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface LogTailerListener {
	public boolean handleLogTail(String line) throws Exception;
	public boolean logFileRewinded(File file) throws Exception;
	public boolean handleLogFileSilence(Duration interval) throws Exception;
}
