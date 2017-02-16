package utils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.Maps;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IOUtils {
	private IOUtils() {
		throw new AssertionError("Should not be called: class=" + IOUtils.class.getName());
	}
	
//	public static boolean closeQuietly(AutoCloseable closeable) {
//		if ( closeable != null ) {
//			try {
//				closeable.close();
//				return true;
//			}
//			catch ( Exception ignored ) {}
//		}
//		
//		return false;
//	}
//	
//	public static void closeQuietly(AutoCloseable... closeables) {
//		Stream.of(closeables).forEach(IOUtils::closeQuietly);
//	}
//	
//    public static int transfer(InputStream is, OutputStream os, int bufSize) throws IOException {
//        byte[] buf = new byte[bufSize];
//
//        int count = 0;
//        for ( int nbytes = is.read(buf); nbytes >= 0; nbytes = is.read(buf) ) {
//            os.write(buf, 0, nbytes);
//            count += nbytes;
//        }
//
//        return count;
//    }
//    
//    public static int transferAndClose(InputStream is, OutputStream os, int bufSize)
//    	throws IOException {
//		try {
//			return transfer(is, os, bufSize);
//		}
//		finally {
//			try { is.close(); } catch ( Exception ignored ) { }
//			try { os.close(); } catch ( Exception ignored ) { }
//		}
//    }
//    
//    public static byte[] toBytes(InputStream is, boolean closeStream) throws IOException {
//    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
//    	try {
//	    	transfer(is, baos, 4096);
//	    	baos.close();
//	    	
//	    	return baos.toByteArray();
//    	}
//    	finally {
//    		if ( closeStream ) {
//    			try {
//					is.close();
//				}
//				catch ( IOException ignored ) { }
//    		}
//    	}
//    }
//    
//    public static byte[] toBytes(File file) throws IOException {
//    	byte[] bytes = new byte[(int)file.length()];
//    	
//    	FileInputStream fis = new FileInputStream(file);
//    	try {
//			fis.read(bytes);
//			
//	    	return bytes;
//		}
//    	finally {
//    		try { fis.close(); } catch ( IOException ignored ) { }
//    	}
//    }
//    
//    public static void toFile(byte[] bytes, File file) throws IOException {
//    	FileOutputStream fos = new FileOutputStream(file);
//    	BufferedOutputStream bos = new BufferedOutputStream(fos);
//    	
//    	try {
//    		bos.write(bytes);
//    	}
//    	finally {
//    		try { bos.close(); } catch ( IOException ignored ) { }
//    	}
//    }
//    
//    public static void toFile(InputStream is, File file) throws IOException {
//    	FileOutputStream fos = new FileOutputStream(file);
//    	BufferedOutputStream bos = new BufferedOutputStream(fos);
//    	
//    	try {
//    		transfer(is, bos, 16<<10);
//    	}
//    	finally {
//    		try { bos.close(); } catch ( IOException ignored ) { }
//    	}
//    }
//
//	public static void readFully(InputStream is, byte[] buf) throws IOException {
//		IOUtils.readFully(is, buf, 0, buf.length);
//	}
//
//	public static void readFully(InputStream is, byte[] buf, int offset, int remains)
//		throws IOException {
//		while ( remains > 0 ) {
//			int nbytes = is.read(buf, offset, remains);
//			if ( nbytes < 0 ) {
//				throw new IOException("reached EOF");
//			}
//			
//			offset += nbytes;
//			remains -= nbytes;
//		}
//	}
//
//	public static int readAtBest(InputStream is, byte[] buf) throws IOException {
//		return IOUtils.readAtBest(is, buf, 0, buf.length);
//	}
//
//	public static int readAtBest(InputStream is, byte[] buf, int offset, int length)
//		throws IOException {
//		int remains = length;
//		while ( remains > 0 ) {
//			int nbytes = is.read(buf, offset, remains);
//			if ( nbytes < 0 ) {
//				return length - remains;
//			}
//			
//			offset += nbytes;
//			remains -= nbytes;
//		}
//		
//		return length;
//	}
//	
//    private static final int transfer(ZipInputStream is, OutputStream os, int bufSize)
//    	throws IOException {
//        byte[] buf = new byte[bufSize];
//
//        int count = 0;
//        for ( int nbytes = is.read(buf); nbytes >= 0; nbytes = is.read(buf) ) {
//            os.write(buf, 0, nbytes);
//            count += nbytes;
//        }
//
//        return count;
//    }
//    
//    public static final void zipDir(File targetDir, ZipOutputStream zos) throws IOException  {
//    	zipDir(targetDir, targetDir.getPath().length() + 1, zos);
//    }
//    
//    private static final void zipDir(File targetDir, int prefixLength, ZipOutputStream zos)
//    	throws IOException  {
//		int nbytes = 0;
//		byte[] buf = new byte[8<<10];
//	
//		for ( String fileName: targetDir.list() ) {
//			File file = new File(targetDir, fileName);
//			if ( file.isDirectory() ) {
//				zipDir(file, prefixLength, zos);
//				continue;
//			}
//			
//			FileInputStream fis = new FileInputStream(file);
//			try {
//    			ZipEntry entry = new ZipEntry(file.getPath().substring(prefixLength));
//    			zos.putNextEntry(entry);
//    			while ( (nbytes = fis.read(buf)) != -1 ) {
//    				zos.write(buf, 0, nbytes);
//    			}
//			}
//			finally {
//				fis.close();
//			}
//		}
//    }
//	
//	public static byte[] compress(byte[] bytes) throws IOException {
//		Deflater deflater = new Deflater();
//		deflater.setInput(bytes);
//		
//		ByteArrayOutputStream baos = null;
//		try {
//			baos = new ByteArrayOutputStream(bytes.length);
//			
//			deflater.finish();
//			byte[] buffer = new byte[1024];
//			while ( !deflater.finished() ) {
//				int count = deflater.deflate(buffer);
//				baos.write(buffer, 0, count);
//			}
//		}
//		finally {
//			IOUtils.closeQuietly(baos);
//		}
//		byte[] deflateds = baos.toByteArray();
//		deflater.end();
//		
//		return deflateds;
//	}
//	
//	public static byte[] decompress(byte[] bytes) throws IOException, DataFormatException {
//		Inflater inflater = new Inflater();
//		inflater.setInput(bytes);
//		
//		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length);
//		byte[] buffer = new byte[1024];
//		while ( !inflater.finished() ) {
//			int count = inflater.inflate(buffer);
//			outputStream.write(buffer, 0, count);
//		}
//		outputStream.close();
//		byte[] output = outputStream.toByteArray();
//		
//		inflater.end(); 
//		return output;  
//	}
//	
//	public static byte[] serialize(Serializable obj) throws IOException {
//		ByteArrayOutputStream baos = new ByteArrayOutputStream();
//		try ( ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
//			oos.writeObject(obj);
//		}
//		baos.close();
//		
//		return baos.toByteArray();
//	}
//	
//	public static Object deserialize(byte[] serialized)
//		throws IOException, ClassNotFoundException {
//		try ( ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
//				ObjectInputStream ois = new ObjectInputStream(bais); ) {
//			return ois.readObject();
//		}
//	}
//	
//	public static String stringify(byte[] bytes) {
//		return Base64.getEncoder().encodeToString(bytes);
//	}
//	
//	public static byte[] destringify(String encoded) {
//		return Base64.getDecoder().decode(encoded);
//	}
}