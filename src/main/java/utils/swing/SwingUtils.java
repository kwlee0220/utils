package utils.swing;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import javax.swing.JFrame;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class SwingUtils {
	private SwingUtils() {
		throw new AssertionError("Should not be invoked!!: class=" + SwingUtils.class.getName());
	}

	/**
	 * 현 시스템에 설치된 모든 모니터들의 장치 정보들을 반환한다.
	 * 
	 * @return	모니터 장치 정보 배열.
	 */
	public static final GraphicsDevice[] getScreens() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		return ge.getScreenDevices();
	}

	/**
	 * 현 시스템에 설치된 모든 모니터들 중에서 주어진 번호에 해당하는 모니터의 장치 정보들을 반환한다.
	 * <p>
	 * 기본 모니터의 정보를 얻고자하는 경우는 음수 값을 인자로 사용한다.
	 * 
	 * @param index	모니터 번호
	 * @return	모니터 장치 정보.
	 */
	public static final GraphicsDevice getScreen(int index) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		if ( index < 0 ) {
			return ge.getDefaultScreenDevice();
		}
		else {
			GraphicsDevice[] devices = ge.getScreenDevices();
			if ( index >= devices.length ) {
				throw new IllegalArgumentException("invalid screen index: " + index);
			}
			
			return devices[index];
		}
	}

	/**
	 * 주어진 번호에 해당하는 모니터의 해상도를 반환한다.
	 * <p>
	 * 번호가 음수인 경우는 기본 모니터의 해상도를 반환한다.
	 * 
	 * @param index	모니터 번호
	 * @return	모니터 해상도.
	 */
	public static final Rectangle getScreenRectangle(int index) {
		GraphicsDevice screen = getScreen(index);
		
		return screen.getConfigurations()[0].getBounds();
	}

	/**
	 * 주어진 프레임 객체를 주어진 모니터에 출력시킨다.
	 * 
	 * @param screenIndex	모니터 번호
	 * @param frame			출력시킬 {@link JFrame} 객체.
	 */
	public static void showOnScreen(int screenIndex, JFrame frame) {
	    GraphicsDevice[] gd = getScreens();
	    if( screenIndex >= 0 && screenIndex < gd.length ) {
	        frame.setLocation(gd[screenIndex].getDefaultConfiguration().getBounds().x, frame.getY());
	    }
	    else if ( gd.length > 0 ) {
	        frame.setLocation(gd[0].getDefaultConfiguration().getBounds().x, frame.getY());
	    }
	    else {
	        throw new IllegalArgumentException("invalid screen index: " + screenIndex);
	    }
		
	}
}
