package utils.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.google.common.io.Files;


/**
 * 클래스패스({@code classpath:}) 리소스와 URL/파일 경로 형태의 리소스 위치를 해석하여
 * {@link URL}, {@link File}, {@link InputStream} 등으로 접근하기 위한 정적 유틸리티 메소드들을 제공한다.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class ResourceUtils {
	/**
	 * 클래스패스 리소스 위치를 나타내는 접두어({@code "classpath:"}).
	 */
	public static final String CLASSPATH_URL_PREFIX = "classpath:";
	/**
	 * 파일 URL의 프로토콜 이름({@code "file"}).
	 */
	public static final String URL_PROTOCOL_FILE = "file";

	/**
	 * 기본 생성자.
	 * <p>
	 * 본 클래스는 정적 메소드만 제공하므로 객체를 생성할 필요는 없다.
	 */
	public ResourceUtils() {}

	/**
	 * 주어진 위치 문자열이 URL 형태인지 여부를 반환한다.
	 * {@code classpath:}로 시작하거나, {@link URI}로 파싱한 뒤 {@link URL}로 변환할 수 있으면 URL로 판단한다.
	 * <p>
	 * <b>주의:</b> scheme이 없는 경로(예: {@code a/b}, {@code /tmp/x})는 {@link URI#toURL()}이
	 * {@link IllegalArgumentException}을 발생시키며, 이 예외는 잡히지 않고 그대로 전파된다.
	 *
	 * @param location	검사할 위치 문자열.
	 * @return	URL 형태이면 {@code true}, 그렇지 않으면 {@code false}.
	 * @throws IllegalArgumentException	{@code location}이 scheme이 없는 경로인 경우.
	 */
	public static boolean isUrl(String location) {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			return true;
		}
		try {
			new URI(location).toURL();
			return true;
		}
		catch ( MalformedURLException | URISyntaxException e ) {
			return false;
		}
	}

	/**
	 * 위치 문자열에 해당하는 {@link URL}을 반환한다.
	 * {@code classpath:}로 시작하면 현재 스레드의 context class loader에서 리소스를 찾고,
	 * 그렇지 않으면 {@link URI}로 파싱하여 URL로 변환한다. URI 파싱에 실패하면 {@code file:}
	 * 접두어를 붙여 다시 시도한다.
	 * <p>
	 * <b>주의:</b> scheme이 없는 경로(예: {@code /tmp/x})는 URI 파싱은 성공하지만
	 * {@link URI#toURL()}이 {@link IllegalArgumentException}을 발생시키며, 이 경우
	 * {@code file:} 대체 경로로 넘어가지 않고 예외가 그대로 전파된다.
	 *
	 * @param location	리소스 위치 문자열.
	 * @return	리소스 URL.
	 * @throws FileNotFoundException	클래스패스 리소스가 존재하지 않거나, 위치 문자열을
	 * 							URL로도 파일 경로로도 해석할 수 없는 경우.
	 * @throws IllegalArgumentException	{@code location}이 scheme이 없는 경로인 경우.
	 */
	public static URL getURL(String location) throws FileNotFoundException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			URL url = Thread.currentThread().getContextClassLoader().getResource(path);
			if ( url == null ) {
				throw new FileNotFoundException("Resource [" + path + "] not found");
			}
			return url;
		}
		try {
			return new URI(location).toURL();
		}
		catch ( MalformedURLException | URISyntaxException ex ) {
			try {
				return new URI("file:" + location).toURL();
			}
			catch ( MalformedURLException | URISyntaxException ex2 ) {
				throw new FileNotFoundException("Resource location [" + location
						+ "] is neither a URL not a well-formed file path");
			}
		}
	}
	
	/**
	 * 주어진 클래스를 기준으로 이름에 해당하는 리소스의 URL을 반환한다.
	 * ({@link Class#getResource(String)}과 동일하다.)
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @return	리소스 URL. 리소스가 존재하지 않으면 {@code null}.
	 */
	public static URL getURL(Class<?> cls, String name) {
		return cls.getResource(name);
	}
	
	/**
	 * 주어진 클래스를 기준으로 이름에 해당하는 리소스의 {@link File}을 반환한다.
	 * 리소스는 파일 시스템 상의 파일({@code file} 프로토콜)이어야 한다.
	 * <p>
	 * <b>주의:</b> 리소스가 존재하지 않으면 {@link FileNotFoundException}이 아니라
	 * {@link NullPointerException}이 발생한다.
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @return	리소스 파일.
	 * @throws FileNotFoundException	리소스가 {@code file} 프로토콜이 아닌 경우(예: jar 내부 리소스).
	 */
	public static File getFile(Class<?> cls, String name) throws FileNotFoundException {
		return getFile(getURL(cls, name));
	}
	
	/**
	 * 주어진 클래스를 기준으로 한 리소스 파일의 전체 내용을 문자열로 읽는다.
	 * 리소스 파일을 찾는 방식과 제약은 {@link #getFile(Class, String)}과 같다.
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @param charset	파일 내용의 문자 인코딩.
	 * @return	파일 내용 문자열.
	 * @throws IOException	리소스 파일을 찾을 수 없거나 읽는 중 오류가 발생한 경우.
	 */
	public static String readString(Class<?> cls, String name, Charset charset)
		throws IOException {
		return Files.asCharSource(getFile(cls, name), charset).read();
	}
	
	/**
	 * 주어진 클래스를 기준으로 한 리소스 파일의 전체 내용을 UTF-8 문자열로 읽는다.
	 * 리소스 파일을 찾는 방식과 제약은 {@link #getFile(Class, String)}과 같다.
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @return	파일 내용 문자열.
	 * @throws IOException	리소스 파일을 찾을 수 없거나 읽는 중 오류가 발생한 경우.
	 */
	public static String readString(Class<?> cls, String name)
		throws IOException {
		return readString(cls, name, StandardCharsets.UTF_8);
	}
	
	/**
	 * 주어진 클래스를 기준으로 한 리소스 파일의 내용을 줄 단위 스트림으로 반환한다.
	 * 반환된 스트림은 파일을 열고 있으므로 호출자가 닫아야 한다.
	 * 리소스 파일을 찾는 방식과 제약은 {@link #getFile(Class, String)}과 같다.
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @param charset	파일 내용의 문자 인코딩.
	 * @return	줄 단위 문자열 스트림.
	 * @throws IOException	리소스 파일을 찾을 수 없거나 여는 중 오류가 발생한 경우.
	 */
	public static Stream<String> readLines(Class<?> cls, String name, Charset charset)
		throws IOException {
		return Files.asCharSource(getFile(cls, name), charset).lines();
	}
	
	/**
	 * 주어진 클래스를 기준으로 한 리소스 파일의 내용을 UTF-8로 읽어 줄 단위 스트림으로 반환한다.
	 * 반환된 스트림은 파일을 열고 있으므로 호출자가 닫아야 한다.
	 *
	 * @param cls	리소스 탐색의 기준 클래스.
	 * @param name	리소스 이름.
	 * @return	줄 단위 문자열 스트림.
	 * @throws IOException	리소스 파일을 찾을 수 없거나 여는 중 오류가 발생한 경우.
	 */
	public static Stream<String> readLines(Class<?> cls, String name)
		throws IOException {
		return readLines(cls, name, StandardCharsets.UTF_8);
	}
	
	/**
	 * 위치 문자열에 해당하는 리소스를 읽기 위한 입력 스트림을 연다.
	 * {@code classpath:} 접두어(현재 스레드의 context class loader에서 탐색) 또는
	 * {@code file}로 시작하는 URL만 지원한다. 반환된 스트림은 호출자가 닫아야 한다.
	 * <p>
	 * <b>주의:</b> 클래스패스 리소스가 존재하지 않으면 {@link NullPointerException}이 발생한다.
	 *
	 * @param location	리소스 위치 문자열.
	 * @return	입력 스트림.
	 * @throws IOException	스트림을 여는 중 오류가 발생한 경우.
	 * @throws IllegalArgumentException	지원하지 않는 형태의 위치이거나 잘못된 URL인 경우.
	 */
	public static InputStream openInputStream(String location) throws IOException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			return Thread.currentThread()
						.getContextClassLoader()
						.getResource(path)
						.openStream();
		}
		else if ( location.startsWith(URL_PROTOCOL_FILE) ) {
			try {
				return new URI(location).toURL().openStream();
			}
			catch ( MalformedURLException | URISyntaxException e ) {
				throw new IllegalArgumentException("invalid resource loc: " + location, e);
			}
		}
		else {
			throw new IllegalArgumentException("invalid resource loc: " + location);
		}
	}

	/**
	 * 위치 문자열에 해당하는 {@link File}을 반환한다.
	 * {@code classpath:}로 시작하면 클래스패스 리소스의 파일을, URL이면 해당 {@code file} URL의 파일을
	 * 반환하며, URI 파싱에 실패하면 위치 문자열을 그대로 파일 경로로 사용한다.
	 * <p>
	 * <b>주의:</b> scheme이 없는 경로(예: {@code /tmp/x})는 URI 파싱은 성공하지만
	 * {@link URI#toURL()}이 {@link IllegalArgumentException}을 발생시키므로, 파일 경로로 처리되지 않고
	 * 예외가 그대로 전파된다.
	 *
	 * @param location	리소스 위치 문자열.
	 * @return	리소스 파일.
	 * @throws FileNotFoundException	클래스패스 리소스가 존재하지 않거나 URL이 {@code file} 프로토콜이 아닌 경우.
	 * @throws IllegalArgumentException	{@code location}이 scheme이 없는 경로인 경우.
	 */
	public static File getFile(String location) throws FileNotFoundException {
		if ( location.startsWith(CLASSPATH_URL_PREFIX) ) {
			String path = location.substring(CLASSPATH_URL_PREFIX.length());
			URL url = Thread.currentThread().getContextClassLoader().getResource(path);
			if ( url == null ) {
				throw new FileNotFoundException("Resource[" + path + "] not found");
			}
			return getFile(url);
		}
		try {
			return getFile(new URI(location).toURL());
		}
		catch ( MalformedURLException | URISyntaxException e ) {
			return new File(location);
		}
	}

	/**
	 * {@code file} 프로토콜 URL에 해당하는 {@link File}을 반환한다.
	 * URL의 경로 부분은 UTF-8로 URL 디코딩된다.
	 *
	 * @param resourceUrl	리소스 URL.
	 * @return	리소스 파일.
	 * @throws FileNotFoundException	URL이 {@code file} 프로토콜이 아닌 경우.
	 */
	public static File getFile(URL resourceUrl) throws FileNotFoundException {
		if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {
			throw new FileNotFoundException("Resource[" + resourceUrl + "] not found");
		}
		
//		return new File(URLDecoder.decode(resourceUrl.getFile()));
		try {
			return new File(URLDecoder.decode(resourceUrl.getFile(), "UTF-8"));
		}
		catch ( UnsupportedEncodingException e ) {
			throw new RuntimeException(e);
		}
	}
}