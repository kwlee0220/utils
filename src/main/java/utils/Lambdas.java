package utils;

import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Lambdas {
	private Lambdas() {
		throw new AssertionError("Should not be called: " + Lambdas.class);
	}
	
	public static void guraded(Lock lock, Runnable work) {
		lock.lock();
		try {
			work.run();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> T guraded(Lock lock, Supplier<T> suppl) {
		lock.lock();
		try {
			return suppl.get();
		}
		finally {
			lock.unlock();
		}
	}
	
	public static <T> void guraded(Lock lock, Consumer<T> consumer, T data) {
		lock.lock();
		try {
			consumer.accept(data);
		}
		finally {
			lock.unlock();
		}
	}
}
