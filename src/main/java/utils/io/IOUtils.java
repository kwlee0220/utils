package utils.io;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.google.common.io.CharStreams;

import utils.Utilities;
import utils.async.AbstractThreadedExecution;
import utils.async.CancellableWork;
import utils.func.Tuple;

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
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	transfer(is, baos, 4096);
    	baos.close();
    	return baos.toByteArray();
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
    
    public static void toFile(byte[] bytes, File file) throws IOException {
    	try ( FileOutputStream fos = new FileOutputStream(file); 
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		bos.write(bytes);
    	}
    }
    
    public static long toFile(InputStream is, File file) throws IOException {
    	try ( InputStream isc = is;
    			FileOutputStream fos = new FileOutputStream(file);
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		return transfer(isc, bos, 16<<10);
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
		Utilities.checkArgument(pipeSize > 0, "invalid pipe size: " + pipeSize);
		
		try {
			PipedOutputStream pipeOut = new PipedOutputStream();
			return Tuple.of(pipeOut, new PipedInputStream(pipeOut, pipeSize));
		}
		catch ( IOException e ) {
			throw new RuntimeException(e);
		}
	}

	public static CopyStream copy(InputStream from, OutputStream to) {
		return new CopyStream(from, to);
	}
	
	public static final class CopyStream extends AbstractThreadedExecution<Long>
											implements CancellableWork {
		private final InputStream m_from;
		private final OutputStream m_to;
		private boolean m_closeInputOnFinished = false;
		private boolean m_closeOutputOnFinished = false;
		private int m_bufSize = 4 * 1024;
		
		private CopyStream(InputStream from, OutputStream to) {
			Objects.requireNonNull(from, "from InputStream");
			Objects.requireNonNull(to, "to OutputStream");
			
			m_from = from;
			m_to = to;
		}
		
		public CopyStream closeInputStreamOnFinished(boolean flag) {
			m_closeInputOnFinished = flag;
			return this;
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
		public Long executeWork() throws Exception {
	        byte[] buf = new byte[m_bufSize];
	        
			try {
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
				if ( m_closeInputOnFinished ) {
					IOUtils.closeQuietly(m_from);
				}
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
}