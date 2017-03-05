package utils.swing;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ImageView extends Convas {
	/**
	 * 이미지 버퍼의 내용이 화면에 출력 중인지 여부를 반환한다.
	 * <p>
	 * <code>true</code>인 경우는 화면에 보여지고 있다는 의미이고,
	 * 그렇지 않은 경우는 화면에서 보이지 않고 있다는 의미이다.
	 * 
	 * @return	화면 출력 여부.
	 */
	public boolean getVisible();
	
	/**
	 * 이미지 버퍼의 내용을 출력할지 여부를 설정한다.
	 * <p>
	 * <code>true</code>인 경우는 화면에 출력하게 되고, 그렇지 않은 경우는 화면에서 보이지 않게 된다.
	 * 
	 * @param flag	화면 출력 여부.
	 */
	public void setVisible(boolean flag);
	
	/**
	 * 화면 출력용 버퍼에 그려진 이미지를 화면에 출력한다.
	 */
	public void updateView();
}
