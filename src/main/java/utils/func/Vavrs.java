package utils.func;

import java.util.function.BiFunction;

import io.vavr.control.Option;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class Vavrs {
	private Vavrs() {
		throw new AssertionError("Should not be called: class=" + Vavrs.class);
	}
	
	public static <S,T> S transform(Option<T> opt, S src, BiFunction<S,T,S> func) {
		if ( opt.isDefined() ) {
			return func.apply(src, opt.get());
		}
		else {
			return src;
		}
	}
}
