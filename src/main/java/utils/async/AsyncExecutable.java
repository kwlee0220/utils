package utils.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import utils.Throwables;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface AsyncExecutable<T> {
	public Execution<T> execute() throws Exception;
	public Execution<T> execute(Executor executor) throws Exception;
	
	public default T executeSynchronously() throws InterruptedException,
											ExecutionException, CancellationException {
		try {
			return execute().get();
		}
		catch ( InterruptedException | ExecutionException | CancellationException e ) {
			throw e;
		}
		catch ( Exception e ) {
			throw new ExecutionException(Throwables.unwrapThrowable(e));
		}
	}
}
