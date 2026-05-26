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
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IOUtils {
	private IOUtils() {
		throw new AssertionError("Should not be called: class=" + IOUtils.class.getName());
	}
	
	public static void close(Object closeable) throws Exception {
		if ( closeable != null && closeable instanceof AutoCloseable ) {
			((AutoCloseable)closeable).close();
		}
	}
	
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
	
	public static void closeQuietly(AutoCloseable... closeables) {
		Stream.of(closeables)
				.filter(c -> c != null)
				.forEach(IOUtils::closeQuietly);
	}
	
	public static void closeQuietly(Collection<? extends AutoCloseable> closeables) {
		closeables.stream()
				.filter(c -> c != null)
				.forEach(IOUtils::closeQuietly);
	}
	
    public static long transfer(Reader reader, Writer writer, int bufSize) throws IOException {
    	char[] buf = new char[bufSize];

    	long count = 0;
        for ( int nbytes = reader.read(buf); nbytes >= 0; nbytes = reader.read(buf) ) {
            writer.write(buf, 0, nbytes);
            count += nbytes;
        }

        return count;
    }
	
    public static long transfer(InputStream is, OutputStream os, int bufSize) throws IOException {
        byte[] buf = new byte[bufSize];

        long count = 0;
        for ( int nbytes = is.read(buf); nbytes >= 0; nbytes = is.read(buf) ) {
            os.write(buf, 0, nbytes);
            count += nbytes;
        }

        return count;
    }
    
    public static long transferAndClose(InputStream is, OutputStream os, int bufSize)
    	throws IOException {
		try {
			return transfer(is, os, bufSize);
		}
		finally {
			closeQuietly(is, os);
		}
    }
    
    public static byte[] toBytes(InputStream is) throws IOException {
    	return is.readAllBytes();
    }
    
    public static byte[] toBytes(File file) throws IOException {
    	byte[] bytes = new byte[(int)file.length()];
    	
    	try ( FileInputStream fis = new FileInputStream(file) ) {
			fis.read(bytes);
			
	    	return bytes;
		}
    }
    
    public static String toString(File file) throws IOException {
    	return new String(Files.readAllBytes(file.toPath()));
    }
    
    public static String toString(InputStream is, Charset charset) throws IOException {
    	return CharStreams.toString(new InputStreamReader(is, charset));
    }
    
    public static String toString(Reader reader) throws IOException {
    	return CharStreams.toString(reader);
    }
    
    public static String toString(File file, Charset charset) throws IOException {
    	return new String(Files.readAllBytes(file.toPath()), charset);
    }
    
    public static void toFile(String str, File file) throws IOException {
    	try ( BufferedWriter writer = new BufferedWriter(new FileWriter(file)) ) { 
    		writer.write(str);
    	}
    }
    public static void toFile(String str, Charset charset, File file) throws IOException {
    	try ( BufferedWriter writer = new BufferedWriter(new FileWriter(file, charset)) ) { 
    		writer.write(str);
    	}
    }
    
    public static void toFile(byte[] bytes, File file) throws IOException {
    	try ( FileOutputStream fos = new FileOutputStream(file); 
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		bos.write(bytes);
    	}
    }
    
    public static long toFile(InputStream is, File file) throws IOException {
	    try ( OutputStream out = new FileOutputStream(file) ) {
	        return is.transferTo(out);
	    }
    }
    
    public static long toFile(InputStream is, File file, int bufSize) throws IOException {
    	try ( InputStream isc = is;
    			FileOutputStream fos = new FileOutputStream(file);
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		return transfer(isc, bos, bufSize);
    	}
    }

	public static void readFully(InputStream is, byte[] buf) throws IOException {
		readFully(is, buf, 0, buf.length);
	}

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

	public static int readAtBest(InputStream is, byte[] buf) throws IOException {
		return IOUtils.readAtBest(is, buf, 0, buf.length);
	}

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
	
	public static String stringify(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
	
	public static byte[] destringify(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}
	
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

	public static CopyStream copyAsync(InputStream from, OutputStream to) {
		return new CopyStream(from, to);
	}
	
	public static long copy(InputStream from, OutputStream to) throws IOException {
		return from.transferTo(to);
	}
	
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
		
		public CopyStream closeOutputStreamOnFinished(boolean flag) {
			m_closeOutputOnFinished = flag;
			return this;
		}
		
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