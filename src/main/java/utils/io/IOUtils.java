package utils.io;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.jetbrains.annotations.Nullable;

import com.google.common.io.CharStreams;

import utils.Preconditions;
import utils.Tuple;
import utils.async.AbstractThreadedExecution;
import utils.async.CancellableWork;

/**
 * 스트림 닫기, 데이터 전송, 파일 입출력, 압축, Base64 변환, pipe 생성 등
 * 입출력 관련 정적 유틸리티 메소드들을 제공한다.
 * @author Kang-Woo Lee (ETRI)
 */
public final class IOUtils {
	private IOUtils() {
		throw new AssertionError("Should not be called: class=" + IOUtils.class.getName());
	}
	
	/**
	 * 주어진 객체가 {@link AutoCloseable}이면 닫는다.
	 * {@code null}이거나 {@link AutoCloseable}이 아니면 아무것도 하지 않는다.
	 *
	 * @param closeable	닫을 객체.
	 * @throws Exception	{@link AutoCloseable#close()}에서 발생한 예외.
	 */
	public static void close(Object closeable) throws Exception {
		if ( closeable != null && closeable instanceof AutoCloseable ) {
			((AutoCloseable)closeable).close();
		}
	}
	
	/**
	 * 주어진 객체가 {@link AutoCloseable}이면 닫고, 닫는 중 발생한 예외는 무시한다.
	 *
	 * @param closeable	닫을 객체.
	 * @return	예외 없이 닫힌 경우는 {@code true}, {@code null}이거나 {@link AutoCloseable}이 아니거나
	 * 			닫는 중 예외가 발생한 경우는 {@code false}.
	 */
	public static boolean closeQuietly(Object closeable) {
		if ( closeable != null && closeable instanceof AutoCloseable ) {
			try {
				((AutoCloseable)closeable).close();
				return true;
			}
			catch ( Exception ignored ) {}
		}
		
		return false;
	}
	
	/**
	 * 주어진 객체들을 차례로 닫는다. {@code null} 원소는 건너뛰며, 닫는 중 발생한 예외는 무시한다.
	 *
	 * @param closeables	닫을 객체들.
	 */
	public static void closeQuietly(AutoCloseable... closeables) {
		Stream.of(closeables)
				.filter(c -> c != null)
				.forEach(IOUtils::closeQuietly);
	}
	
	/**
	 * 주어진 컬렉션의 객체들을 차례로 닫는다. {@code null} 원소는 건너뛰며, 닫는 중 발생한 예외는 무시한다.
	 *
	 * @param closeables	닫을 객체 컬렉션.
	 */
	public static void closeQuietly(Collection<? extends AutoCloseable> closeables) {
		closeables.stream()
				.filter(c -> c != null)
				.forEach(IOUtils::closeQuietly);
	}
	
    /**
     * {@code reader}의 내용을 EOF까지 읽어 {@code writer}에 기록한다. {@link Reader#transferTo(Writer)}와 같다.
     * 두 스트림 모두 닫지 않으며, {@code writer}를 flush하지도 않는다.
     *
     * @param reader	입력 reader.
     * @param writer	출력 writer.
     * @return	전송한 문자 수.
     * @throws IOException	읽기 또는 쓰기 중 오류가 발생한 경우.
     */
    public static long copy(Reader reader, Writer writer) throws IOException {
    	return reader.transferTo(writer);
    }
    
    /**
     * 파일의 전체 내용을 byte 배열로 읽는다. {@link Files#readAllBytes(Path)}와 같다.
     *
     * @param file	읽을 파일.
     * @return	파일의 전체 내용.
     * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
     * @throws OutOfMemoryError	파일이 배열 하나에 담을 수 없을 만큼 큰 경우(2GB 이상).
     */
    public static byte[] readAllBytes(File file) throws IOException {
    	return Files.readAllBytes(file.toPath());
    }
    
    /**
     * 파일의 전체 내용을 UTF-8로 디코딩하여 문자열로 반환한다. {@link Files#readString(Path)}와 같다.
     *
     * @param file	읽을 파일.
     * @return	파일 내용 문자열.
     * @throws IOException	파일을 읽는 중 오류가 발생했거나, 내용이 올바른 UTF-8이 아닌 경우.
     */
    public static String readString(File file) throws IOException {
    	return Files.readString(file.toPath());
    }
    
    /**
     * 파일의 전체 내용을 주어진 charset으로 디코딩하여 문자열로 반환한다.
     *
     * @param file	읽을 파일.
     * @param charset	문자 인코딩.
     * @return	파일 내용 문자열.
     * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
     */
    public static String readString(File file, Charset charset) throws IOException {
    	return Files.readString(file.toPath(), charset);
    }
    
    /**
     * 입력 스트림의 남은 내용을 주어진 charset으로 디코딩하여 문자열로 반환한다. 스트림은 닫지 않는다.
     *
     * @param is	입력 스트림.
     * @param charset	문자 인코딩.
     * @return	읽은 문자열.
     * @throws IOException	읽는 중 오류가 발생한 경우.
     */
    public static String readString(InputStream is, Charset charset) throws IOException {
    	return new String(is.readAllBytes(), charset);
    }
    
    /**
     * reader의 남은 내용을 모두 읽어 문자열로 반환한다. reader는 닫지 않는다.
     *
     * @param reader	입력 reader.
     * @return	읽은 문자열.
     * @throws IOException	읽는 중 오류가 발생한 경우.
     */
    public static String readString(Reader reader) throws IOException {
    	return CharStreams.toString(reader);
    }
    
    /**
     * 문자열을 UTF-8로 파일에 기록한다. 기존 파일은 덮어쓴다. {@link Files#writeString(Path, CharSequence,
     * java.nio.file.OpenOption...)}와 같다.
     *
     * @param file	대상 파일.
     * @param str	기록할 문자열.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void writeString(File file, String str) throws IOException {
    	Files.writeString(file.toPath(), str);
    }
    
    /**
     * 문자열을 주어진 charset으로 파일에 기록한다. 기존 파일은 덮어쓴다.
     *
     * @param file	대상 파일.
     * @param str	기록할 문자열.
     * @param charset	문자 인코딩.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void writeString(File file, String str, Charset charset) throws IOException {
    	Files.writeString(file.toPath(), str, charset);
    }
    
    /**
     * byte 배열을 파일에 기록한다. 기존 파일은 덮어쓴다.
     *
     * @param file	대상 파일.
     * @param bytes	기록할 데이터.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void write(File file, byte[] bytes) throws IOException {
    	Files.write(file.toPath(), bytes);
    }
    
    /**
     * 입력 스트림의 남은 내용을 파일에 기록한다. 기존 파일은 덮어쓴다.
     * 입력 스트림은 닫지 않는다. 닫는 것은 호출자의 몫이다.
     *
     * @param file	대상 파일.
     * @param is	입력 스트림.
     * @return	기록한 byte 수.
     * @throws IOException	읽기 또는 파일 기록 중 오류가 발생한 경우.
     */
    public static long write(File file, InputStream is) throws IOException {
	    try ( OutputStream out = Files.newOutputStream(file.toPath()) ) {
	        return is.transferTo(out);
	    }
    }

	/**
	 * 주어진 버퍼 전체가 채워질 때까지 입력 스트림에서 읽는다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @throws EOFException	버퍼를 다 채우기 전에 EOF에 도달한 경우.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static void readFully(InputStream is, byte[] buf) throws IOException {
		readFully(is, buf, 0, buf.length);
	}

	/**
	 * {@code length} byte를 모두 읽을 때까지 입력 스트림에서 읽어 버퍼의 {@code offset} 위치부터 저장한다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @param offset	버퍼 내 저장 시작 위치.
	 * @param length	읽을 byte 수.
	 * @throws EOFException	{@code length} byte를 다 읽기 전에 EOF에 도달한 경우.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static void readFully(InputStream is, byte[] buf, int offset, int length)
		throws IOException {
		int nread = is.readNBytes(buf, offset, length);
		if ( nread < length ) {
			throw new EOFException("expected " + length + " bytes, but read " + nread);
		}
	}

	/**
	 * 버퍼가 다 찰 때까지 읽되, 중간에 EOF에 도달하면 그때까지 읽은 만큼만 반환한다.
	 * {@link InputStream#readNBytes(byte[], int, int)}와 같지만 EOF 표시가 다르다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @return	읽은 byte 수. 아무것도 읽지 못한 채 EOF에 도달한 경우는 {@code -1}.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static int readUpTo(InputStream is, byte[] buf) throws IOException {
		return readUpTo(is, buf, 0, buf.length);
	}

	/**
	 * 최대 {@code length} byte를 읽어 버퍼의 {@code offset} 위치부터 저장한다. 중간에 EOF에 도달하면
	 * 그때까지 읽은 만큼만 반환한다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @param offset	버퍼 내 저장 시작 위치.
	 * @param length	읽을 최대 byte 수.
	 * @return	읽은 byte 수. 아무것도 읽지 못한 채 EOF에 도달한 경우는 {@code -1}
	 * 			({@code length}가 0이면 {@code 0}).
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static int readUpTo(InputStream is, byte[] buf, int offset, int length) throws IOException {
		int nread = is.readNBytes(buf, offset, length);
		return (nread == 0 && length > 0) ? -1 : nread;
	}
	
	/**
	 * byte 배열을 {@link Deflater}(기본 압축 수준)로 압축한다.
	 *
	 * @param bytes	압축할 데이터.
	 * @return	압축된 byte 배열.
	 * @throws IOException	선언되어 있으나 현재 구현에서는 발생하지 않는다.
	 */
	public static byte[] compress(byte[] bytes) throws IOException {
		Deflater deflater = new Deflater();
		try {
			deflater.setInput(bytes);
			deflater.finish();

			ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
			byte[] buffer = new byte[1024];
			while ( !deflater.finished() ) {
				int count = deflater.deflate(buffer);
				baos.write(buffer, 0, count);
			}
			return baos.toByteArray();
		}
		finally {
			deflater.end();
		}
	}
	
	/**
	 * {@link #compress(byte[])}로 압축된 데이터를 {@link Inflater}로 복원한다.
	 *
	 * @param bytes	압축된 데이터.
	 * @return	복원된 byte 배열.
	 * @throws IOException	선언되어 있으나 현재 구현에서는 발생하지 않는다.
	 * @throws DataFormatException	압축 데이터의 형식이 잘못되었거나, 데이터가 중간에 잘렸거나(입력이 끝났는데
	 * 								압축 스트림이 끝나지 않음), 복원에 사전(dictionary)이 필요한 경우.
	 */
	public static byte[] decompress(byte[] bytes) throws IOException, DataFormatException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(bytes);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length);
			byte[] buffer = new byte[1024];
			while ( !inflater.finished() ) {
				int count = inflater.inflate(buffer);
				if ( count == 0 && !inflater.finished() ) {
					// 출력이 없는데 끝나지도 않았다: 입력이 모자라거나 사전이 필요하다. 계속 돌면 무한 루프가 된다.
					if ( inflater.needsDictionary() ) {
						throw new DataFormatException("compressed data requires a preset dictionary");
					}
					if ( inflater.needsInput() ) {
						throw new DataFormatException("truncated compressed data: input ended after "
														+ outputStream.size() + " bytes of output");
					}
				}
				outputStream.write(buffer, 0, count);
			}
			return outputStream.toByteArray();
		}
		finally {
			inflater.end();
		}
	}
	
	/**
	 * byte 배열을 Base64 문자열로 인코딩한다.
	 *
	 * @param bytes	인코딩할 데이터.
	 * @return	Base64 문자열.
	 */
	public static String stringify(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
	
	/**
	 * Base64 문자열을 디코딩하여 byte 배열로 반환한다.
	 *
	 * @param encoded	Base64 문자열.
	 * @return	디코딩된 byte 배열.
	 * @throws IllegalArgumentException	{@code encoded}가 올바른 Base64 문자열이 아닌 경우.
	 */
	public static byte[] destringify(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}
	
	/**
	 * 주어진 버퍼 크기로 서로 연결된 {@link PipedOutputStream}과 {@link PipedInputStream} 쌍을 생성한다.
	 * 두 스트림은 서로 다른 스레드에서 사용해야 한다.
	 *
	 * @param pipeSize	pipe 버퍼 크기(byte). 0보다 커야 한다.
	 * @return	({@code PipedOutputStream}, {@code PipedInputStream}) 쌍.
	 * @throws IllegalArgumentException	{@code pipeSize}가 0 이하인 경우.
	 */
	public static Tuple<PipedOutputStream, PipedInputStream> pipe(int pipeSize) {
		Preconditions.checkArgument(pipeSize > 0, "invalid pipe size: " + pipeSize);
		
		try {
			PipedOutputStream pipeOut = new PipedOutputStream();
			return Tuple.of(pipeOut, new PipedInputStream(pipeOut, pipeSize));
		}
		catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@code from}의 내용을 {@code to}로 복사하는 비동기 실행 객체를 생성한다.
	 * 실행은 시작되지 않은 상태로 반환되므로, 호출자가 {@link CopyStream#start()}로 시작해야 한다.
	 *
	 * @param from	입력 스트림.
	 * @param to	출력 스트림.
	 * @return	복사 작업을 수행할 {@link CopyStream} 객체.
	 * @throws IllegalArgumentException	{@code from} 또는 {@code to}가 {@code null}인 경우.
	 */
	public static CopyStream copyAsync(InputStream from, OutputStream to) {
		return new CopyStream(from, to);
	}
	
	/**
	 * 입력 스트림의 남은 내용을 출력 스트림으로 복사한다. 두 스트림 모두 닫지 않는다.
	 *
	 * @param from	입력 스트림.
	 * @param to	출력 스트림.
	 * @return	복사한 byte 수.
	 * @throws IOException	읽기 또는 쓰기 중 오류가 발생한 경우.
	 */
	public static long copy(InputStream from, OutputStream to) throws IOException {
		return from.transferTo(to);
	}
	
	/**
	 * 입력 스트림의 내용을 출력 스트림으로 복사하는 스레드 기반 비동기 실행이다.
	 * 실행 결과는 복사한 byte 수이다.
	 * <p>
	 * 입력 스트림은 복사가 끝나면 항상 닫히며, 출력 스트림은 {@link #closeOutputStreamOnFinished(boolean)}로
	 * 설정한 경우에만 닫힌다.
	 * <p>
	 * {@link #cancel(boolean)}은 입력 스트림을 닫아 블록된 {@code read}를 깨운다. 닫으면 대기 중인 {@code read}가
	 * 실패하는 스트림(소켓 등)에서는 바로 반영되고, 그렇지 않은 스트림에서는 다음 블록을 읽은 뒤 반영된다.
	 * 취소된 복사는 복사한 양과 관계없이 취소 상태로 끝난다.
	 */
	public static final class CopyStream extends AbstractThreadedExecution<Long>
											implements CancellableWork {
		private final InputStream m_from;
		private final OutputStream m_to;
		private boolean m_closeOutputOnFinished = false;
		private int m_bufSize = 4 * 1024;
		
		private CopyStream(InputStream from, OutputStream to) {
			Preconditions.checkNotNullArgument(from, "from InputStream is null");
			Preconditions.checkNotNullArgument(to, "to OutputStream is null");
			
			m_from = from;
			m_to = to;
		}
		
		/**
		 * 복사 작업이 끝났을 때(성공, 실패, 취소 모두) 출력 스트림을 닫을지 여부를 설정한다.
		 * 기본값은 {@code false}이다.
		 *
		 * @param flag	출력 스트림을 닫으려면 {@code true}.
		 * @return	본 객체.
		 */
		public CopyStream closeOutputStreamOnFinished(boolean flag) {
			m_closeOutputOnFinished = flag;
			return this;
		}
		
		/**
		 * 복사 버퍼 크기(byte)를 설정한다. 기본값은 4KB이다.
		 *
		 * @param size	버퍼 크기(byte). 0보다 커야 한다.
		 * @return	본 객체.
		 * @throws IllegalArgumentException	{@code size}가 0 이하인 경우.
		 */
		public CopyStream bufferSize(int size) {
			Preconditions.checkArgument(size > 0, "invalid buffer size: " + size);
			m_bufSize = size;
			return this;
		}
		
		@Override
		public Long executeWork() throws IOException, CancellationException {
	        byte[] buf = new byte[m_bufSize];
	        
			try ( m_from ) {
		        long total = 0;
		        for ( int nbytes = m_from.read(buf); nbytes >= 0; nbytes = m_from.read(buf) ) {
		            m_to.write(buf, 0, nbytes);
		            total += nbytes;
		            
		            if ( isCancelRequested() ) {
		            	throw new CancellationException("copy cancelled after " + total + " bytes");
		            }
		        }

		        return total;
			}
			catch ( IOException e ) {
				// cancelWork()가 입력 스트림을 닫아 생긴 실패는 취소로 본다.
				if ( isCancelRequested() ) {
					CancellationException ce = new CancellationException("copy cancelled");
					ce.initCause(e);
					throw ce;
				}
				throw e;
			}
			finally {
				if ( m_closeOutputOnFinished ) {
					IOUtils.closeQuietly(m_to);
				}
			}
		}

		/**
		 * 입력 스트림을 닫아 블록된 {@code read}를 깨운다. 작업 스레드는 그 실패를 보고 취소로 끝난다.
		 */
		@Override
		public boolean cancelWork() {
			IOUtils.closeQuietly(m_from);
			return true;
		}
	}

	/**
	 * 현재 file position부터 한 줄을 바이트 단위로 읽어 UTF-8로 디코딩한다.
	 * <p>
	 * {@link RandomAccessFile#readLine()}은 각 바이트를 그대로 {@code char}로 확장하여(사실상
	 * ISO-8859-1) UTF-8 멀티바이트 문자(한글 등)를 깨뜨리므로, 이를 대체한다.
	 * 줄 종료는 {@code \n}, {@code \r}, {@code \r\n} 또는 EOF로 인식하며, 종료 문자는 결과에
	 * 포함하지 않는다. 아무 바이트도 읽지 못한 채 EOF에 도달하면 {@code null}을 반환한다.
	 * 개행 없이 EOF로 끝나는 마지막 조각도 그대로 반환하므로 {@code readLine()}의 의미를 따른다.
	 *
	 * @param raf	읽을 대상 파일. 현재 file position부터 읽는다.
	 * @return	디코딩된 한 줄. EOF에서 읽은 바이트가 없으면 {@code null}.
	 * @throws IOException	파일 입출력 중 오류가 발생한 경우.
	 */
	public static @Nullable String readLineUtf8(RandomAccessFile raf) throws IOException {
		return readLineUtf8(raf, false);
	}

	/**
	 * 현재 file position부터 한 줄을 바이트 단위로 읽어 UTF-8로 디코딩한다.
	 * <p>
	 * {@code requireNewline}이 {@code true}이면 개행({@code \n}, {@code \r}, {@code \r\n})으로
	 * 종료된 완전한 줄만 반환한다. 개행 없이 EOF에 먼저 도달한 미완성 조각을 만나면 file pointer를
	 * 그 줄의 시작 위치로 되돌리고 {@code null}을 반환한다. 따라서 아직 끝까지 기록되지 않은 줄을
	 * 조기에 소비하지 않으며, 파일이 더 채워진 뒤 다시 호출하면 같은 줄을 처음부터 읽을 수 있다.
	 * tail 류의 점진적 읽기에 유용하다.
	 * <p>
	 * {@code requireNewline}이 {@code false}이면 {@link #readLineUtf8(RandomAccessFile)}과 동일하게
	 * 동작하여, 개행 없이 EOF로 끝나는 마지막 조각도 그대로 반환한다.
	 *
	 * @param raf				읽을 대상 파일. 현재 file position부터 읽는다.
	 * @param requireNewline	개행으로 종료된 완전한 줄만 반환할지 여부.
	 * @return	디코딩된 한 줄. EOF에서 읽은 바이트가 없거나, {@code requireNewline}이 {@code true}인데
	 * 			완전한 줄이 없으면 {@code null}.
	 * @throws IOException	파일 입출력 중 오류가 발생한 경우.
	 */
	public static @Nullable String readLineUtf8(RandomAccessFile raf, boolean requireNewline) throws IOException {
		long start = raf.getFilePointer();
		ByteArrayOutputStream buf = null;
		boolean readAny = false;
		boolean terminated = false;

		int b;
		while ( (b = raf.read()) != -1 ) {
			readAny = true;
			if ( b == '\n' ) {
				terminated = true;
				break;
			}
			if ( b == '\r' ) {
				// '\r\n'이면 뒤따르는 '\n'까지 소비하고, 단독 '\r'이면 읽었던 위치로 되돌린다.
				long mark = raf.getFilePointer();
				int next = raf.read();
				if ( next == -1 ) {
					// '\r' 직후 EOF이면 뒤따르는 '\n'이 아직 기록되지 않았을 수 있으므로
					// 완전한 줄을 요구하는 경우 미완성으로 간주한다.
					if ( requireNewline ) {
						raf.seek(start);
						return null;
					}
				}
				else if ( next != '\n' ) {
					raf.seek(mark);
				}
				terminated = true;
				break;
			}
			if ( buf == null ) {
				buf = new ByteArrayOutputStream();
			}
			buf.write(b);
		}

		if ( !readAny ) {
			return null;
		}
		if ( requireNewline && !terminated ) {
			// 개행 없이 EOF에 도달한 미완성 조각 — 되돌리고 다음 호출을 기다린다.
			raf.seek(start);
			return null;
		}
		return (buf == null) ? "" : buf.toString(StandardCharsets.UTF_8);
	}
}
