package utils.io;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
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
public class IOUtils {
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
     * {@code reader}의 내용을 EOF까지 읽어 {@code writer}에 기록한다.
     * 두 스트림 모두 닫지 않으며, {@code writer}를 flush하지도 않는다.
     *
     * @param reader	입력 reader.
     * @param writer	출력 writer.
     * @param bufSize	전송 버퍼 크기(문자 수).
     * @return	전송한 문자 수.
     * @throws IOException	읽기 또는 쓰기 중 오류가 발생한 경우.
     */
    public static long transfer(Reader reader, Writer writer, int bufSize) throws IOException {
    	char[] buf = new char[bufSize];

    	long count = 0;
        for ( int nbytes = reader.read(buf); nbytes >= 0; nbytes = reader.read(buf) ) {
            writer.write(buf, 0, nbytes);
            count += nbytes;
        }

        return count;
    }
	
    /**
     * 입력 스트림의 내용을 EOF까지 읽어 출력 스트림에 기록한다.
     * 두 스트림 모두 닫지 않으며, 출력 스트림을 flush하지도 않는다.
     *
     * @param is	입력 스트림.
     * @param os	출력 스트림.
     * @param bufSize	전송 버퍼 크기(byte).
     * @return	전송한 byte 수.
     * @throws IOException	읽기 또는 쓰기 중 오류가 발생한 경우.
     */
    public static long transfer(InputStream is, OutputStream os, int bufSize) throws IOException {
        byte[] buf = new byte[bufSize];

        long count = 0;
        for ( int nbytes = is.read(buf); nbytes >= 0; nbytes = is.read(buf) ) {
            os.write(buf, 0, nbytes);
            count += nbytes;
        }

        return count;
    }
    
    /**
     * {@link #transfer(InputStream, OutputStream, int)}로 데이터를 전송한 뒤,
     * 성공 여부와 관계없이 두 스트림을 모두 닫는다. 닫는 중 발생한 예외는 무시한다.
     *
     * @param is	입력 스트림.
     * @param os	출력 스트림.
     * @param bufSize	전송 버퍼 크기(byte).
     * @return	전송한 byte 수.
     * @throws IOException	읽기 또는 쓰기 중 오류가 발생한 경우.
     */
    public static long transferAndClose(InputStream is, OutputStream os, int bufSize)
    	throws IOException {
		try {
			return transfer(is, os, bufSize);
		}
		finally {
			closeQuietly(is, os);
		}
    }
    
    /**
     * 입력 스트림의 남은 내용을 모두 읽어 byte 배열로 반환한다. 스트림은 닫지 않는다.
     *
     * @param is	입력 스트림.
     * @return	읽은 byte 배열.
     * @throws IOException	읽는 중 오류가 발생한 경우.
     */
    public static byte[] toBytes(InputStream is) throws IOException {
    	return is.readAllBytes();
    }
    
    /**
     * 파일의 내용을 byte 배열로 읽는다.
     * <p>
     * <b>주의:</b> {@link FileInputStream#read(byte[])}를 한 번만 호출하므로, 한 번에 다 읽히지 않으면
     * 배열의 나머지 부분이 0으로 채워진 채 반환될 수 있다. 또한 파일 길이를 {@code int}로 변환하므로
     * 2GB 이상의 파일은 지원하지 않는다.
     *
     * @param file	읽을 파일.
     * @return	파일 길이만큼의 byte 배열.
     * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
     */
    public static byte[] toBytes(File file) throws IOException {
    	byte[] bytes = new byte[(int)file.length()];
    	
    	try ( FileInputStream fis = new FileInputStream(file) ) {
			fis.read(bytes);
			
	    	return bytes;
		}
    }
    
    /**
     * 파일의 전체 내용을 플랫폼 기본 charset으로 디코딩하여 문자열로 반환한다.
     *
     * @param file	읽을 파일.
     * @return	파일 내용 문자열.
     * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
     */
    public static String toString(File file) throws IOException {
    	return new String(Files.readAllBytes(file.toPath()));
    }
    
    /**
     * 입력 스트림의 남은 내용을 주어진 charset으로 디코딩하여 문자열로 반환한다. 스트림은 닫지 않는다.
     *
     * @param is	입력 스트림.
     * @param charset	문자 인코딩.
     * @return	읽은 문자열.
     * @throws IOException	읽는 중 오류가 발생한 경우.
     */
    public static String toString(InputStream is, Charset charset) throws IOException {
    	return CharStreams.toString(new InputStreamReader(is, charset));
    }
    
    /**
     * reader의 남은 내용을 모두 읽어 문자열로 반환한다. reader는 닫지 않는다.
     *
     * @param reader	입력 reader.
     * @return	읽은 문자열.
     * @throws IOException	읽는 중 오류가 발생한 경우.
     */
    public static String toString(Reader reader) throws IOException {
    	return CharStreams.toString(reader);
    }
    
    /**
     * 파일의 전체 내용을 주어진 charset으로 디코딩하여 문자열로 반환한다.
     *
     * @param file	읽을 파일.
     * @param charset	문자 인코딩.
     * @return	파일 내용 문자열.
     * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
     */
    public static String toString(File file, Charset charset) throws IOException {
    	return new String(Files.readAllBytes(file.toPath()), charset);
    }
    
    /**
     * 문자열을 플랫폼 기본 charset으로 파일에 기록한다. 기존 파일은 덮어쓴다.
     *
     * @param str	기록할 문자열.
     * @param file	대상 파일.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void toFile(String str, File file) throws IOException {
    	try ( BufferedWriter writer = new BufferedWriter(new FileWriter(file)) ) { 
    		writer.write(str);
    	}
    }
    /**
     * 문자열을 주어진 charset으로 파일에 기록한다. 기존 파일은 덮어쓴다.
     *
     * @param str	기록할 문자열.
     * @param charset	문자 인코딩.
     * @param file	대상 파일.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void toFile(String str, Charset charset, File file) throws IOException {
    	try ( BufferedWriter writer = new BufferedWriter(new FileWriter(file, charset)) ) { 
    		writer.write(str);
    	}
    }
    
    /**
     * byte 배열을 파일에 기록한다. 기존 파일은 덮어쓴다.
     *
     * @param bytes	기록할 데이터.
     * @param file	대상 파일.
     * @throws IOException	파일 기록 중 오류가 발생한 경우.
     */
    public static void toFile(byte[] bytes, File file) throws IOException {
    	try ( FileOutputStream fos = new FileOutputStream(file); 
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		bos.write(bytes);
    	}
    }
    
    /**
     * 입력 스트림의 남은 내용을 파일에 기록한다. 기존 파일은 덮어쓴다.
     * 입력 스트림은 닫지 않는다.
     *
     * @param is	입력 스트림.
     * @param file	대상 파일.
     * @return	기록한 byte 수.
     * @throws IOException	읽기 또는 파일 기록 중 오류가 발생한 경우.
     */
    public static long toFile(InputStream is, File file) throws IOException {
	    try ( OutputStream out = new FileOutputStream(file) ) {
	        return is.transferTo(out);
	    }
    }
    
    /**
     * 입력 스트림의 남은 내용을 주어진 버퍼 크기로 파일에 기록한다. 기존 파일은 덮어쓴다.
     * {@link #toFile(InputStream, File)}과 달리 입력 스트림은 작업이 끝나면(실패한 경우 포함) 닫힌다.
     *
     * @param is	입력 스트림.
     * @param file	대상 파일.
     * @param bufSize	전송 버퍼 크기(byte).
     * @return	기록한 byte 수.
     * @throws IOException	읽기 또는 파일 기록 중 오류가 발생한 경우.
     */
    public static long toFile(InputStream is, File file, int bufSize) throws IOException {
    	try ( InputStream isc = is;
    			FileOutputStream fos = new FileOutputStream(file);
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		return transfer(isc, bos, bufSize);
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
		while ( length > 0 ) {
			int nbytes = is.read(buf, offset, length);
			if ( nbytes < 0 ) {
				throw new EOFException();
			}
			
			offset += nbytes;
			length -= nbytes;
		}
	}

	/**
	 * 가능한 한 버퍼 전체를 채울 때까지 입력 스트림에서 읽는다.
	 * 중간에 EOF에 도달하면 그때까지 읽은 만큼만 반환한다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @return	읽은 byte 수. 아무것도 읽지 못한 채 EOF에 도달한 경우는 {@code -1}.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static int readAtBest(InputStream is, byte[] buf) throws IOException {
		return IOUtils.readAtBest(is, buf, 0, buf.length);
	}

	/**
	 * 가능한 한 {@code length} byte를 읽어 버퍼의 {@code offset} 위치부터 저장한다.
	 * 중간에 EOF에 도달하면 그때까지 읽은 만큼만 반환한다.
	 *
	 * @param is	입력 스트림.
	 * @param buf	읽은 데이터를 저장할 버퍼.
	 * @param offset	버퍼 내 저장 시작 위치.
	 * @param length	읽을 최대 byte 수.
	 * @return	읽은 byte 수. 아무것도 읽지 못한 채 EOF에 도달한 경우는 {@code -1}
	 * 			({@code length}가 0이면 {@code 0}).
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static int readAtBest(InputStream is, byte[] buf, int offset, int length)
		throws IOException {
		int remains = length;
		while ( remains > 0 ) {
			int nbytes = is.read(buf, offset, remains);
			if ( nbytes == -1 ) {
				int nread = length - remains;
				return (nread > 0) ? nread : -1;
			}
			
			offset += nbytes;
			remains -= nbytes;
		}
		
		return length;
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
		deflater.setInput(bytes);
		
		ByteArrayOutputStream baos = null;
		try {
			baos = new ByteArrayOutputStream(bytes.length);
			
			deflater.finish();
			byte[] buffer = new byte[1024];
			while ( !deflater.finished() ) {
				int count = deflater.deflate(buffer);
				baos.write(buffer, 0, count);
			}
		}
		finally {
			IOUtils.closeQuietly(baos);
		}
		byte[] deflateds = baos.toByteArray();
		deflater.end();
		
		return deflateds;
	}
	
	/**
	 * {@link #compress(byte[])}로 압축된 데이터를 {@link Inflater}로 복원한다.
	 * <p>
	 * <b>주의:</b> 입력이 잘린(불완전한) 압축 데이터이면 {@link Inflater#finished()}가 {@code true}가
	 * 되지 않아 무한 루프에 빠진다.
	 *
	 * @param bytes	압축된 데이터.
	 * @return	복원된 byte 배열.
	 * @throws IOException	선언되어 있으나 현재 구현에서는 발생하지 않는다.
	 * @throws DataFormatException	압축 데이터의 형식이 잘못된 경우.
	 */
	public static byte[] decompress(byte[] bytes) throws IOException, DataFormatException {
		Inflater inflater = new Inflater();
		inflater.setInput(bytes);
		
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length);
		byte[] buffer = new byte[1024];
		while ( !inflater.finished() ) {
			int count = inflater.inflate(buffer);
			outputStream.write(buffer, 0, count);
		}
		outputStream.close();
		byte[] output = outputStream.toByteArray();
		
		inflater.end(); 
		return output;  
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
	 * 설정한 경우에만 닫힌다. 취소 요청은 각 블록을 기록한 뒤에만 확인되므로, 입력 스트림의
	 * {@code read}가 블록된 동안에는 취소가 즉시 반영되지 않는다.
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
		 * <p>
		 * <b>주의:</b> 값을 검사하지 않는다. 0이면 매 {@code read}가 0을 반환하여 복사가 끝나지 않고,
		 * 음수이면 실행 시 {@link NegativeArraySizeException}이 발생한다.
		 *
		 * @param size	버퍼 크기(byte).
		 * @return	본 객체.
		 */
		public CopyStream bufferSize(int size) {
			m_bufSize = size;
			return this;
		}
		
		@Override
		public Long executeWork() throws IOException {
	        byte[] buf = new byte[m_bufSize];
	        
			try ( m_from ) {
		        long total = 0;
		        for ( int nbytes = m_from.read(buf); nbytes >= 0; nbytes = m_from.read(buf) ) {
		            m_to.write(buf, 0, nbytes);
		            total += nbytes;
		            
		            if ( isCancelRequested() ) {
		            	break;
		            }
		        }

		        return total;
			}
			finally {
				if ( m_closeOutputOnFinished ) {
					IOUtils.closeQuietly(m_to);
				}
			}
		}

		@Override
		public boolean cancelWork() {
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