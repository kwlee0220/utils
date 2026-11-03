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
 * 이미지 읽기, {@link BufferedImage} 변환, JPEG 인코딩 등 이미지 관련 정적 유틸리티 클래스.
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
	
	/**
	 * 주어진 이미지 파일을 읽어 {@link BufferedImage} 객체를 생성한다.
	 *
	 * @param file	읽어들일 이미지 파일.
	 * @return	BufferedImage 객체.
	 * @throws IOException	파일 읽기시 오류가 발생된 경우.
	 * @throws RuntimeException	지원되지 않는 이미지 형식인 경우.
	 */
	public static BufferedImage toBufferedImage(File file) throws IOException {
		BufferedImage bi = ImageIO.read(file);
		if ( bi == null ) {
			throw new RuntimeException("unknown image format");
		}
		
		return bi;
	}

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

	/**
	 * 주어진 {@link Image}를 {@link BufferedImage#TYPE_INT_RGB} 형식의 {@link BufferedImage}로 변환한다.
	 * 
	 * @param image	변환할 이미지. 크기 정보를 바로 얻을 수 있도록 적재가 완료된 상태여야 한다.
	 * @return	새로 생성된 BufferedImage 객체.
	 */
	public static BufferedImage toBufferedImage(Image image) {
		BufferedImage buffered = new BufferedImage(image.getWidth(null), image.getHeight(null),
													BufferedImage.TYPE_INT_RGB);
		Graphics2D g = buffered.createGraphics();
		g.drawImage(image, null, null);
		g.dispose();

		return buffered;
	}

	/**
	 * 주어진 {@link Image}를 주어진 형식의 {@link BufferedImage}로 변환한다.
	 * 
	 * @param image	변환할 이미지. 크기 정보를 바로 얻을 수 있도록 적재가 완료된 상태여야 한다.
	 * @param imageType	생성할 이미지 형식 (예: {@link BufferedImage#TYPE_INT_RGB}).
	 * @return	새로 생성된 BufferedImage 객체.
	 */
	public static BufferedImage toBufferedImage(Image image, int imageType) {
		BufferedImage bi = new BufferedImage(image.getWidth(null), image.getHeight(null), imageType);
		
		Graphics2D g = bi.createGraphics();
		g.drawImage(image, null, null);
		g.dispose();

		return bi;
	}
	
	/**
	 * 주어진 이미지를 JPEG 형식으로 인코딩한 바이트 배열을 반환한다.
	 * 
	 * @param bimage	인코딩할 이미지.
	 * @param quality	JPEG 압축 품질 (0.0 ~ 1.0).
	 * @return	JPEG 바이트 배열.
	 * @throws RuntimeException	인코딩 중 {@link IOException}이 발생된 경우.
	 */
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
	
	/**
	 * 주어진 이미지를 JPEG 형식으로 인코딩하여 출력 스트림에 쓴다.
	 * <p>
	 * {@code os}는 닫지 않는다.
	 * <b>주의:</b> 모든 호출이 하나의 공유 JPEG {@link ImageWriter}를 동기화 없이 사용하므로
	 * 여러 스레드에서 동시에 호출하면 안전하지 않다.
	 * 
	 * @param bufferedImage	인코딩할 이미지.
	 * @param quality	JPEG 압축 품질 (0.0 ~ 1.0).
	 * @param os	JPEG 데이터를 쓸 출력 스트림.
	 * @throws IOException	인코딩 또는 쓰기 중 오류가 발생된 경우.
	 */
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
	
	/**
	 * 주어진 이미지를 JPEG 형식으로 인코딩하여 파일에 저장한다.
	 * 
	 * @param bimage	인코딩할 이미지.
	 * @param quality	JPEG 압축 품질 (0.0 ~ 1.0).
	 * @param jpegFile	저장할 파일.
	 * @throws FileNotFoundException	파일을 생성하거나 열 수 없는 경우.
	 * @throws RuntimeException	인코딩 또는 쓰기 중 {@link IOException}이 발생된 경우.
	 */
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
	
	/**
	 * 주어진 이미지의 픽셀 데이터를 복사한 새 이미지를 생성한다.
	 * 
	 * @param bi	복사할 이미지.
	 * @return	복사된 이미지.
	 */
	public static BufferedImage deepCopy(BufferedImage bi) {
		ColorModel cm = bi.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	/**
	 * 주어진 이미지의 래스터 데이터 바이트 배열을 반환한다.
	 * 복사본이 아닌 이미지 내부 배열이 그대로 반환된다.
	 * 
	 * @param bi	대상 이미지. 데이터 버퍼가 {@link DataBufferByte}이어야 한다.
	 * @return	래스터 데이터 바이트 배열.
	 * @throws ClassCastException	데이터 버퍼가 {@link DataBufferByte}가 아닌 경우.
	 */
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
