package utils.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;

import utils.func.FOption;

/**
 * Java 직렬화({@link ObjectOutputStream}/{@link ObjectInputStream})와
 * {@link DataOutput}/{@link DataInput}을 이용한 직렬화 보조 정적 메소드들을 제공한다.
 * 스트림을 인자로 받는 메소드들은 주어진 스트림을 닫지 않는다.
 * @author Kang-Woo Lee (ETRI)
 */
public class Serializables {
	private Serializables() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}

	/**
	 * 객체를 Java 직렬화한 뒤 Base64 문자열로 변환한다.
	 *
	 * @param obj	직렬화할 객체.
	 * @return	Base64로 인코딩된 직렬화 문자열.
	 * @throws IOException	직렬화 중 오류가 발생한 경우.
	 */
	public static String toSerializedString(Serializable obj) throws IOException {
		return IOUtils.stringify(Serializables.serialize(obj));
	}

	/**
	 * {@link #toSerializedString(Serializable)}로 생성된 Base64 문자열을 역직렬화하여 객체를 복원한다.
	 *
	 * @param encoded	Base64로 인코딩된 직렬화 문자열.
	 * @return	복원된 객체.
	 * @throws IOException	역직렬화 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	직렬화된 객체의 클래스를 찾을 수 없는 경우.
	 * @throws IllegalArgumentException	{@code encoded}가 올바른 Base64 문자열이 아닌 경우.
	 */
	public static Object fromSerializedString(String encoded)
		throws IOException, ClassNotFoundException {
		return Serializables.deserialize(IOUtils.destringify(encoded));
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
		baos.close();
		
		return baos.toByteArray();
	}

	/**
	 * Java 직렬화된 byte 배열로부터 객체를 복원한다.
	 *
	 * @param serialized	직렬화된 byte 배열.
	 * @return	복원된 객체.
	 * @throws IOException	역직렬화 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	직렬화된 객체의 클래스를 찾을 수 없는 경우.
	 */
	public static Object deserialize(byte[] serialized)
		throws IOException, ClassNotFoundException {
		try ( ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
				ObjectInputStream ois = new ObjectInputStream(bais); ) {
			return ois.readObject();
		}
	}

	/**
	 * 리스트를 주어진 객체 출력 스트림에 기록한다.
	 * 리스트 크기({@code int})를 먼저 기록한 뒤 각 원소를 차례로 {@code writeObject}로 기록한다.
	 *
	 * @param <T>	리스트 원소 타입.
	 * @param list	기록할 리스트.
	 * @param oos	객체 출력 스트림.
	 * @throws IOException	기록 중 오류가 발생한 경우.
	 */
	public static <T extends Serializable>
	void serializeList(List<T> list, ObjectOutputStream oos) throws IOException {
		oos.writeInt(list.size());
		for ( T item: list ) {
			oos.writeObject(item);
		}
	}

	/**
	 * {@link #serializeList(List, ObjectOutputStream)}로 기록된 리스트를 읽어 복원한다.
	 * 원소 타입은 검사하지 않고 {@code T}로 캐스팅한다.
	 *
	 * @param <T>	리스트 원소 타입.
	 * @param ois	객체 입력 스트림.
	 * @return	복원된 리스트.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 * @throws ClassNotFoundException	원소 객체의 클래스를 찾을 수 없는 경우.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable>
	List<T> deserializeList(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		int count = ois.readInt();
		List<T> list = Lists.newArrayListWithExpectedSize(count);
		for ( int i =0; i < count; ++i ) {
			list.add((T)ois.readObject());
		}
		
		return list;
	}

	/**
	 * {@link #writeStringCollection(Collection, DataOutput)}로 기록된 문자열들을 읽어
	 * 각 문자열을 차례로 {@code consumer}에 전달한다.
	 *
	 * @param consumer	읽은 문자열을 받을 consumer.
	 * @param in	데이터 입력 스트림.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	public static void readStringCollection(Consumer<String> consumer, DataInput in)
		throws IOException {
		int count = in.readInt();
		for ( int i =0; i < count; ++i ) {
			consumer.accept(in.readUTF());
		}
	}

	/**
	 * 문자열 컬렉션을 기록한다. 원소 수({@code int})를 먼저 기록한 뒤
	 * 각 문자열을 {@link DataOutput#writeUTF(String)}로 기록한다.
	 *
	 * @param coll	기록할 문자열 컬렉션.
	 * @param out	데이터 출력 스트림.
	 * @throws IOException	기록 중 오류가 발생한 경우.
	 */
	public static void writeStringCollection(Collection<String> coll,
											DataOutput out) throws IOException {
		out.writeInt(coll.size());
		for ( String item: coll ) {
			out.writeUTF(item);
		}
	}

	/**
	 * {@link FOption} 값을 기록한다. 값 존재 여부({@code boolean})를 먼저 기록하고,
	 * 값이 있으면 이어서 {@code writeObject}로 값을 기록한다.
	 *
	 * @param obj	기록할 {@link FOption} 값.
	 * @param oos	객체 출력 스트림.
	 * @throws IOException	기록 중 오류가 발생한 경우.
	 */
	public static void serializeOption(FOption<? extends Serializable> obj,
										ObjectOutputStream oos) throws IOException {
		if ( obj.isPresent() ) {
			oos.writeBoolean(true);
			oos.writeObject(obj.get());
		}
		else {
			oos.writeBoolean(false);
		}
	}

	/**
	 * {@code Double} 타입의 {@link FOption} 값을 기록한다. 값 존재 여부({@code boolean})를
	 * 먼저 기록하고, 값이 있으면 이어서 {@link DataOutput#writeDouble(double)}로 값을 기록한다.
	 *
	 * @param opt	기록할 {@link FOption} 값.
	 * @param out	데이터 출력 스트림.
	 * @throws IOException	기록 중 오류가 발생한 경우.
	 */
	public static void writeOptionDouble(FOption<Double> opt,
										DataOutput out) throws IOException {
		if ( opt.isPresent() ) {
			out.writeBoolean(true);
			out.writeDouble(opt.get());
		}
		else {
			out.writeBoolean(false);
		}
	}

	/**
	 * {@link #serializeOption(FOption, ObjectOutputStream)}로 기록된 {@link FOption} 값을 읽어 복원한다.
	 * 값의 타입은 검사하지 않고 {@code T}로 캐스팅한다.
	 *
	 * @param <T>	값의 타입.
	 * @param ois	객체 입력 스트림.
	 * @param cls	값의 클래스. 현재 구현에서는 사용되지 않는다.
	 * @return	복원된 {@link FOption} 값.
	 * @throws ClassNotFoundException	값 객체의 클래스를 찾을 수 없는 경우.
	 * @throws IOException	읽는 중 오류가 발생한 경우.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable>
	FOption<T> deserializeOption(ObjectInputStream ois, Class<T> cls) throws ClassNotFoundException, IOException {
		return (ois.readBoolean())
				? FOption.of((T)ois.readObject())
				: FOption.empty();
	}
}
