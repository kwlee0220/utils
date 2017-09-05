package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.ReentrantLock;

import io.vavr.control.Option;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class CancellableDelegate<T> extends CompletableFuture<T> {
	protected final Option<Cancellable> m_canceller;
	protected final CompletableFuture<T> m_delegatee;

	protected enum State { RUNNING, COMPLETED, FAILED, CANCELLED };
	protected final ReentrantLock m_lock = new ReentrantLock();
	protected State m_state = State.RUNNING;
	
	protected CancellableDelegate(Cancellable canceller, CompletableFuture<T> future) {
		this(Option.some(canceller), future);
	}
	
	protected CancellableDelegate(CompletableFuture<T> future) {
		this(Option.none(), future);
	}
	
	protected CancellableDelegate(Option<Cancellable> canceller, CompletableFuture<T> future) {
		m_canceller = canceller;
		m_delegatee = future;
		
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
					if ( e instanceof CompletionException ) {
						super.completeExceptionally(((CompletionException)e).getCause());
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
    		CompletableFuture.runAsync(this::cancelTask);
    		
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
    		CompletableFuture.runAsync(this::cancelTask);
    		
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
    		CompletableFuture.runAsync(this::cancelTask);

	    	return true;
    	}
    	finally { m_lock.unlock(); }
    }
    
    private final void cancelTask() {
    	m_canceller.forEach(Cancellable::cancel);
    }
}