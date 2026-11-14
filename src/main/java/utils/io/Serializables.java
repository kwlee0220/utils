package utils.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import utils.Preconditions;

/**
 * Java 직렬화({@link ObjectOutputStream}/{@link ObjectInputStream})로 객체와 byte 배열을 서로 바꾸는
 * 정적 메소드들을 제공한다.
 * <p>
 * <b>신뢰할 수 없는 입력:</b> Java 역직렬화는 입력에 담긴 클래스의 코드를 실행할 수 있다. 외부에서 받은 데이터를
 * 복원할 때는 허용할 클래스를 정한 {@link ObjectInputFilter}를 {@link #deserialize(byte[], ObjectInputFilter)}에
 * 넘긴다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class Serializables {
	private Serializables() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}

	/**
	 * 객체를 Java 직렬화하여 byte 배열로 반환한다.
	 *
	 * @param obj	직렬화할 객체.
	 * @return	직렬화된 byte 배열.
	 * @throws IOException	직렬화 중 오류가 발생한 경우.
	 */
	public static byte[] serialize(Serializable obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try ( ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
			oos.writeObject(obj);
		}
		return baos.toByteArray();
	}

	/**
	 * Java 직렬화된 byte 배열로부터 객체를 복원한다. JVM 전역 필터({@code jdk.serialFilter})만 적용된다.
	 *
	 * @param serialized	직렬화된 byte 배열.
	 * @return	복원된 객체.
	 * @throws IOException	역직렬화 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	직렬화된 객체의 클래스를 찾을 수 없는 경우.
	 */
	public static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
		try ( ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized)) ) {
			return ois.readObject();
		}
	}

	/**
	 * Java 직렬화된 byte 배열로부터 객체를 복원하되, 주어진 필터가 허용하는 클래스만 받아들인다.
	 * <p>
	 * 예: {@code ObjectInputFilter.Config.createFilter("java.util.*;my.pkg.*;!*")}
	 *
	 * @param serialized	직렬화된 byte 배열.
	 * @param filter		역직렬화 필터. {@code null}이면 안 된다.
	 * @return	복원된 객체.
	 * @throws java.io.InvalidClassException	필터가 거부한 클래스가 들어 있는 경우.
	 * @throws IOException	역직렬화 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	직렬화된 객체의 클래스를 찾을 수 없는 경우.
	 */
	public static Object deserialize(byte[] serialized, ObjectInputFilter filter)
		throws IOException, ClassNotFoundException {
		Preconditions.checkNotNullArgument(filter, "filter is null");

		try ( ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized)) ) {
			ois.setObjectInputFilter(filter);
			return ois.readObject();
		}
	}
}
