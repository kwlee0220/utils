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

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;

import net.jcip.annotations.GuardedBy;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class BufferedImageConvas implements Convas {
	private static final AffineTransform NULL_TRANS = new AffineTransform();
	
	@Nonnull private final Dimension m_size;
	@Nonnull @GuardedBy("this") private BufferedImage m_bi;
	
	public static BufferedImageConvas createRGBConvas(Dimension size) {
		return new BufferedImageConvas(new BufferedImage(size.width, size.height,
															BufferedImage.TYPE_INT_RGB));
	}
	
	public BufferedImageConvas(BufferedImage bi) {
		Objects.requireNonNull(bi);
		
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
	
	public BufferedImage getBufferedImage() {
		return m_bi;
	}
	
	public synchronized void resize(Dimension newSize) {
//		m_bi = ImageUtils.resize(m_bi, newSize.width, newSize.height);
	}

	@Override
	public void drawConvas(Convas convas) {
		drawRenderedImage(convas.getRenderedImage());
	}
	
	public synchronized void setBufferedImage(final BufferedImage bi) {
		if ( m_bi.getWidth() != bi.getWidth() || m_bi.getHeight() != bi.getHeight() ) {
			throw new IllegalArgumentException("incompatible getHeight: different size");
		}
		
		m_bi = bi;
	}
	
	public synchronized void drawBufferedImage(final BufferedImage bi) {
		Graphics2D g = m_bi.createGraphics();
		g.drawImage(bi, 0, 0, m_size.width, m_size.height, null);
		g.dispose();
	}
	
	public synchronized void drawBufferedImage(final BufferedImage bi, final Point pt,
												final Dimension size) {
		Graphics2D g = m_bi.createGraphics();
		g.drawImage(bi, pt.x, pt.y, size.width, size.height, null);
		g.dispose();
	}
	
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
