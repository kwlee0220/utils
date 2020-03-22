package utils.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import utils.UnitUtils;
import utils.Utilities;
import utils.func.Lazy;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Lz4Compressions {
	private static final Logger s_logger = LoggerFactory.getLogger(Lz4Compressions.class);
	
	private static final int DEFAULT_BLOCK_SIZE = 16 * 1024;
	private static final int HEADER_SIZE = 4 + 4;
	private static final Lazy<LZ4Factory> s_fact = Lazy.of(LZ4Factory::fastestInstance);
	private static final Lazy<LZ4Compressor> s_compressor = Lazy.of(() -> s_fact.get().fastCompressor());
	
	private Lz4Compressions() {
		throw new AssertionError("Should not be called: class=" + Lz4Compressions.class.getName());
	}
	
	public static int maxCompressedLength(int srcLength) {
		return s_compressor.get().maxCompressedLength(srcLength);
	}
	
	public static byte[] compress(byte[] bytes) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
		try ( LZ4BlockOutputStream lz4os = new LZ4BlockOutputStream(baos, bytes.length) ) {
			lz4os.write(bytes);
		}
		baos.close();
		
		return baos.toByteArray();
	}
	
	public static byte[] decompress(byte[] bytes) throws IOException {
		try ( ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				LZ4BlockInputStream lz4is = new LZ4BlockInputStream(bais) ) {
			return IOUtils.toBytes(lz4is);
		}
	}
	
	public static OutputStream toCompressedStream(OutputStream out, int blockSize) {
		return new LZ4BlockOutputStream(out, blockSize);
	}
	
	public static InputStream toDecompressedStream(InputStream in) {
		return new LZ4BlockInputStream(in);
	}
	
	public static Lz4CompressedInputStream compress(InputStream is) {
		return new Lz4CompressedInputStream(is, DEFAULT_BLOCK_SIZE);
	}
	
	public static Lz4CompressedInputStream compress(InputStream is, int blockSize) {
		return new Lz4CompressedInputStream(is, blockSize);
	}
	
	public static Lz4DecompressedInputStream decompress(InputStream is) {
		return new Lz4DecompressedInputStream (is);
	}
	
	private static class Lz4CompressedInputStream extends InputStream {
		private final InputStream m_src;
		private final int m_blockSize;
		
		private final LZ4Compressor m_compressor;
		private final ByteBuffer m_header = ByteBuffer.allocate(HEADER_SIZE);
		private final byte[] m_rawBuffer;
		private final byte[] m_compressed;
		private int m_remains;
		private int m_offset;

		private long m_blockCount =0;
		private long m_total =0;
		private long m_totalCompressed =0;
		
		public Lz4CompressedInputStream(InputStream src, int blockSize) {
			Utilities.checkNotNullArgument(src, "Source InputStream");
			Utilities.checkArgument(blockSize > 0, "invalid block-size: " + blockSize);
			
			m_src = src;
			m_blockSize = blockSize;
			m_compressor = s_fact.get().fastCompressor();
			m_rawBuffer = new byte[m_blockSize];
			m_compressed = new byte[m_compressor.maxCompressedLength(m_blockSize) + HEADER_SIZE];
			m_remains = 0;
		}

		@Override
		public int read() throws IOException {
			if ( m_remains < 0 ) {
				return -1;
			}
			else if ( m_remains == 0 ) {
				if ( compressNextBlock() < 0 ) {
					return -1;
				}
			}

			--m_remains;
			return m_compressed[m_offset++] & 0xFF;
		}

		@Override
	    public int read(byte b[], int off, int len) throws IOException {
			if ( m_remains < 0 ) {
				return -1;
			}
			else if ( m_remains == 0 ) {
				if ( compressNextBlock() < 0 ) {
					return -1;
				}
			}
			
			int nbytes = Math.min(m_remains, len);
			System.arraycopy(m_compressed, m_offset, b, off, nbytes);
			m_offset += nbytes;
			m_remains -= nbytes;
			
			return nbytes;
	    }
		
		@Override
	    public int available() throws IOException {
	        return m_remains;
	    }

		@Override
	    public void close() throws IOException {
			printCompressionRatio();
		}
		
		@Override
		public String toString() {
			return String.format("block[%d]=%d:%d, %d -> %d", m_blockCount,
									m_offset+m_remains, m_offset, m_total, m_totalCompressed);
		}
		
		private int compressNextBlock() throws IOException {
			int nread;
			while ( (nread = IOUtils.readAtBest(m_src, m_rawBuffer)) == 0 ) {
				Thread.yield();
			}
			if ( nread == -1 ) {
				return m_remains = -1;
			}
			m_total += nread;
			
			int ncompresseds = m_compressor.compress(m_rawBuffer, 0, nread, m_compressed,
													HEADER_SIZE, m_compressed.length-HEADER_SIZE);
			m_header.clear();
			m_header.putInt(nread)
					.putInt(ncompresseds)
					.flip();
			m_header.get(m_compressed, 0, HEADER_SIZE);
			m_totalCompressed += ncompresseds;
			
			m_offset = 0;
			return m_remains = HEADER_SIZE + ncompresseds;
		}
		
		private void printCompressionRatio() {
			if ( s_logger.isDebugEnabled() ) {
				String msg = String.format("compressed: total=%s, output=%s, ratio=%.1f%%",
											UnitUtils.toByteSizeString(m_total),
											UnitUtils.toByteSizeString(m_totalCompressed),
											(m_totalCompressed*100.)/m_total);
				s_logger.debug(msg);
			}
		}
	}
	
	private static class Lz4DecompressedInputStream extends InputStream {
		private final InputStream m_src;
		
		private final LZ4FastDecompressor m_decompressor;
		private final byte[] m_header = new byte[HEADER_SIZE];
		private byte[] m_compressedBuffer;
		private byte[] m_buffer;
		private int m_remains;
		private int m_offset;
		
		private long m_blockCount =0;
		private long m_total =0;
		private long m_totalCompressed =0;
		
		private Lz4DecompressedInputStream(InputStream compressedStream) {
			Utilities.checkNotNullArgument(compressedStream, "Lz4Compressed InputStream");
			
			m_src = compressedStream;
			m_decompressor = s_fact.get().fastDecompressor();
			m_compressedBuffer = new byte[0];
			m_buffer = new byte[0];
			m_remains = 0;
			m_offset = 0;
		}

		@Override
		public int read() throws IOException {
			if ( m_remains < 0 ) {
				return -1;
			}
			else if ( m_remains == 0 ) {
				if ( decompressNextBlock() < 0 ) {
					return -1;
				}
			}

			--m_remains;
			return m_buffer[m_offset++] & 0xFF;
		}

		@Override
	    public int read(byte b[], int off, int len) throws IOException {
			if ( m_remains < 0 ) {
				return -1;
			}
			else if ( m_remains == 0 ) {
				if ( decompressNextBlock() < 0 ) {
					return -1;
				}
			}
			
			int nbytes = Math.min(m_remains, len);
			System.arraycopy(m_buffer, m_offset, b, off, nbytes);
			m_offset += nbytes;
			m_remains -= nbytes;
			
			return nbytes;
	    }
		
		@Override
	    public int available() throws IOException {
	        return m_remains;
	    }

		@Override
	    public void close() throws IOException {
			printCompressionRatio();
			m_src.close();
		}
		
		private int decompressNextBlock() throws IOException {
			try {
				IOUtils.readFully(m_src, m_header, 0, HEADER_SIZE);
			}
			catch ( EOFException e ) {
				return -1;
			}
			ByteBuffer headerBuf = ByteBuffer.wrap(m_header);
			int len = headerBuf.getInt();
			int compressedLen = headerBuf.getInt();
			
			if ( m_buffer.length < len ) {
				m_buffer = new byte[len];
				int maxCompressedBlockSize = maxCompressedLength(len) + HEADER_SIZE;
				m_compressedBuffer = new byte[maxCompressedBlockSize];
			}
			
			IOUtils.readFully(m_src, m_compressedBuffer, 0, compressedLen);
			m_decompressor.decompress(m_compressedBuffer, 0, m_buffer, 0, len);
			m_remains = len;
			m_offset = 0;
			
			++m_blockCount;
			m_total += len;
			m_totalCompressed += compressedLen;
			
			return m_remains;
		}
		
		@Override
		public String toString() {
			return String.format("block[%d]=%d:%d, %d -> %d", m_blockCount,
									m_offset+m_remains, m_offset, m_totalCompressed, m_total);
		}
		
		private void printCompressionRatio() {
			if ( s_logger.isInfoEnabled() ) {
				String msg = String.format("decompressed: total=%s, output=%s, ratio=%.1f%%%n",
											UnitUtils.toByteSizeString(m_totalCompressed),
											UnitUtils.toByteSizeString(m_total),
											(m_totalCompressed*100.)/m_total);
				s_logger.info(msg);
			}
		}
	}
}
