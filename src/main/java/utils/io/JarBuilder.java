package utils.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * class 파일 또는 디렉토리를 모아 jar 파일을 생성하는 빌더이다.
 * {@link #builder(File)}로 생성하고 {@link #add(Class)} 또는 {@link #add(File)}로 항목을 추가한 뒤
 * {@link #build()}를 호출하여 jar 파일을 완성한다.
 * @author Kang-Woo Lee (ETRI)
 */
public class JarBuilder {
	private final JarOutputStream m_jos;
	
	/**
	 * 주어진 jar 파일에 기록하는 빌더를 생성한다.
	 * 대상 파일은 즉시 생성되며, 이미 존재하는 경우에는 덮어쓴다.
	 *
	 * @param jarFile	생성할 jar 파일.
	 * @return	생성된 빌더 객체.
	 * @throws IOException	jar 파일을 열 수 없는 경우.
	 */
	public static JarBuilder builder(File jarFile) throws IOException {
		return new JarBuilder(jarFile);
	}
	
	private JarBuilder(File jarFile) throws IOException {
		m_jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)));
	}
	
	/**
	 * jar 출력 스트림을 닫아 jar 파일 생성을 완료한다.
	 * 이 호출 이후에는 항목을 추가할 수 없다.
	 *
	 * @throws IOException	jar 파일을 닫는 중 오류가 발생한 경우.
	 */
	public void build() throws IOException {
		m_jos.close();
	}
	
	/**
	 * 주어진 클래스의 class 파일을 jar에 추가한다.
	 * 클래스가 로드된 code source 위치(디렉토리)에서 class 파일을 찾아 {@link #add(File)}로 추가한다.
	 * <p>
	 * <b>주의:</b> jar 항목 이름으로 class 파일의 전체 경로가 그대로 사용되므로({@link #add(File)} 참고),
	 * 패키지 경로와 일치하는 항목 이름이 만들어지지 않는다. 또한 code source가 jar 파일인 클래스는
	 * 지원하지 않는다.
	 *
	 * @param cls	추가할 클래스.
	 * @return	본 빌더 객체.
	 * @throws IOException	class 파일을 읽거나 jar에 기록하는 중 오류가 발생한 경우.
	 * @throws IllegalArgumentException	클래스 위치를 URI로 변환할 수 없는 경우.
	 */
	public JarBuilder add(Class<?> cls) throws IOException {
		try {
			URL url = cls.getProtectionDomain().getCodeSource().getLocation();
			File base = new File(url.toURI());
			File file = new File(base, cls.getName().replace('.', '/')+".class");
			
			return add(file);
		}
		catch ( URISyntaxException e ) {
			throw new IllegalArgumentException("invalid class location: " + e);
		}
	}

	/**
	 * 주어진 파일을 jar에 추가한다. 디렉토리이면 하위 파일들을 재귀적으로 추가한다.
	 * jar 항목 이름은 {@link File#getPath()}의 경로 구분자를 {@code /}로 바꾼 값이며,
	 * 디렉토리 항목은 {@code /}로 끝난다.
	 *
	 * @param clsFile	추가할 파일 또는 디렉토리.
	 * @return	본 빌더 객체.
	 * @throws IOException	파일을 읽거나 jar에 기록하는 중 오류가 발생한 경우.
	 */
	public JarBuilder add(File clsFile) throws IOException {
		addEntry(clsFile);
		if ( clsFile.isDirectory() ) {
			for ( File nestedFile: clsFile.listFiles() ) {
				add(nestedFile);
				m_jos.closeEntry();
			}
		}
		else {
			try ( BufferedInputStream in = new BufferedInputStream(new FileInputStream(clsFile)) ) {
				IOUtils.transfer(in, m_jos, 16*1024);
				m_jos.closeEntry();
			}
		}
		
		return this;
	}
	
	private void addEntry(File clsFile) throws IOException {
		String name = clsFile.getPath().replace("\\", "/");
		if ( !name.isEmpty() ) {
			if ( clsFile.isDirectory() && !name.endsWith("/") ) {
				name += "/";
			}
			JarEntry entry = new JarEntry(name);
			entry.setTime(clsFile.lastModified());
			m_jos.putNextEntry(entry);
		}
	}
}
