package utils.swing;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import utils.io.IOUtils;



/**
 *
 * @author Kang-Woo Lee
 */
public class ImageUtils {
	private ImageUtils() {
		throw new AssertionError("Should not be invoked: class=" + getClass().getName());
	}

//	public static BufferedImage resize(BufferedImage bimage, int width, int height) {
//		if ( bimage == null ) {
//			throw new IllegalArgumentException("bimage was null: ImageUtils.resize");
//		}
//		if ( bimage.getWidth() == width && bimage.getHeight() == height ) {
//			return bimage;
//		}
//		
//		return Scalr.resize(bimage, Method.QUALITY, Mode.FIT_EXACT, width, height);
//	}
//	
//	public static BufferedImage resizeQuickly(BufferedImage bimage, int width, int height) {
//		if ( bimage == null ) {
//			throw new IllegalArgumentException("bimage was null: ImageUtils.resizeQuickly");
//		}
//		if ( bimage.getWidth() == width && bimage.getHeight() == height ) {
//			return bimage;
//		}
//		
//		return Scalr.resize(bimage, Method.SPEED, Mode.AUTOMATIC, width, height);
//	}
//	
//	public static BufferedImage toBufferedImage(File file) throws IOException {
//		BufferedImage bi = ImageIO.read(file);
//		if ( bi == null ) {
//			throw new RuntimeException("unknown image format");
//		}
//		
//		return bi;
//	}

	/**
	 * 주어진 JPEG 입력 스트림을 읽어 {@link BufferedImage} 객체를 생성한다.
	 *
	 * @param input	읽어들일 JPEG 입력 스트림
	 * @return	BufferedImage 객체.
	 * @throws IOException	input 읽기시 오류가 발생된 경우.
	 */
	public static BufferedImage toBufferedImage(InputStream input) throws IOException {
		BufferedImage bi = ImageIO.read(input);
		if ( bi == null ) {
			throw new RuntimeException("unknown image format");
		}
		
		return bi;
	}

	/**
	 * 주어진 JPEG 바이트 배열을 읽어 {@link BufferedImage} 객체를 생성한다.
	 *
	 * @param bytes	읽어들일 JPEG 바이트 배열
	 * @return	BufferedImage 객체.
	 */
	public static BufferedImage toBufferedImage(byte[] bytes) {
		ByteArrayInputStream input = null;
		try {
			input = new ByteArrayInputStream(bytes);
			return toBufferedImage(input);
		}
		catch ( IOException neverHappens ) {
			throw new RuntimeException("Should not be here: method=" + ImageUtils.class.getName()
										+ "#JPEGToImage(byte[]), cause=" + neverHappens);
		}
		finally {
			IOUtils.closeQuietly(input);
		}
	}

	public static BufferedImage toBufferedImage(Image image) {
		BufferedImage buffered = new BufferedImage(image.getWidth(null), image.getHeight(null),
													BufferedImage.TYPE_INT_RGB);
		Graphics2D g = buffered.createGraphics();
		g.drawImage(image, null, null);
		g.dispose();

		return buffered;
	}

	public static BufferedImage toBufferedImage(Image image, int imageType) {
		BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), imageType);
		
		Graphics2D g = bi.createGraphics();
		g.drawImage(image, null, null);
		g.dispose();

		return bi;
	}
	
	public static byte[] toJpegBytes(BufferedImage bimage, float quality) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			toJpegStream(bimage, quality, baos);
			return baos.toByteArray();
		}
		catch ( IOException e ) {
			throw new RuntimeException("" + e);
		}
	}
	
	public static void toJpegStream(BufferedImage bufferedImage, float quality, OutputStream os)
		throws IOException {
		JPEGImageWriteParam param = new JPEGImageWriteParam(null);
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(quality);
		
		ImageWriter iwriter = getJpegImageWriter();
		ImageOutputStream ios = ImageIO.createImageOutputStream(os);
		iwriter.setOutput(ios);
		iwriter.write(null, new IIOImage(bufferedImage, null, null), param);
		ios.close();
	}
	
	public static void toJpegFile(BufferedImage bimage, float quality, File jpegFile)
		throws FileNotFoundException {
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(jpegFile));
		try {
			toJpegStream(bimage, quality, bos);
		}
		catch ( IOException e ) {
			throw new RuntimeException("" + e);
		}
		finally {
			IOUtils.closeQuietly(bos);
		}
	}
	
	public static BufferedImage deepCopy(BufferedImage bi) {
		ColorModel cm = bi.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	public static byte[] getRasterBytes(BufferedImage bi) {
		return ((DataBufferByte)bi.getRaster().getDataBuffer()).getData();
	}

	private static ImageWriter s_jpegEncoder = null;
	private static ImageWriter getJpegImageWriter() {
		synchronized ( ImageUtils.class ) {
			if ( s_jpegEncoder == null ) {
				Iterator<ImageWriter> it = ImageIO.getImageWritersBySuffix("jpg");
				s_jpegEncoder = it.next();
			}
			
			return s_jpegEncoder;
		}
	}
}
