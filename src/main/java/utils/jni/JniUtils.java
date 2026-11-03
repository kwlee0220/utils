package utils.jni;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;



/**
 * JNI 모듈 연동 시 필요한 문자열 변환 및 native 라이브러리 경로 설정 유틸리티 함수들을 정의한다.
 *
 * @author Kang-Woo Lee
 */
public class JniUtils {
    /**
     * 객체를 생성한다. 본 클래스는 static 메소드만 제공하므로 별도로 생성할 필요가 없다.
     */
    public JniUtils() { }
    
    /**
     * 주어진 문자열을 KSC5601 인코딩의 byte 배열로 변환한다.
     *
     * @param msg	변환할 문자열.
     * @return	KSC5601로 인코딩된 byte 배열.
     * @throws RuntimeException	JVM이 KSC5601 인코딩을 지원하지 않는 경우
     * 			({@link UnsupportedEncodingException}을 감싼다).
     * @throws NullPointerException	{@code msg}가 {@code null}인 경우.
     */
    public static byte[] toKsc5601Bytes(String msg) {
        try {
            return msg.getBytes("ksc5601");
        }
        catch ( UnsupportedEncodingException e ) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 주어진 byte 배열을 UTF-8로 해석하여 문자열로 변환한다.
     *
     * @param bytes	UTF-8로 인코딩된 byte 배열.
     * @return	변환된 문자열.
     * @throws NullPointerException	{@code bytes}가 {@code null}인 경우.
     */
    public static String toUTFString(byte[] bytes) {
        try {
            return new String(bytes, "utf-8");
        }
        catch ( UnsupportedEncodingException e ) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 주어진 디렉토리를 {@code java.library.path} 시스템 속성에 추가하고,
     * {@link ClassLoader}의 {@code sys_paths} 필드를 {@code null}로 설정하여
     * 다음 native 라이브러리 적재 시 경로가 다시 읽히도록 한다.
     * <p>
     * <b>주의:</b> 경로 구분자로 항상 {@code ';'}(Windows 형식)를 사용한다.
     * 또한 {@code sys_paths}는 JDK 내부 필드이므로 최근 JDK에서는 필드가 없거나
     * 접근이 차단되어 예외가 발생할 수 있으며, 이 경우에도 시스템 속성은 이미 변경된 상태로 남는다.
     *
     * @param path	추가할 native 라이브러리 디렉토리.
     * @throws NoSuchFieldException	{@link ClassLoader}에 {@code sys_paths} 필드가 없는 경우.
     * @throws SecurityException	보안 관리자에 의해 필드 접근이 거부된 경우.
     * @throws IllegalArgumentException	필드 값 설정에 실패한 경우.
     * @throws IllegalAccessException	필드에 접근할 수 없는 경우.
     */
    public static void addPathToJavaLibraryPath(File path)
    	throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
    	String pathes = System.getProperty("java.library.path");
    	System.setProperty("java.library.path", pathes + ";" + path.getAbsolutePath());
    	
    	Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
    	fieldSysPath.setAccessible( true );
    	fieldSysPath.set( null, null );
    }
}
