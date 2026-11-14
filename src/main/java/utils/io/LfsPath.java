package utils.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.LockSupport;

import utils.stream.FStream;

/**
 * 로컬 파일 시스템의 {@link File}에 기반한 {@link FilePath} 구현이다.
 * 직렬화 시에는 파일의 절대 경로가 기록된다.
 * @author Kang-Woo Lee (ETRI)
 */
public class LfsPath implements FilePath, Serializable {
	private static final long serialVersionUID = 1L;
	
	private transient File m_file;
	
	/**
	 * 주어진 파일에 대한 {@link LfsPath} 객체를 생성한다.
	 *
	 * @param file	대상 파일.
	 * @return	생성된 {@link LfsPath} 객체.
	 */
	public static LfsPath of(File file) {
		return new LfsPath(file);
	}
	
	/**
	 * 주어진 경로에 대한 {@link LfsPath} 객체를 생성한다.
	 *
	 * @param path	대상 파일 경로.
	 * @return	생성된 {@link LfsPath} 객체.
	 */
	public static LfsPath of(String path) {
		return new LfsPath(new File(path));
	}
	
	LfsPath(File file) {
		m_file = file;
	}
	
	/**
	 * 본 경로에 해당하는 {@link File} 객체를 반환한다.
	 *
	 * @return	파일 객체.
	 */
	public File getFile() {
		return m_file;
	}

	@Override
	public String getName() {
		return m_file.getName();
	}

	@Override
	public String getPath() {
		return m_file.getPath();
	}

	@Override
	public String getAbsolutePath() {
		return m_file.getAbsolutePath();
	}

	@Override
	public boolean exists() {
		return m_file.exists();
	}

	@Override
	public LfsPath getParent() {
		File parent = m_file.getParentFile();
		return parent != null ? LfsPath.of(parent) : null;
	}

	@Override
	public LfsPath getChild(String childName) {
		return LfsPath.of(new File(m_file, childName));
	}

	@Override
	public boolean isDirectory() {
		return m_file.isDirectory();
	}

	@Override
	public boolean isRegular() {
		String fname = getName();
		return !(fname.startsWith("_") || fname.startsWith("."));
	}

	@Override
	public FStream<FilePath> streamChildFilePaths() throws IOException {
		File[] subFiles = m_file.listFiles();
		if ( subFiles == null ) {
			if ( !m_file.exists() ) {
				throw new NoSuchFileException(m_file.toString());
			}
			if ( !m_file.isDirectory() ) {
				throw new NotDirectoryException(m_file.toString());
			}
			throw new IOException("failed to list the directory: " + m_file);
		}
		return FStream.of(subFiles)
						.map(file -> LfsPath.of(file))
						.cast(FilePath.class);
	}

	@Override
	public boolean delete() {
		return m_file.delete();
	}

	@Override
	public void renameTo(FilePath dstFile, boolean replaceExisting) throws IOException {
		if ( !exists() ) {
			throw new IOException("source not found: file=" + m_file);
		}
		
		if ( !(dstFile instanceof LfsPath) ) {
			throw new IllegalArgumentException("incompatible destination file handle: " + dstFile);
		}
		LfsPath dst = (LfsPath)dstFile;

		createParentDirectories(dst.m_file);
		if ( replaceExisting ) {
			Files.move(m_file.toPath(), dst.m_file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		else {
			// 대상이 있으면 Files.move가 FileAlreadyExistsException을 던진다(존재 확인과 이동 사이의 경쟁이 없다).
			Files.move(m_file.toPath(), dst.m_file.toPath());
		}
	}

	// 부모 디렉토리가 있으면(상대 경로 'name'처럼 부모가 없을 수도 있다) 중간 경로까지 만든다. 실패는 예외로 알린다.
	private static void createParentDirectories(File file) throws IOException {
		File parent = file.getAbsoluteFile().getParentFile();
		if ( parent != null ) {
			Files.createDirectories(parent.toPath());
		}
	}

	@Override
	public boolean mkdirs() {
		return m_file.mkdirs();
	}

	@Override
	public long getLength() throws IOException {
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
	public LfsPath path(String path) {
		return LfsPath.of(new File(path));
	}

	@Override
	public FileInputStream read() throws IOException {
		return new FileInputStream(m_file);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * 부모 디렉터리가 없으면 먼저 만들고, 만들지 못하면 {@link IOException}을 던진다.
	 * {@code overwrite}가 {@code false}이면 파일이 없을 때만 원자적으로 만들므로,
	 * 존재 확인과 생성 사이에 다른 프로세스가 파일을 만들어도 덮어쓰지 않는다.
	 *
	 * @throws java.nio.file.FileAlreadyExistsException	{@code overwrite}가 {@code false}이고 파일이 이미 있는 경우.
	 */
	@Override
	public OutputStream create(boolean overwrite) throws IOException {
		createParentDirectories(m_file);

		Path path = m_file.toPath();
		return overwrite
				? Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
										StandardOpenOption.WRITE)
				: Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	@Override
	public OutputStream append() throws IOException {
		createParentDirectories(m_file);
		return new FileOutputStream(m_file, true);
	}
	
	@Override
	public String toString() {
		return m_file.toString();
	}
	
	/**
	 * 두 객체가 같은 {@link File}을 가리키는지 비교한다. 비교는 {@link File#equals(Object)}를 따르므로 경로 문자열을
	 * 비교하며, 상대 경로와 그에 해당하는 절대 경로는 서로 다르다.
	 *
	 * @param obj	비교 대상. {@code null}이나 {@link LfsPath}가 아닌 객체면 {@code false}.
	 * @return	같은 파일 경로이면 {@code true}.
	 */
	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) {
			return true;
		}
		return obj instanceof LfsPath other && m_file.equals(other.m_file);
	}

	@Override
	public int hashCode() {
		return m_file.hashCode();
	}
	
	/**
	 * 직렬화 시 기본 필드에 이어 파일의 절대 경로를 UTF 문자열로 기록한다.
	 *
	 * @param os	객체 출력 스트림.
	 * @throws IOException	기록 중 오류가 발생한 경우.
	 */
	private void writeObject(ObjectOutputStream os) throws IOException {
		os.defaultWriteObject();
		
		os.writeUTF(m_file.getAbsolutePath());
	}
	
	/**
	 * 역직렬화 시 기록된 절대 경로로부터 {@link File} 객체를 복원한다.
	 *
	 * @param is	객체 입력 스트림.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	기본 필드 역직렬화 중 클래스를 찾을 수 없는 경우.
	 */
	private void readObject(ObjectInputStream is) throws IOException, ClassNotFoundException {
		is.defaultReadObject();
		
		m_file = new File(is.readUTF());
	}
}
