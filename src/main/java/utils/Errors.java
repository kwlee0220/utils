package utils;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Errors {
	public static Runnable ignoreRunnableException(CheckedRunnable task) {
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable ignored ) { }
		};
	}
	
	public static <T,S> Function<T,S> ignoreFunctionException(CheckedFunction<T,S> func) {
		return (data) -> {
			try {
				return func.apply(data);
			}
			catch ( Throwable e ) {
				throw new RuntimeException(e);
			}
		};
	}
	
	public static <T> Consumer<T> ignoreConsumerException(CheckedConsumer<T> consumer) {
		return (data) -> {
			try {
				consumer.accept(data);
			}
			catch ( Throwable ignored ) { }
		};
	}
	
	public static boolean runQuietly(CheckedRunnable task) {
		if ( task != null ) {
			try {
				task.run();
				return true;
			}
			catch ( Throwable ignored ) {
				return false;
			}
		}
		
		return false;
	}
	
	public static void runRTE(CheckedRunnable task) {
		if ( task != null ) {
			try {
				task.run();
			}
//			catch ( RuntimeException rte ) {
//				throw rte;
//			}
			catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public static Runnable toRunnableRTE(CheckedRunnable task) {
		return () -> {
			try {
				task.run();
			}
			catch ( RuntimeException e ) {
				throw e;
			}
			catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		};
	}
	
	public static Runnable toRunnableIE(CheckedRunnable task) {
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable ignore ) { }
		};
	}
	
	public static Runnable toRunnable(CheckedRunnable task, Consumer<Throwable> errorHandler) {
		return () -> {
			try {
				task.run();
			}
			catch ( Throwable e ) {
				runQuietly(()->errorHandler.accept(e));
			}
		};
	}
	
	public static <T,S> Function<T,S> toFunctionRTE(CheckedFunction<T,S> func) {
		return (data) -> {
			try {
				return func.apply(data);
			}
			catch ( RuntimeException e ) {
				throw e;
			}
			catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		};
	}
	
	public static <T> Consumer<T> toConsumerRTE(CheckedConsumer<T> consumer) {
		return (data) -> {
			try {
				consumer.accept(data);
			}
			catch ( RuntimeException e ) {
				throw e;
			}
			catch ( Exception e ) {
				throw new RuntimeException(e);
			}
		};
	}
	
	public static <T> Consumer<T> toConsumer(CheckedConsumer<T> task,
											BiConsumer<T,Throwable> errorHandler) {
		return (data) -> {
			try {
				task.accept(data);
			}
			catch ( Throwable e ) {
				runQuietly(()->errorHandler.accept(data, e));
			}
		};
	}
	
	public static interface CheckedRunnable {
		public void run() throws Exception;
	}
	
	public static interface CheckedAction0 {
		public void call() throws Exception;
	}
	
	@FunctionalInterface
	public static interface CheckedConsumer<T> {
		public void accept(T data) throws Exception;
	}
	
	public static interface CheckedFunction<T,S> {
		public S apply(T data) throws Exception;
	}
	
	@FunctionalInterface
	public static interface BiConsumer<T1,T2> {
		public void accept(T1 data1, T2 data2);
	}
}
