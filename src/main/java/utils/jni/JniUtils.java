package utils.jni;


import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;



/**
 *
 * @author Kang-Woo Lee
 */
public class JniUtils {
    public static byte[] toKsc5601Bytes(String msg) {
        try {
            return msg.getBytes("ksc5601");
        }
        catch ( UnsupportedEncodingException e ) {
            throw new RuntimeException(e);
        }
    }
    
    public static String toUTFString(byte[] bytes) {
        try {
            return new String(bytes, "utf-8");
        }
        catch ( UnsupportedEncodingException e ) {
            throw new RuntimeException(e);
        }
    }
    
    public static void addPathToJavaLibraryPath(File path)
    	throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
    	String pathes = System.getProperty("java.library.path");
    	System.setProperty("java.library.path", pathes + ";" + path.getAbsolutePath());
    	
    	Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
    	fieldSysPath.setAccessible( true );
    	fieldSysPath.set( null, null );
    }
}
