package utils.func;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.collect.Lists;

import utils.Tuple;


/**
 * 불변(immutable) 단일 연결 리스트(cons list).
 * <p>
 * 빈 리스트({@link #NIL})와 head/tail로 구성된 cons 셀로 이루어지며, 모든 연산은 기존 리스트를
 * 변경하지 않고 새 리스트를 반환한다. 대부분의 재귀 연산은 {@link TailCall}을 이용하여 스택을
 * 소모하지 않도록 구현되어 있다.
 *
 * @param <T>	원소 타입.
 * @author Kang-Woo Lee (ETRI)
 */
public abstract class FuncList<T> {
	/**
	 * 빈 리스트를 나타내는 공유 객체.
	 * <p>
	 * raw 타입으로 선언되어 있으므로 타입이 지정된 빈 리스트가 필요한 경우에는 {@link #list()}를 사용한다.
	 */
	@SuppressWarnings("rawtypes")
	public static final FuncList NIL = new Nil();
	
	/**
	 * 리스트의 첫번째 원소를 반환한다.
	 *
	 * @return	첫번째 원소.
	 * @throws IllegalStateException	빈 리스트인 경우.
	 */
	public abstract T head();

	/**
	 * 첫번째 원소를 제외한 나머지 리스트를 반환한다.
	 *
	 * @return	나머지 리스트.
	 * @throws IllegalStateException	빈 리스트인 경우.
	 */
	public abstract FuncList<T> tail();

	/**
	 * 빈 리스트인지 여부를 반환한다.
	 *
	 * @return	빈 리스트인 경우 {@code true}, 그렇지 않으면 {@code false}.
	 */
	public abstract boolean isEmpty();

	/**
	 * 리스트의 원소 수를 반환한다.
	 * <p>
	 * 길이는 각 cons 셀 생성 시 미리 계산되어 있으므로 상수 시간에 반환된다.
	 *
	 * @return	원소 수.
	 */
	public abstract int length();

	/**
	 * 앞에서부터 {@code n}개의 원소를 제외한 나머지 리스트를 반환한다.
	 * <p>
	 * {@code n}이 0 이하이면 본 리스트를, 리스트 길이 이상이면 빈 리스트를 반환한다.
	 *
	 * @param n	제외할 원소 수.
	 * @return	남은 리스트.
	 */
	public abstract FuncList<T> drop(int n);

	/**
	 * 앞에서부터 조건을 만족하는 원소들을 제외하고, 처음으로 조건을 만족하지 않는 원소부터의 리스트를 반환한다.
	 *
	 * @param cond	제외 조건.
	 * @return	남은 리스트.
	 */
	public abstract FuncList<T> dropWhile(Function<T, Boolean> cond);

	/**
	 * 원소 순서를 뒤집은 새 리스트를 반환한다.
	 *
	 * @return	역순 리스트.
	 */
	public abstract FuncList<T> reverse();

	/**
	 * 첫번째 원소부터 차례대로 {@code fold} 함수를 적용하여 하나의 값으로 누적한다.
	 *
	 * @param <U>	누적 값 타입.
	 * @param identity	누적 초기 값.
	 * @param fold	(누적 값, 원소)로 새 누적 값을 계산하는 함수.
	 * @return	최종 누적 값. 빈 리스트인 경우 {@code identity}.
	 */
	public abstract <U> U foldLeft(U identity, BiFunction<U,T,U> fold);

	/**
	 * 첫번째 원소부터 차례대로 {@code fold} 함수를 적용하되, 누적 값이 {@code stopper}와 같아지면 중단한다.
	 * <p>
	 * 각 원소를 적용하기 전에 누적 값이 {@code stopper}와 {@code equals}인지 검사하므로,
	 * 누적 값이 {@code null}이 되면 {@link NullPointerException}이 발생한다.
	 * <b>주의:</b> 이 연산은 {@link TailCall}이 아닌 일반 재귀로 구현되어 있어 매우 긴 리스트에서는
	 * {@link StackOverflowError}가 발생할 수 있다.
	 *
	 * @param <U>	누적 값 타입.
	 * @param identity	누적 초기 값.
	 * @param stopper	누적을 중단시키는 값.
	 * @param fold	(누적 값, 원소)로 새 누적 값을 계산하는 함수.
	 * @return	(최종 누적 값, 아직 처리되지 않은 나머지 리스트) 쌍.
	 */
	public abstract <U> Tuple<U,FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U,T,U> fold);
	
	private FuncList() { }
	
	/**
	 * 빈 리스트를 반환한다.
	 *
	 * @param <T>	원소 타입.
	 * @return	빈 리스트({@link #NIL}).
	 */
	@SuppressWarnings("unchecked")
	public static <T> FuncList<T> list() {
		return NIL;
	}
	
	/**
	 * 주어진 원소를 맨 앞에 추가한 새 리스트를 반환한다. 본 리스트는 변경되지 않는다.
	 *
	 * @param head	추가할 원소.
	 * @return	새 리스트.
	 */
	public FuncList<T> cons(T head) {
		return new Cons<>(head, this);
	}
	
	/**
	 * 커리(curried) 형태의 함수를 사용하는 {@link #foldLeft(Object, BiFunction)}.
	 *
	 * @param <U>	누적 값 타입.
	 * @param identity	누적 초기 값.
	 * @param fold	누적 값을 받아 원소에 적용할 함수를 반환하는 함수.
	 * @return	최종 누적 값.
	 */
	public <U> U foldLeft(U identity, Function<U, Function<T, U>> fold) {
		return foldLeft(identity, (a,t) -> fold.apply(a).apply(t));
	}

	/**
	 * 마지막 원소부터 역순으로 {@code fold} 함수를 적용하여 하나의 값으로 누적한다.
	 * <p>
	 * 리스트를 뒤집은 뒤 {@link #foldLeft(Object, BiFunction)}로 수행하므로 스택을 소모하지 않는다.
	 *
	 * @param <U>	누적 값 타입.
	 * @param identity	누적 초기 값.
	 * @param fold	(원소, 누적 값)으로 새 누적 값을 계산하는 함수.
	 * @return	최종 누적 값. 빈 리스트인 경우 {@code identity}.
	 */
	public <U> U foldRight(U identity, BiFunction<T, U, U> fold) {
		return reverse().foldLeft(identity, (a,t)->fold.apply(t, a));
	}

	/**
	 * 커리(curried) 형태의 함수를 사용하는 {@link #foldRight(Object, BiFunction)}.
	 *
	 * @param <U>	누적 값 타입.
	 * @param identity	누적 초기 값.
	 * @param fold	원소를 받아 누적 값에 적용할 함수를 반환하는 함수.
	 * @return	최종 누적 값.
	 */
	public <U> U foldRight(U identity, Function<T, Function<U, U>> fold) {
		return foldRight(identity, (t,u) -> fold.apply(t).apply(u));
	}

	/**
	 * 리스트의 원소들을 {@code fold} 함수로 하나의 값으로 축약한다.
	 * <p>
	 * <b>주의:</b> 현재 구현은 첫번째 원소를 초기 값으로 하여 첫번째 원소를 포함한 <em>전체</em> 리스트에
	 * {@link #foldLeft(Object, BiFunction)}를 적용하므로, 첫번째 원소가 두 번 반영된다.
	 * max/min처럼 멱등(idempotent)한 연산에서는 문제가 없으나 합계 등에서는 결과가 달라진다.
	 * 또한 축약 결과가 {@code null}이면 {@link NullPointerException}이 발생한다.
	 *
	 * @param fold	두 값을 하나로 합치는 함수.
	 * @return	축약 결과. 빈 리스트인 경우 {@link Optional#empty()}.
	 */
	public Optional<T> reduce(BiFunction<T, T, T> fold) {
		return isEmpty()
				? Optional.empty()
				: Optional.of(foldLeft(head(), fold));
	}
	
	/**
	 * 초기 상태로부터 원소를 차례로 생성하여 리스트를 만든다.
	 * <p>
	 * {@code gen}이 성공 결과 (원소, 다음 상태)를 반환하는 동안 원소를 추가하고, {@code gen}이
	 * 성공이 아닌 결과(none 또는 failure)를 반환하면 생성을 종료한다. failure에 포함된 예외는 무시된다.
	 *
	 * @param <T>	원소 타입.
	 * @param <S>	상태 타입.
	 * @param start	초기 상태.
	 * @param gen	현재 상태로부터 (원소, 다음 상태)를 생성하는 함수.
	 * @return	생성된 리스트.
	 */
	public static <T,S> FuncList<T> unfold(S start, Function<S,Result<Tuple<T,S>>> gen) {
		return unfold_(list(), start, gen).evaluate().reverse();
	}
	private static <T,S> TailCall<FuncList<T>> unfold_(FuncList<T> accum, S cursor,
														Function<S,Result<Tuple<T,S>>> gen) {
		return gen.apply(cursor)
					.map(t -> TailCall.suspend(() -> unfold_(accum.cons(t._1), t._2, gen)))
					.getOrElse(TailCall.returns(accum));
	}

	/**
	 * 조건을 만족하는 원소들만으로 구성된 새 리스트를 반환한다. 원소 순서는 유지된다.
	 *
	 * @param pred	선택 조건.
	 * @return	조건을 만족하는 원소들의 리스트.
	 */
	public FuncList<T> filter(Function<T, Boolean> pred) {
		return foldRight(list(), (t,a) -> pred.apply(t) ? new Cons<>(t, a) : a);
	}

	/**
	 * 각 원소에 {@code mapper}를 적용한 결과로 구성된 새 리스트를 반환한다.
	 *
	 * @param <U>	변환된 원소 타입.
	 * @param mapper	원소 변환 함수.
	 * @return	변환된 리스트.
	 */
	public <U> FuncList<U> map(Function<T, U> mapper) {
		return foldRight(list(), (t,a) -> new Cons<>(mapper.apply(t), a));
	}

	/**
	 * 각 원소에 {@code mapper}를 적용하여 얻은 리스트들을 순서대로 이어 붙인 새 리스트를 반환한다.
	 *
	 * @param <U>	결과 원소 타입.
	 * @param mapper	원소를 리스트로 변환하는 함수.
	 * @return	이어 붙인 리스트.
	 */
	public <U> FuncList<U> flatMap(Function<T, FuncList<U>> mapper) {
		return foldRight(list(), (t,a) -> concat(mapper.apply(t), a));
	}
	
	/**
	 * 조건을 만족하는 원소가 하나라도 있는지 여부를 반환한다.
	 * <p>
	 * 조건을 만족하는 원소를 찾으면 나머지 원소는 검사하지 않는다.
	 *
	 * @param cond	검사 조건.
	 * @return	조건을 만족하는 원소가 있으면 {@code true}. 빈 리스트인 경우 {@code false}.
	 */
	public boolean exists(Function<T,Boolean> cond) {
		return foldLeft(false, true, (b,t) -> cond.apply(t))._1;
	}
	
	/**
	 * 모든 원소가 조건을 만족하는지 여부를 반환한다.
	 * <p>
	 * 조건을 만족하지 않는 원소를 찾으면 나머지 원소는 검사하지 않는다.
	 *
	 * @param cond	검사 조건.
	 * @return	모든 원소가 조건을 만족하면 {@code true}. 빈 리스트인 경우 {@code true}.
	 */
	public boolean forAll(Function<T,Boolean> cond) {
		return foldLeft(true, false, (b,t) -> cond.apply(t))._1;
	}
	
	/**
	 * 주어진 원소들을 같은 순서로 담은 리스트를 생성한다.
	 *
	 * @param <T>	원소 타입.
	 * @param elms	원소 배열.
	 * @return	생성된 리스트.
	 */
	@SafeVarargs
	public static <T> FuncList<T> list(T... elms) {
		FuncList<T> cons = list();
		for ( int i = elms.length-1; i >= 0; --i ) {
			cons = new Cons<>(elms[i], cons);
		}
		
		return cons;
	}
	
	/**
	 * 주어진 {@link List}의 원소들을 같은 순서로 담은 리스트를 생성한다.
	 * <p>
	 * 원소들은 생성 시점에 복사되므로 이후 {@code elms}의 변경은 반영되지 않는다.
	 *
	 * @param <T>	원소 타입.
	 * @param elms	원소 리스트.
	 * @return	생성된 리스트.
	 */
	public static <T> FuncList<T> list(List<T> elms) {
		FuncList<T> cons = list();
		for ( int i = elms.size()-1; i >= 0; --i ) {
			cons = new Cons<>(elms.get(i), cons);
		}
		
		return cons;
	}
	
	/**
	 * 두 리스트를 순서대로 이어 붙인 새 리스트를 반환한다.
	 * <p>
	 * {@code list1}의 원소들만 복사되고 {@code list2}는 결과 리스트의 꼬리로 공유된다.
	 *
	 * @param <T>	원소 타입.
	 * @param list1	앞쪽 리스트.
	 * @param list2	뒤쪽 리스트.
	 * @return	이어 붙인 리스트.
	 */
	public static <T> FuncList<T> concat(FuncList<T> list1, FuncList<T> list2) {
		return list1.foldRight(list2, (t,accum)->new Cons<>(t, accum));
	}
	
	/**
	 * 리스트들의 리스트를 순서대로 이어 붙여 하나의 리스트로 만든다.
	 *
	 * @param <T>	원소 타입.
	 * @param list	리스트들의 리스트.
	 * @return	평탄화된 리스트.
	 */
	public static <T> FuncList<T> flatten(FuncList<FuncList<T>> list) {
		return list.foldLeft(list(), (a,l) -> concat(a, l));
	}

	/**
	 * 커리(curried) 형태의 함수를 사용하는 {@link #zipWith(FuncList, FuncList, BiFunction)}.
	 *
	 * @param <U>	첫번째 리스트의 원소 타입.
	 * @param <V>	두번째 리스트의 원소 타입.
	 * @param <T>	결과 원소 타입.
	 * @param list1	첫번째 리스트.
	 * @param list2	두번째 리스트.
	 * @param zip	두 원소를 결합하는 커리 함수.
	 * @return	결합된 리스트.
	 */
	public static <U,V,T> FuncList<T> zipWith(FuncList<U> list1, FuncList<V> list2,
												Function<U, Function<V,T>> zip) {
		return zipWith(list1, list2, (u,v) -> zip.apply(u).apply(v));
	}
	
	/**
	 * 두 리스트의 같은 위치 원소들을 {@code zip} 함수로 결합한 리스트를 반환한다.
	 * <p>
	 * 결과 리스트의 길이는 두 리스트 중 짧은 쪽의 길이와 같다.
	 *
	 * @param <U>	첫번째 리스트의 원소 타입.
	 * @param <V>	두번째 리스트의 원소 타입.
	 * @param <T>	결과 원소 타입.
	 * @param list1	첫번째 리스트.
	 * @param list2	두번째 리스트.
	 * @param zip	두 원소를 결합하는 함수.
	 * @return	결합된 리스트.
	 */
	public static <U,V,T> FuncList<T> zipWith(FuncList<U> list1, FuncList<V> list2,
												BiFunction<U,V,T> zip) {
		return zipWith_(list(), list1, list2, zip).evaluate().reverse();
	}
	private static <U,V,T> TailCall<FuncList<T>> zipWith_(FuncList<T> accum,
															FuncList<U> list1,
															FuncList<V> list2,
															BiFunction<U,V,T> zip) {
		return list1.isEmpty() || list2.isEmpty()
			? TailCall.returns(accum)
			: TailCall.suspend(() -> zipWith_(accum.cons(zip.apply(list1.head(), list2.head())),
												list1.tail(), list2.tail(), zip));
	}
	
	/**
	 * 쌍들의 리스트를 첫번째 값들의 리스트와 두번째 값들의 리스트로 분리한다.
	 *
	 * @param <U>	쌍의 첫번째 값 타입.
	 * @param <V>	쌍의 두번째 값 타입.
	 * @param list	쌍들의 리스트.
	 * @return	(첫번째 값 리스트, 두번째 값 리스트) 쌍.
	 */
	public static <U,V> Tuple<FuncList<U>,FuncList<V>> unzip(FuncList<Tuple<U,V>> list) {
		return list.foldRight(Tuple.of(list(), list()),
							(t,a) -> Tuple.of(a._1.cons(t._1), a._2.cons(t._2)));
	}

	/**
	 * 각 원소를 {@code tear} 함수로 쌍으로 변환한 뒤 두 리스트로 분리한다.
	 *
	 * @param <U>	쌍의 첫번째 값 타입.
	 * @param <V>	쌍의 두번째 값 타입.
	 * @param tear	원소를 쌍으로 변환하는 함수.
	 * @return	(첫번째 값 리스트, 두번째 값 리스트) 쌍.
	 */
	public <U,V> Tuple<FuncList<U>,FuncList<V>> unzip(Function<T,Tuple<U,V>> tear) {
		return foldRight(Tuple.of(list(), list()),
						(t,a) -> {
							Tuple<U,V> p = tear.apply(t);
							return Tuple.of(a._1.cons(p._1), a._2.cons(p._2));
						});
	}
	
	/**
	 * 리스트에서 가장 큰 원소를 반환한다. 최대 원소가 여러 개인 경우 가장 앞의 원소가 선택된다.
	 *
	 * @param <C>	원소 타입.
	 * @param list	대상 리스트.
	 * @return	최대 원소. 빈 리스트인 경우 {@link Optional#empty()}.
	 */
	public static <C extends Comparable<C>> Optional<C> max(FuncList<C> list) {
		return list.reduce((m,t) -> (m.compareTo(t) >= 0) ? m : t);
	}
	
	/**
	 * 리스트에서 가장 작은 원소를 반환한다. 최소 원소가 여러 개인 경우 가장 앞의 원소가 선택된다.
	 *
	 * @param <C>	원소 타입.
	 * @param list	대상 리스트.
	 * @return	최소 원소. 빈 리스트인 경우 {@link Optional#empty()}.
	 */
	public static <C extends Comparable<C>> Optional<C> min(FuncList<C> list) {
		return list.reduce((m,t) -> (m.compareTo(t) <= 0) ? m : t);
	}
	
	/**
	 * 주어진 위치의 원소를 반환한다.
	 *
	 * @param index	원소 위치 (0부터 시작).
	 * @return	해당 원소를 담은 성공 결과. {@code index}가 범위를 벗어나면
	 * 			{@link IndexOutOfBoundsException}을 담은 실패 결과.
	 */
	public Result<T> get(int index) {
		return index < 0 || index >= length()
				? Result.failure(new IndexOutOfBoundsException(""+index))
				: get_(this, index).evaluate();
	}
	private static <T> TailCall<Result<T>> get_(FuncList<T> list, int index) {
		return index == 0
				? TailCall.returns(Result.success(list.head()))
				: TailCall.suspend(() -> get_(list.tail(), index-1));
	}
	
	/**
	 * 리스트를 주어진 위치에서 두 리스트로 나눈다.
	 * <p>
	 * 첫번째 리스트는 앞쪽 {@code index}개의 원소, 두번째 리스트는 나머지 원소들로 구성된다.
	 * <b>주의:</b> {@code index}가 {@code length()}와 같은 경우도 실패로 처리되므로, 빈 리스트에 대해서는
	 * 항상 실패 결과가 반환된다.
	 *
	 * @param index	분할 위치. 0 이상 {@code length()} 미만이어야 한다.
	 * @return	(앞쪽 리스트, 뒤쪽 리스트) 쌍을 담은 성공 결과. {@code index}가 범위를 벗어나면
	 * 			{@link IndexOutOfBoundsException}을 담은 실패 결과.
	 */
	public Result<Tuple<FuncList<T>, FuncList<T>>> splitAt(int index) {
		return index < 0 || index >= length()
				? Result.failure(new IndexOutOfBoundsException(""+index))
				: splitAt_(list(), reverse(), length() - index).evaluate();
	}
	private static <T> TailCall<Result<Tuple<FuncList<T>, FuncList<T>>>>
							splitAt_(FuncList<T> accum, FuncList<T> list, int index) {
		return index == 0 || list.isEmpty()
				? TailCall.returns(Result.success(Tuple.of(list.reverse(), accum)))
				: TailCall.suspend(() -> splitAt_(accum.cons(list.head()), list.tail(),
													index-1));
	}
	
	/**
	 * 본 리스트가 주어진 리스트로 시작하는지 여부를 반환한다.
	 * <p>
	 * 원소 비교는 본 리스트 원소의 {@code equals}로 수행하므로 본 리스트에 {@code null} 원소가 있으면
	 * {@link NullPointerException}이 발생할 수 있다.
	 *
	 * @param sub	비교할 리스트.
	 * @return	본 리스트가 {@code sub}로 시작하면 {@code true}. {@code sub}가 빈 리스트이면 항상 {@code true}.
	 */
	public boolean startsWith(FuncList<T> sub) {
		return startsWith_(this, sub).evaluate();
	}
	private static <T> TailCall<Boolean> startsWith_(FuncList<T> list, FuncList<T> sub) {
		return sub.isEmpty()
				? TailCall.returns(true)
				: list.isEmpty()
					? TailCall.returns(false)
					: list.head().equals(sub.head())
						? TailCall.suspend(()-> startsWith_(list.tail(), sub.tail()))
						: TailCall.returns(false);
	}
	
	/**
	 * 주어진 리스트가 본 리스트에 연속된 부분 리스트로 포함되는지 여부를 반환한다.
	 *
	 * @param sub	찾을 부분 리스트.
	 * @return	포함되면 {@code true}. {@code sub}가 빈 리스트이면 항상 {@code true}.
	 */
	public boolean hasSubList(FuncList<T> sub) {
		return hasSubList_(this, sub).evaluate();
	}
	/**
	 * {@link #hasSubList(FuncList)}의 구현에 사용되는 내부 함수.
	 * <p>
	 * <b>주의:</b> 구현용 함수이나 {@code public}으로 노출되어 있다.
	 *
	 * @param <T>	원소 타입.
	 * @param list	대상 리스트.
	 * @param sub	찾을 부분 리스트.
	 * @return	포함 여부를 계산하는 {@link TailCall}.
	 */
	public static <T> TailCall<Boolean> hasSubList_(FuncList<T> list, FuncList<T> sub) {
		return list.isEmpty()
				? TailCall.returns(sub.isEmpty())
				: list.startsWith(sub)
					? TailCall.returns(true)
					: TailCall.suspend(()-> hasSubList_(list.tail(), sub));
	}
	
	/**
	 * 리스트의 원소들을 같은 순서로 담은 새 {@link java.util.ArrayList}를 반환한다.
	 *
	 * @return	원소 리스트.
	 */
	public List<T> toList() {
		List<T> list = Lists.newArrayList();
		fill(list);
		
		return list;
	}
	
	/**
	 * 리스트의 원소들을 순서대로 주어진 {@link List}의 끝에 추가한다.
	 *
	 * @param list	원소를 추가할 대상 리스트.
	 */
	public void fill(List<T> list) {
		FuncList<T> current = this;
		while ( current instanceof Cons ) {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Cons<T> cons = (Cons)current;
			list.add(cons.m_head);
			current = cons.m_tail;
		}
	}
	
	private static class Nil<T> extends FuncList<T> {
		private Nil() { }

		@Override
		public T head() {
			throw new IllegalStateException("empty list");
		}

		@Override
		public FuncList<T> tail() {
			throw new IllegalStateException("empty list");
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public int length() {
			return 0;
		}

		@Override
		public FuncList<T> drop(int n) {
			return this;
		}

		@Override
		public FuncList<T> dropWhile(Function<T,Boolean> cond) {
			return this;
		}

		@Override
		public FuncList<T> reverse() {
			return this;
		}

		@Override
		public <U> U foldLeft(U identity, BiFunction<U,T,U> fold) {
			return identity;
		}

		@Override
		public <U> Tuple<U, FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U, T, U> fold) {
			return Tuple.of(identity, this);
		}
		
		@Override
		public String toString() {
			return "[]";
		}
	}
	
	private static class Cons<T> extends FuncList<T> {
		private final T m_head;
		private final FuncList<T> m_tail;
		private final int m_length;
		
		private Cons(T head, FuncList<T> tail) {
			m_head = head;
			m_tail = tail;
			m_length = tail.length() + 1;
		}

		@Override
		public T head() {
			return m_head;
		}

		@Override
		public FuncList<T> tail() {
			return m_tail;
		}

		@Override
		public boolean isEmpty() {
			return false;
		}

		@Override
		public int length() {
			return m_length;
		}

		@Override
		public FuncList<T> drop(int n) {
			return drop_(this, n).evaluate();
		}
		private static <T> TailCall<FuncList<T>> drop_(FuncList<T> list, int n) {
			return (n <= 0 || list.isEmpty())
					? TailCall.returns(list)
					: TailCall.suspend(()-> drop_(list.tail(), n-1));
		}

		@Override
		public FuncList<T> dropWhile(Function<T, Boolean> cond) {
			return dropWhile_(this, cond).evaluate();
		}
		private static <T> TailCall<FuncList<T>> dropWhile_(FuncList<T> list,
															Function<T, Boolean> cond) {
			return (list.isEmpty() || !cond.apply(list.head()))
					? TailCall.returns(list)
					: TailCall.suspend(()-> dropWhile_(list.tail(), cond));
		}

		@Override
		public FuncList<T> reverse() {
			return reverse(list(), this).evaluate();
		}
		private static <T> TailCall<FuncList<T>> reverse(FuncList<T> acc, FuncList<T> list) {
			return list.isEmpty()
					? TailCall.returns(acc)
					: TailCall.suspend(() -> reverse(new Cons<>(list.head(), acc), list.tail()));
		}

		@Override
		public <U> U foldLeft(U identity, BiFunction<U,T,U> fold) {
			return foldLeft(identity, this, fold).evaluate();
		}
		private <U> TailCall<U> foldLeft(U acc, FuncList<T> list, BiFunction<U,T,U> fold) {
			return list.isEmpty()
					? TailCall.returns(acc)
					: TailCall.suspend(() -> foldLeft(fold.apply(acc, list.head()),
														list.tail(), fold));
		}

		@Override
		public <U> Tuple<U,FuncList<T>> foldLeft(U identity, U stopper, BiFunction<U,T,U> fold) {
			return foldLeft_(identity, stopper, this, fold);
		}
		private static <T,U> Tuple<U,FuncList<T>> foldLeft_(U acc, U stopper, FuncList<T> list,
															BiFunction<U,T,U> fold) {
			return list.isEmpty() || acc.equals(stopper)
					? Tuple.of(acc, list)
					: foldLeft_(fold.apply(acc, list.head()), stopper, list.tail(), fold);
		}
		
		@Override
		public String toString() {
			StringBuilder builder = foldLeft(new StringBuilder(),
											(b,d)-> b.append(""+d).append(','));
			builder.setLength(builder.length()-1);
			return "[" + builder.toString() + "]";
		}
	}
}
