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
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JarBuilder {
	private final File m_jarFile;
	private final JarOutputStream m_jos;
	
	public static JarBuilder builder(File jarFile) throws IOException {
		return new JarBuilder(jarFile);
	}
	
	private JarBuilder(File jarFile) throws IOException {
		m_jarFile = jarFile;
		m_jos = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)));
	}
	
	public void build() throws IOException {
		m_jos.close();
	}
	
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
