/**
 * 
 */
package utils.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.LockSupport;

import com.google.common.base.Objects;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class LocalFile implements FileProxy {
	private final File m_file;
	
	public static LocalFile of(File file) {
		return new LocalFile(file);
	}
	
	public static LocalFile of(String path) {
		return new LocalFile(new File(path));
	}
	
	public LocalFile(File file) {
		m_file = file;
	}
	
	public File getFile() {
		return m_file;
	}

	@Override
	public String getName() {
		return m_file.getName();
	}

	@Override
	public String getAbsolutePath() {
		return m_file.getAbsolutePath();
	}

	@Override
	public LocalFile getParent() {
		File parent = m_file.getParentFile();
		return parent != null ? LocalFile.of(parent) : null;
	}

	@Override
	public LocalFile getChild(String childName) {
		return LocalFile.of(new File(m_file, childName));
	}

	@Override
	public boolean isDirectory() throws IOException {
		return m_file.isDirectory();
	}

	@Override
	public boolean exists() {
		return m_file.exists();
	}

	@Override
	public void delete() throws IOException {
		if ( !m_file.delete() ) {
			throw new IOException("fails to delete file: " + this);
		}
	}

	@Override
	public void renameTo(FileProxy dstFile, boolean replaceExisting) throws IOException {
		if ( !exists() ) {
			throw new IOException("source not found: file=" + m_file);
		}
		
		if ( !(dstFile instanceof LocalFile) ) {
			throw new IllegalArgumentException("incompatible destination file handle: " + dstFile);
		}
		LocalFile dst = (LocalFile)dstFile;
		
		if ( replaceExisting ) {
	        Files.move(m_file.toPath(), dst.m_file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		else {
			if ( dst.exists() ) {
				throw new IOException("destination exists: file=" + dstFile);
			}
	        Files.move(m_file.toPath(), dst.m_file.toPath());
		}
	}

	@Override
	public void mkdir() throws IOException {
		if ( exists() ) {
			throw new FileAlreadyExistsException("already exists: file=" + m_file);
		}
		
		LocalFile parent = getParent();
		if ( parent != null ) {
			if ( !parent.exists() ) {
				parent.mkdir();
			}
			else if ( !parent.isDirectory() ) {
				throw new IOException("the parent file is not a directory");
			}
		}
		
		if ( !m_file.mkdir() ) {
			throw new IOException("fails to create a directory: " + m_file);
		}
	}

	@Override
	public long length() throws IOException {
        // Reading the file length is a tricky business.
        // We will retry some.
        Exception lastError = null;
        for (int trialIndex = 0; trialIndex < 5; trialIndex++) {
            long fileLength = m_file.length();
            if (fileLength != 0) {
                return fileLength;
            }
            // `File#length()` can return 0 due to I/O failures.
            // We are falling back to NIO for a second attempt.
            else {
                Path path = m_file.toPath();
                try {
                    return Files.size(path);
                } catch (IOException error) {
                    lastError = error;
                }
            }
            // Scientifically proven retry practice: wait a bit.
            LockSupport.parkNanos(1);
        }
        
        String message = String.format("file length read failure {file=%s}", m_file);
        throw new IOException(message, lastError);
    }

	@Override
	public LocalFile newFile(String path) {
		return LocalFile.of(new File(path));
	}

	@Override
	public FileInputStream openInputStream() throws IOException {
		return new FileInputStream(m_file);
	}

	@Override
	public FileOutputStream openOutputStream(boolean append) throws IOException {
		return new FileOutputStream(m_file, append);
	}
	
	@Override
	public String toString() {
		return m_file.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		else if ( this == null || getClass() != LocalFile.class ) {
			return false;
		}
		
		LocalFile other = (LocalFile)obj;
		return Objects.equal(m_file, other.m_file);
	}
}