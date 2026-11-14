package utils.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

/**
 * 시스템 기본 임시 디렉토리에 생성한 임시 파일을 감싸는 객체이다.
 * {@link #close()}를 호출하면 해당 파일을 삭제한다.
 * <pre>{@code
 * try ( TempFile tmp = TempFile.create("upload-", ".json") ) {
 *     Files.writeString(tmp.getPath(), json);
 *     ...
 * }
 * }</pre>
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class TempFile implements Closeable {
	private final Path m_path;

	private TempFile(Path path) {
		m_path = path;
	}

	/**
	 * 임시 파일을 생성한다. 이름의 접두어·접미어는 JDK 기본값을 쓴다.
	 *
	 * @return	임시 파일 객체.
	 * @throws IOException	임시 파일 생성 중 오류가 발생한 경우.
	 */
	public static TempFile create() throws IOException {
		return create(null, null);
	}

	/**
	 * 주어진 접두어와 접미어로 이름을 붙인 임시 파일을 생성한다. {@link Files#createTempFile(String, String,
	 * java.nio.file.attribute.FileAttribute...)}의 규칙을 따른다.
	 *
	 * @param prefix	파일 이름 접두어. {@code null}이면 접두어 없음.
	 * @param suffix	파일 이름 접미어. {@code null}이면 {@code ".tmp"}.
	 * @return	임시 파일 객체.
	 * @throws IOException	임시 파일 생성 중 오류가 발생한 경우.
	 */
	public static TempFile create(@Nullable String prefix, @Nullable String suffix) throws IOException {
		return new TempFile(Files.createTempFile(prefix, suffix));
	}

	/**
	 * 임시 파일의 경로를 반환한다.
	 *
	 * @return	임시 파일 경로.
	 */
	public Path getPath() {
		return m_path;
	}

	/**
	 * 임시 파일의 {@link File} 객체를 반환한다.
	 *
	 * @return	임시 파일.
	 */
	public File getFile() {
		return m_path.toFile();
	}

	/**
	 * 임시 파일에 쓰기 위한 출력 스트림을 연다.
	 * 기존 내용은 덮어쓰며, 반환된 스트림은 호출자가 닫아야 한다.
	 *
	 * @return	파일 출력 스트림.
	 * @throws IOException	파일을 열 수 없는 경우.
	 */
	public OutputStream getOutputStream() throws IOException {
		return Files.newOutputStream(m_path);
	}

	/**
	 * 임시 파일을 삭제한다. 이미 지워졌으면 아무것도 하지 않는다.
	 *
	 * @throws IOException	삭제에 실패한 경우.
	 */
	@Override
	public void close() throws IOException {
		Files.deleteIfExists(m_path);
	}

	@Override
	public String toString() {
		return "TempFile[" + m_path + "]";
	}
}
