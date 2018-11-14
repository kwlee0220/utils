package utils.fostore;



/**
 * <code>FileObjectVisitor</code> 인터페이스는 container 클래스에 속한 각 원소를 방문하는
 * 방문자 객체를 정의한다.
 * 각 원소의 실제 클래스 타입은 container 클래스에 따라 달라진다.
 *
 * @author Kang-Woo Lee
 * @version 1.0
 */
public interface FileObjectVisitor<K> {
	/**
	 * <code>visitee</code> 객체를 방문한다.
	 *
	 * @param key       방문할 객체 (non-null)
	 */
	void visit(K key);
}