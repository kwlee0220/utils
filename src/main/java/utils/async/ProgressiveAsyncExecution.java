package utils.async;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface ProgressiveAsyncExecution<T,P> extends AsyncExecution<T>, ProgressReporter<P> {

}
