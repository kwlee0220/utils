package utils.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.vavr.Lazy;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4SafeDecompressor;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Lz4Compress {
	private static final Lazy<LZ4Factory> s_fact = Lazy.of(LZ4Factory::fastestInstance);
	
	public static Tuple2<byte[], Integer> compress(byte[] bytes) throws IOException {
		LZ4Compressor compressor = s_fact.get().fastCompressor();
		
		byte[] compressed = new byte[compressor.maxCompressedLength(bytes.length)];
		int compressedLength = compressor.compress(bytes, 0, bytes.length,
													compressed, 0, compressed.length);
		
		return Tuple.of(compressed, compressedLength);
	}
	
	public static int decompress(byte[] bytes, int offset, int length, byte[] output) throws IOException {
		LZ4SafeDecompressor decomp = s_fact.get().safeDecompressor();
		return decomp.decompress(bytes, offset, length, output, 0, output.length);
	}
	
	public static LZ4FrameOutputStream toCompressedStream(OutputStream output) throws IOException {
		return new LZ4FrameOutputStream(output);
	}
	
	public static LZ4FrameInputStream toCompressedStream(InputStream in) throws IOException {
		return new LZ4FrameInputStream(in);
	}
}
