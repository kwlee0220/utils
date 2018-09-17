package utils.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import com.google.common.collect.Lists;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class IOUtils {
	private IOUtils() {
		throw new AssertionError("Should not be called: class=" + IOUtils.class.getName());
	}
	
	public static boolean closeQuietly(AutoCloseable closeable) {
		if ( closeable != null ) {
			try {
				closeable.close();
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
	
	public static void closeQuietly(Collection<AutoCloseable> closeables) {
		closeables.stream()
				.filter(c -> c != null)
				.forEach(IOUtils::closeQuietly);
	}
	
    public static int transfer(InputStream is, OutputStream os, int bufSize) throws IOException {
        byte[] buf = new byte[bufSize];

        int count = 0;
        for ( int nbytes = is.read(buf); nbytes >= 0; nbytes = is.read(buf) ) {
            os.write(buf, 0, nbytes);
            count += nbytes;
        }

        return count;
    }
    
    public static int transferAndClose(InputStream is, OutputStream os, int bufSize)
    	throws IOException {
		try {
			return transfer(is, os, bufSize);
		}
		finally {
			closeQuietly(is, os);
		}
    }
    
    public static byte[] toBytes(InputStream is, boolean closeStream) throws IOException {
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	try {
	    	transfer(is, baos, 4096);
	    	baos.close();
	    	
	    	return baos.toByteArray();
    	}
    	finally {
    		if ( closeStream ) {
    			closeQuietly(is);
    		}
    	}
    }
    
    public static byte[] toBytes(File file) throws IOException {
    	byte[] bytes = new byte[(int)file.length()];
    	
    	try ( FileInputStream fis = new FileInputStream(file) ) {
			fis.read(bytes);
			
	    	return bytes;
		}
    }
    
    public static String toString(File file) throws IOException {
		return new String(IOUtils.toBytes(file));
    }
    
    public static String toString(InputStream is, Charset charset) throws IOException {
		byte[] bytes = IOUtils.toBytes(new BufferedInputStream(is), true);
		return new String(bytes, charset);
    }
    
    public static String toString(File file, Charset charset) throws IOException {
		return new String(IOUtils.toBytes(file), charset);
    }
    
    public static void toFile(byte[] bytes, File file) throws IOException {
    	try ( FileOutputStream fos = new FileOutputStream(file); 
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		bos.write(bytes);
    	}
    }
    
    public static void toFile(InputStream is, File file) throws IOException {
    	try ( FileOutputStream fos = new FileOutputStream(file);
    		BufferedOutputStream bos = new BufferedOutputStream(fos); ) {
    		transfer(is, bos, 16<<10);
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
				throw new IOException("reached EOF");
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
			if ( nbytes < 0 ) {
				return length - remains;
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
	
	public static String toSerializedString(Serializable obj) throws IOException {
		return stringify(serialize(obj));
	}
	
	public static Object fromSerializedString(String encoded)
		throws IOException, ClassNotFoundException {
		return deserialize(destringify(encoded));
	}
	
	public static byte[] serialize(Serializable obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try ( ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
			oos.writeObject(obj);
		}
		baos.close();
		
		return baos.toByteArray();
	}
	
	public static Object deserialize(byte[] serialized)
		throws IOException, ClassNotFoundException {
		try ( ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
				ObjectInputStream ois = new ObjectInputStream(bais); ) {
			return ois.readObject();
		}
	}
	
	public static String stringify(byte[] bytes) {
		return Base64.getEncoder().encodeToString(bytes);
	}
	
	public static byte[] destringify(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}
	
	public static void serializeOption(Option<? extends Serializable> obj,
										ObjectOutputStream oos) throws IOException {
		if ( obj.isDefined() ) {
			oos.writeBoolean(true);
			oos.writeObject(obj.get());
		}
		else {
			oos.writeBoolean(false);
		}
	}
	
	public static void writeOptionDouble(Option<Double> opt,
										DataOutput out) throws IOException {
		if ( opt.isDefined() ) {
			out.writeBoolean(true);
			out.writeDouble(opt.get());
		}
		else {
			out.writeBoolean(false);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends Serializable>
	Option<T> deserializeOption(ObjectInputStream ois, Class<T> cls) throws ClassNotFoundException, IOException {
		return (ois.readBoolean())
				? Option.some((T)ois.readObject())
				: Option.none();
	}

	public static <T extends Serializable>
	void serializeList(List<T> list, ObjectOutputStream oos) throws IOException {
		oos.writeInt(list.size());
		for ( T item: list ) {
			oos.writeObject(item);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends Serializable>
	List<T> deserializeList(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		int count = ois.readInt();
		List<T> list = Lists.newArrayListWithExpectedSize(count);
		for ( int i =0; i < count; ++i ) {
			list.add((T)ois.readObject());
		}
		
		return list;
	}
	
	public static void readStringCollection(Consumer<String> consumer, DataInput in)
		throws IOException {
		int count = in.readInt();
		for ( int i =0; i < count; ++i ) {
			consumer.accept(in.readUTF());
		}
	}
	
	public static void writeStringCollection(Collection<String> coll,
											DataOutput out) throws IOException {
		out.writeInt(coll.size());
		for ( String item: coll ) {
			out.writeUTF(item);
		}
	}
}