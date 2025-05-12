package utils.io;

import java.io.File;
import java.time.Duration;

/**
 * LogTailer가 로그 파일의 변화를 감지할 때 호출하는 리스너.
 * <p>
 * LogTailer는 다음과 같은 로그 파일의 변화를 감지할 때, 이 리스너를 통해 변화를 알린다.
 * <ul>
 * 	<li>새로운 line이 추가된 경우: {@link #handleLogTail(String)}.
 * 	<li>로그 파일이 rewind된 경우: {@link #logFileRewinded(File)}.
 * 	<li>로그 파일에 변화가 없는 경우: {@link #handleLogFileSilence(Duration)}. 변화가 없는 기간의 길이는
 *         {@link LogTailer.Builder#sampleInterval(Duration)}로 설정된다.
 * </ul>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public interface LogTailerListener {
	/**
	 * 주어진 로그 파일을 처리한다.
	 * <p>
	 * 이 메소드는 로그 파일의 각 line에 대해 호출된다.
	 * 로그 파일의 마지막 line을 처리한 후 추가로 처리할 line이 없다면 false를 반환한다.
	 * 즉, false를 반환하면 로그 파일의 처리를 중단한다.
	 * 
	 * @param line	처리할 로그 파일.
	 * @return	로그 파일 지속 여부. {@code false}이면 로그 파일의 처리를 중단한다.
	 * @throws Exception	처리 중 오류가 발생한 경우.
	 */
	public boolean handleLogTail(String line) throws Exception;
	
	/**
	 * Log file이 rewind되었음을 알린다.
	 * <p>
	 * Log file이 rewind되면, 이 메소드가 호출된다.
	 * 추후 로그 파일 처리 계속 여부를 반환 값으로 결정하게 한다.
	 * 
	 * @param file	rewind된 로그 파일.
	 * @return	로그 파일 처리 계속 여부. {@code false}이면 로그 파일의 처리를 중단한다.
	 * @throws Exception	처리 중 오류가 발생한 경우.
	 */
	public boolean logFileRewinded(File file) throws Exception;
	
	/**
	 * 주어진 시간 동안 로그 파일에 변화가 없음을 알린다.
	 * <p>
	 * 일정 기간동안 로그 파일에 변화가 없을 경우 호출된다.
	 * 
	 * @param interval	로그 파일 변화가 없었던 구간.
	 * @return	로그 파일 처리 계속 여부. {@code false}이면 로그 파일의 처리를 중단한다.
	 */
	public boolean handleLogFileSilence(Duration interval) throws Exception;
}
