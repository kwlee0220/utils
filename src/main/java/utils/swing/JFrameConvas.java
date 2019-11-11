package utils.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.Initializable;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class JFrameConvas implements ImageView, Initializable {
	private static final Logger s_logger = LoggerFactory.getLogger("VIEWER");
	private static final String DEFAULT_TITLE = "Image View";
	
	// properties (BEGIN)
	private volatile String m_title;
	private volatile int m_imageType = BufferedImage.TYPE_INT_RGB;
	private volatile Dimension m_size;					// 각 view의 크기 (해상도)
	private volatile boolean m_alwaysOnTop = false;
	private volatile boolean m_frameDecoration = true;	// 윈도우의 타이틀바와  테두리를 보여줄지 여부를 결정
														// ( true: 보임, false: 안보임)
	private volatile int m_monitorIndex =-1;			// optional (default: -1)
	// properties (END)
	
	private volatile JFrame m_frame;
	private volatile ViewPanel m_panel;
	
	@GuardedBy("this") private boolean m_visible = false;
	@GuardedBy("this") private BufferedImageConvas m_convas;
	
	class OnWindowExitHandler extends WindowAdapter {
		@Override
		public void windowClosing(WindowEvent ev) {
			setVisible(false);
		}
	};
	
	public JFrameConvas() { }
	
	public void setTitle(String title) {
		m_title = title;
	}
	
	/**
	 * 이미지 뷰 보드에서  display되는 뷰의 해상도를 설정한다.
	 * 
	 * @param size	이미지 뷰의 해상도.
	 * @throws	IllegalArgumentException	<code>viewSize</code>가 null이거나 유효하지 않은 경우.
	 */
	public void setViewSize(Dimension size) {
		if ( size == null ) {
			throw new IllegalArgumentException("Resolution is null");
		}
		
		synchronized ( this ) {
			m_size = size;
			
			if ( m_frame != null ) {	// 이미 컴포넌트가 초기화가 수행된 경우
				resizeFrame();
			}
		}
	}
	
	public void setImageType(int type) {
		m_imageType = type;
	}
	
	public void setFrameDecoration(boolean flag) {
		m_frameDecoration = flag;
	}
	
	public void setAlwaysOnTop(boolean flag) {
		m_alwaysOnTop = flag;
	}
	
	public void setMonitorIndex(int monitorIndex) {
		m_monitorIndex = monitorIndex;
	}

	@Override
	public void initialize() {
		if ( m_size == null ) {
			// ViewSize가 설정되지 않은 경우는 전체 화면으로 설정한다.
			m_size = SwingUtils.getScreenRectangle(m_monitorIndex).getSize();
		}
		if ( m_title == null ) {
			m_title = DEFAULT_TITLE;
		}
		
		if ( m_monitorIndex < 0 ) {
			m_frame = new JFrame(m_title);
		}
		else {
			GraphicsDevice gd = SwingUtils.getScreen(m_monitorIndex);
			
			m_frame = new JFrame(m_title, gd.getDefaultConfiguration());
		}
		m_frame.setBackground(Color.WHITE);
		m_frame.setAlwaysOnTop(m_alwaysOnTop);
		m_frame.setUndecorated(!m_frameDecoration);
		m_frame.addWindowListener(new OnWindowExitHandler());
		
		resizeFrame();
		m_frame.setVisible(false);
	}
	
	@Override
	public void destroy() {
		m_frame.setVisible(false);
		m_frame.getContentPane().removeAll();
		
		m_frame.dispose();
	}
	
	/**
	 * 이미지 뷰 보드에서 display 되는 뷰의 해상도를 반환한다.
	 * 
	 * @return	이미지 뷰 해상도.
	 */
	@Override
	public synchronized Dimension getSize() {
		return m_size;
	}
	
	public int getMonitorIndex() {
		return m_monitorIndex;
	}

	@Override
	public synchronized boolean getVisible() {
		return m_visible;
	}

	@Override
	public void setVisible(boolean flag) {
		m_visible = flag;
		m_frame.setVisible(flag);
	}

	@Override
	public void drawConvas(Convas convas) {
		m_convas.drawConvas(convas);
	}
	
	public void drawBufferedImage(final BufferedImage bi) {
		m_convas.drawBufferedImage(bi);
	}
	
	public void drawRenderedImage(final RenderedImage bi) {
		m_convas.drawRenderedImage(bi);
	}
	
	public synchronized void drawBufferedImage(BufferedImage bi, Point pt, Dimension size) {
		m_convas.drawBufferedImage(bi, pt, size);
	}

	@Override
	public void drawLine(Point fromPt, Point toPt, Color color, int thickness) {
		m_convas.drawLine(fromPt, toPt, color, thickness);
	}

	@Override
	public void drawRect(Rectangle rect, Color color, int thickness) {
		m_convas.drawRect(rect, color, thickness);
	}

	@Override
	public void drawCircle(Point center, int radius, Color color, int thickness) {
		m_convas.drawCircle(center, radius, color, thickness);
	}

	@Override
	public void drawPolygon(Polygon polygon, Color color, int thickness) {
		m_convas.drawPolygon(polygon, color, thickness);
	}

	@Override
	public void drawString(String str, Point loc, int fontSize, Color color) {
		m_convas.drawString(str, loc, fontSize, color);
	}

	@Override
	public synchronized void clear() {
		m_convas.clear();
	}
	
	public Convas getConvas() {
		return m_convas;
	}

	@Override
	public RenderedImage getRenderedImage() {
		return m_convas.getRenderedImage();
	}

	@Override
	public void updateView() {
		m_panel.repaint();
	}
	
	public JFrame getJFrame() {
		return m_frame;
	}
	
	public GraphicsDevice getGraphicsDevice() {
		return SwingUtils.getScreen(m_monitorIndex);
	}
	
	private void resizeFrame() {
		m_frame.getContentPane().setSize(m_size.width, m_size.height);
		
		m_panel = new ViewPanel(m_size, new GridLayout(1, 1));
		m_panel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));
		m_frame.getContentPane().add(m_panel, BorderLayout.CENTER);
		
		relocateFrame();
		m_frame.pack();
		
		m_convas = new BufferedImageConvas(new BufferedImage(
														m_size.width, m_size.height, m_imageType));
	}
	
	private void relocateFrame() {
		GraphicsConfiguration config = m_frame.getGraphicsConfiguration(); 
		java.awt.Rectangle rect = config.getBounds();
		
		m_frame.setLocation(rect.x + (rect.width-m_size.width)/2,
							rect.y + (rect.height-m_size.height)/2);
	}
	
	class ViewPanel extends JPanel {
		private static final long serialVersionUID = -462483431223342055L;

		ViewPanel(Dimension size, LayoutManager lm) {
			super(lm);
			
			setPreferredSize(size);
		}
		
		@Override public void paint(Graphics g) {
			super.paintComponent(g);

			Graphics2D g2d = (Graphics2D)g;
			synchronized ( this ) {
				g2d.drawImage(m_convas.getBufferedImage(), 0, 0, m_size.width, m_size.height, this);
			}
		}
		
	}
}
