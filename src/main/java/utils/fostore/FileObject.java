package utils.fostore;

import java.io.File;
import java.io.IOException;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileObject {
	public void save(File file) throws IOException;
	public void destroy();
}
