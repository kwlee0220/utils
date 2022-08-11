/**
 * 
 */
package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FileProxy {
	public String getName();
	public String getAbsolutePath();
	public boolean isDirectory() throws IOException;
	
	public FileProxy getParent();
	public FileProxy getChild(String childName);
	public boolean exists() throws IOException;
	public void renameTo(FileProxy dstFile, boolean replaceExisting) throws IOException;
	public void delete() throws IOException;
	public void mkdir() throws IOException;
	
	public long length() throws IOException;
	
	public FileProxy newFile(String path);
	public InputStream openInputStream() throws IOException;
	public OutputStream openOutputStream(boolean append) throws IOException;
}
