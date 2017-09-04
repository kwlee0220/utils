package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import utils.func.Result;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Asyncs {
	public static CompletableFuture<Void> runAsync(Runnable task) {
		return new Delegate<>(task, CompletableFuture.runAsync(task));
	}
	public static CompletableFuture<Void> runAsync(Runnable task, Executor exector) {
		return new Delegate<>(task, CompletableFuture.runAsync(task, exector));
	}
	
	public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task) {
		return new Delegate<>(task, CompletableFuture.supplyAsync(task));
	}
	public static <T> CompletableFuture<T> supplyAsync(Supplier<T> task, Executor exector) {
		return new Delegate<>(task, CompletableFuture.supplyAsync(task, exector));
	}
	
	public static <T> Result<T> getResult(CompletableFuture<T> future) {
		try {
			return Result.some(future.get());
		}
		catch ( CancellationException e ) {
			return Result.none();
		}
		catch ( ExecutionException e ) {
			return Result.failure(e.getCause());
		}
		catch ( Exception e ) {
			return Result.failure(e);
		}
	}
	
	public static <T> Result<T> getResult(CompletableFuture<T> future, long timeout, TimeUnit tu) {
		try {
			return Result.some(future.get(timeout, tu));
		}
		catch ( CancellationException e ) {
			return Result.none();
		}
		catch ( ExecutionException e ) {
			return Result.failure(e.getCause());
		}
		catch ( Exception e ) {
			return Result.failure(e);
		}
	}
	
	private static class Delegate<T> extends CompletableFuture<T> {
		private final Object m_task;

		private enum State { RUNNING, COMPLETED, FAILED, CANCELLED };
		private final ReentrantLock m_lock = new ReentrantLock();
		private State m_state = State.RUNNING;
		
		Delegate(Object task, CompletableFuture<T> future) {
			m_task = task;
			
			future.whenComplete((v,e) -> {
		    	m_lock.lock();
		    	try {
		    		switch ( m_state ) {
		    			case COMPLETED:
		    			case CANCELLED:
		    			case FAILED:
		    				return;
		    			case RUNNING:
		    				break;
		    		}
		    		
					if ( e != null ) {
						if ( e instanceof CancellationException ) {
							super.cancel(true);
						}
						else {
							super.completeExceptionally(e);
						}
					}
					else {
						super.complete(v);
					}
		    	}
		    	finally { m_lock.unlock(); }
			});
		}
		
	    public boolean complete(T value) {
	    	m_lock.lock();
	    	try {
		    	if ( !super.complete(value) ) {
		    		return false;
		    	}
		    	
		    	m_state = State.COMPLETED;
		    	if ( m_task instanceof Cancellable ) {
		    		CompletableFuture.runAsync(() -> ((Cancellable)m_task).cancel());
		    	}
		    	return true;
	    	}
	    	finally { m_lock.unlock(); }
	    }
	    
	    public boolean completeExceptionally(Throwable cause) {
	    	m_lock.lock();
	    	try {
		    	if ( !super.completeExceptionally(cause) ) {
		    		return false;
		    	}
		    	
		    	m_state = State.FAILED;
		    	if ( m_task instanceof Cancellable ) {
		    		CompletableFuture.runAsync(() -> ((Cancellable)m_task).cancel());
		    	}
		    	return true;
	    	}
	    	finally { m_lock.unlock(); }
	    }
		
		@Override
	    public boolean cancel(boolean mayInterruptIfRunning) {
	    	m_lock.lock();
	    	try {
		    	if ( !super.cancel(mayInterruptIfRunning) ) {
		    		return false;
		    	}
		    	
		    	m_state = State.CANCELLED;
		    	if ( m_task instanceof Cancellable ) {
		    		CompletableFuture.supplyAsync(() -> ((Cancellable)m_task).cancel());
		    	}
	
		    	return true;
	    	}
	    	finally { m_lock.unlock(); }
	    }
	}
}
