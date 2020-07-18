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
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Serializables {
	private Serializables() {
		throw new AssertionError("Should not be called: class=" + getClass());
	}

	public static String toSerializedString(Serializable obj) throws IOException {
		return IOUtils.stringify(Serializables.serialize(obj));
	}

	public static Object fromSerializedString(String encoded)
		throws IOException, ClassNotFoundException {
		return Serializables.deserialize(IOUtils.destringify(encoded));
	}

	public static byte[] serialize(Serializable obj) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try ( ObjectOutputStream oos = new ObjectOutputStream(baos) ) {
			oos.writeObject(obj);
		}
		baos.close();
		
		return baos.toByteArray();
	}

	public static Object deserialize(byte[] serialized)
		throws IOException, ClassNotFoundException {
		try ( ByteArrayInputStream bais = new ByteArrayInputStream(serialized);
				ObjectInputStream ois = new ObjectInputStream(bais); ) {
			return ois.readObject();
		}
	}

	public static <T extends Serializable>
	void serializeList(List<T> list, ObjectOutputStream oos) throws IOException {
		oos.writeInt(list.size());
		for ( T item: list ) {
			oos.writeObject(item);
		}
	}

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

	public static void readStringCollection(Consumer<String> consumer, DataInput in)
		throws IOException {
		int count = in.readInt();
		for ( int i =0; i < count; ++i ) {
			consumer.accept(in.readUTF());
		}
	}

	public static void writeStringCollection(Collection<String> coll,
											DataOutput out) throws IOException {
		out.writeInt(coll.size());
		for ( String item: coll ) {
			out.writeUTF(item);
		}
	}

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

	@SuppressWarnings("unchecked")
	public static <T extends Serializable>
	FOption<T> deserializeOption(ObjectInputStream ois, Class<T> cls) throws ClassNotFoundException, IOException {
		return (ois.readBoolean())
				? FOption.of((T)ois.readObject())
				: FOption.empty();
	}
}
