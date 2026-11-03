package utils.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

import javax.annotation.concurrent.GuardedBy;

import org.jetbrains.annotations.NotNull;

import utils.Preconditions;



/**
 * {@link BufferedImage}에 그림을 그리는 {@link Convas} 구현 클래스.
 * <p>
 * 그리기 연산은 객체 단위로 동기화({@code synchronized})된다. 캔버스 크기는 생성 시 주어진 이미지의
 * 크기로 고정된다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class BufferedImageConvas implements Convas {
	private static final AffineTransform NULL_TRANS = new AffineTransform();
	
	@NotNull private final Dimension m_size;
	@NotNull @GuardedBy("this") private BufferedImage m_bi;
	
	/**
	 * 주어진 크기의 {@link BufferedImage#TYPE_INT_RGB} 이미지를 새로 만들어 이를 사용하는 캔버스를 생성한다.
	 * 
	 * @param size	캔버스 크기.
	 * @return	생성된 캔버스 객체.
	 */
	public static BufferedImageConvas createRGBConvas(Dimension size) {
		return new BufferedImageConvas(new BufferedImage(size.width, size.height,
															BufferedImage.TYPE_INT_RGB));
	}
	
	/**
	 * 주어진 이미지에 그림을 그리는 캔버스를 생성한다.
	 * 이미지는 복사되지 않고 그대로 사용된다.
	 * 
	 * @param bi	캔버스로 사용할 이미지.
	 * @throws IllegalArgumentException	{@code bi}가 {@code null}인 경우.
	 */
	public BufferedImageConvas(BufferedImage bi) {
		Preconditions.checkNotNullArgument(bi, "bi must not be null");
		
		m_size = new Dimension(bi.getWidth(), bi.getHeight());
		m_bi = bi;
	}

	@Override
	public Dimension getSize() {
		return m_size;
	}

	@Override
	public RenderedImage getRenderedImage() {
		return getBufferedImage();
	}
	
	/**
	 * 캔버스가 현재 사용 중인 이미지를 반환한다.
	 * 복사본이 아닌 내부 이미지 객체가 그대로 반환된다.
	 * 
	 * @return	캔버스 이미지.
	 */
	public BufferedImage getBufferedImage() {
		return m_bi;
	}
	
	/**
	 * 캔버스 크기를 변경한다.
	 * <p>
	 * <b>주의:</b> 현재 구현은 아무 작업도 하지 않는다 (구현부가 주석 처리되어 있음).
	 * 
	 * @param newSize	새 캔버스 크기 (사용되지 않음).
	 */
	public synchronized void resize(Dimension newSize) {
//		m_bi = ImageUtils.resize(m_bi, newSize.width, newSize.height);
	}

	@Override
	public void drawConvas(Convas convas) {
		drawRenderedImage(convas.getRenderedImage());
	}
	
	/**
	 * 캔버스가 사용하는 이미지를 주어진 이미지로 교체한다.
	 * 
	 * @param bi	새 이미지. 현재 이미지와 크기가 같아야 한다.
	 * @throws IllegalArgumentException	{@code bi}의 크기가 현재 이미지와 다른 경우.
	 * @throws NullPointerException	{@code bi}가 {@code null}인 경우.
	 */
	public synchronized void setBufferedImage(final BufferedImage bi) {
		if ( m_bi.getWidth() != bi.getWidth() || m_bi.getHeight() != bi.getHeight() ) {
			throw new IllegalArgumentException("incompatible getHeight: different size");
		}
		
		m_bi = bi;
	}
	
	/**
	 * 주어진 이미지를 캔버스 전체 크기에 맞게 늘이거나 줄여 그린다.
	 * 
	 * @param bi	그릴 이미지.
	 */
	public synchronized void drawBufferedImage(final BufferedImage bi) {
		Graphics2D g = m_bi.createGraphics();
		g.drawImage(bi, 0, 0, m_size.width, m_size.height, null);
		g.dispose();
	}
	
	/**
	 * 주어진 이미지를 주어진 위치에 주어진 크기로 늘이거나 줄여 그린다.
	 * 
	 * @param bi	그릴 이미지.
	 * @param pt	그려질 영역의 좌상단 좌표.
	 * @param size	그려질 영역의 크기.
	 */
	public synchronized void drawBufferedImage(final BufferedImage bi, final Point pt,
												final Dimension size) {
		Graphics2D g = m_bi.createGraphics();
		g.drawImage(bi, pt.x, pt.y, size.width, size.height, null);
		g.dispose();
	}
	
	/**
	 * 주어진 이미지를 크기 변환 없이 캔버스의 원점(좌상단)에 그린다.
	 * 
	 * @param bi	그릴 이미지.
	 */
	public synchronized void drawRenderedImage(final RenderedImage bi) {
		Graphics2D g = m_bi.createGraphics();
		g.drawRenderedImage(bi, NULL_TRANS);
		g.dispose();
	}

	@Override
	public synchronized void drawLine(Point fromPt, Point toPt, Color color, int thickness) {
		Graphics2D g = m_bi.createGraphics();
		g.setColor(color);
		g.setStroke(new BasicStroke(thickness));
		g.drawLine(fromPt.x, fromPt.y, toPt.x, toPt.y);
		g.dispose();
	}

	@Override
	public synchronized void drawRect(Rectangle rect, Color color, int thickness) {
		Graphics2D g = m_bi.createGraphics();
		g.setColor(color);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		if ( thickness < 0 ) {
			g.fillRect(rect.x, rect.y, rect.width, rect.height);
		}
		else if ( thickness > 0 ) {
			g.setStroke(new BasicStroke(thickness));
			g.drawRect(rect.x, rect.y, rect.width, rect.height);
		}
		g.dispose();
	}

	@Override
	public synchronized void drawCircle(Point center, int radius, Color color, int thickness) {
		Graphics2D g = m_bi.createGraphics();
		g.setColor(color);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		Shape circle = new Ellipse2D.Double(center.x - radius, center.y - radius,
											2.0 * radius, 2.0 * radius);
		
		if ( thickness < 0 ) {
			g.fill(circle);
		}
		else if ( thickness > 0 ) {
			g.setStroke(new BasicStroke(thickness));
			g.draw(circle);
		}
		g.dispose();
	}

	@Override
	public void drawPolygon(Polygon polygon, Color color, int thickness) {
		// Polygon에서 java.awt.Polygon으로 변환하는 과정이 시간이 소요될 수 있기 때문에
		// synchronized 블럭으로 진입하기 전에 수행한다.
		if ( polygon.npoints == 0 ) {
			return;
		}
		
		synchronized ( this ) {
			Graphics2D g = m_bi.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(color);
			g.setStroke(new BasicStroke(thickness));
			g.drawPolygon(polygon);
			g.dispose();
		}
	}

	@Override
	public synchronized void drawString(String str, Point loc, int fontSize, Color color) {
		Graphics2D g = m_bi.createGraphics();
		g.setColor(color);
		g.setFont(new Font("Ariel", Font.BOLD, fontSize));
		g.drawString(str, loc.x, loc.y);
		g.dispose();
	}

	@Override
	public void clear() {
		drawRect(new Rectangle(new Point(0,0), m_size), Color.BLACK, -1);
	}
}
