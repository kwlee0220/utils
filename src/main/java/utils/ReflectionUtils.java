package utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

/**
 * 자바 리플렉션 관련 유틸리티 메서드를 모아둔 클래스.
 * <p>
 * 내부 구현은 대부분 Apache Commons Lang3의 {@link FieldUtils} / {@link ClassUtils}에
 * 위임하며, 프로젝트 고유의 호출 관례(예외 시그니처, 정렬 순서 등)에 맞춰 얇은 래퍼를
 * 제공한다. 인스턴스화는 불가능하며 모든 메서드는 정적 메서드이다.
 *
 * @author Kang-Woo Lee (ETRI)
 */
public final class ReflectionUtils {
	private ReflectionUtils() {
		throw new AssertionError("Should not be called: " + ReflectionUtils.class);
	}

	/**
	 * 주어진 클래스의 상속 계층(자기 자신 + 모든 상위 클래스)을
	 * <b>최상위 부모(보통 {@link Object})부터 자기 자신까지</b>의 순서로 반환한다.
	 * <p>
	 * 예를 들어, {@code C extends B extends A extends Object}인 경우
	 * {@code [Object, A, B, C]} 순서의 리스트가 반환된다.
	 * 인터페이스는 포함되지 않는다.
	 * <p>
	 * 입력이 인터페이스인 경우는 상위 클래스 체인이 없으므로 해당 인터페이스 자체만 단독으로
	 * 포함된 리스트가 반환된다.
	 *
	 * @param cls	계층을 탐색할 클래스. {@code null}이면 안 된다.
	 * @return	최상위 부모부터 자기 자신까지 정렬된 클래스 리스트.
	 *			반환되는 리스트는 가변(mutable)이며 호출자가 자유롭게 수정할 수 있다.
	 */
	public static List<Class<?>> traverseClassHierarchy(Class<?> cls) {
		Preconditions.checkNotNullArgument(cls, "cls is null");

		List<Class<?>> hierarchy = new ArrayList<>();
		ClassUtils.hierarchy(cls).forEach(hierarchy::add);
		Collections.reverse(hierarchy);
		return hierarchy;
	}

	/**
	 * 주어진 클래스 또는 인터페이스가 직접 또는 간접적으로 구현/상속하는 모든 인터페이스를
	 * 반환한다.
	 * <p>
	 * 입력이 일반 클래스인 경우, 해당 클래스와 모든 상위 클래스가 구현하는 인터페이스 및
	 * 그 인터페이스들이 상속하는 super-interface를 재귀적으로 모두 포함한다. 입력 클래스
	 * 자체는 결과에 포함되지 않는다.
	 * <p>
	 * 입력이 인터페이스인 경우, 자기 자신을 결과 첫 항목으로 포함하고 그 뒤에 모든
	 * super-interface가 재귀적으로 이어 반환된다.
	 * <p>
	 * 결과 리스트의 순서는 {@link ClassUtils#getAllInterfaces(Class)}의 순서를 따르며,
	 * 중복은 제거된다.
	 *
	 * @param cls	대상 클래스 또는 인터페이스. {@code null}이면 안 된다.
	 * @return	재귀적으로 수집된 인터페이스 리스트. 빈 리스트일 수 있다.
	 *			반환되는 리스트는 가변(mutable)이며 호출자가 자유롭게 수정할 수 있다.
	 */
	public static List<Class<?>> getAllInterfaces(Class<?> cls) {
		Preconditions.checkNotNullArgument(cls, "cls is null");

		List<Class<?>> superInterfaces = ClassUtils.getAllInterfaces(cls);
		if ( !cls.isInterface() ) {
			return new ArrayList<>(superInterfaces);
		}
		List<Class<?>> result = new ArrayList<>(superInterfaces.size() + 1);
		result.add(cls);
		result.addAll(superInterfaces);
		return result;
	}

	/**
	 * 클래스에 선언된 모든 필드와 부모 클래스의 모든 필드를 리스트로 반환한다.
	 * <p>
	 * 접근 제어자(private/protected/package-private/public)와 무관하게 모든 선언 필드를
	 * 포함하며, 정적/인스턴스 필드를 모두 포함한다. 필드 순서와 중복 처리는 commons-lang3의
	 * {@link FieldUtils#getAllFieldsList(Class)} 동작을 따른다.
	 *
	 * @param cls	필드를 수집할 클래스. {@code null}이면 안 된다.
	 * @return	필드 리스트. 클래스에 선언된 필드가 없으면 빈 리스트를 반환한다.
	 *			반환되는 리스트는 가변(mutable)이며 호출자가 자유롭게 수정할 수 있다.
	 */
	public static List<Field> getFieldList(Class<?> cls) {
		Preconditions.checkNotNullArgument(cls, "cls is null");

		return new ArrayList<>(FieldUtils.getAllFieldsList(cls));
	}

	/**
	 * 주어진 객체에서 이름에 해당하는 필드 값을 리플렉션을 사용하여 가져온다.
	 * <p>
	 * 필드 검색은 {@code obj}의 실제 클래스부터 시작하여 모든 상위 클래스(Object 제외)까지
	 * 거슬러 올라가며 수행된다. 접근 제어자(private/protected/package-private)와 무관하게
	 * 필드 값을 읽을 수 있으며, 인스턴스 필드와 정적 필드를 모두 지원한다. 정적 필드의 경우
	 * {@code obj} 파라미터는 필드 위치 클래스 결정에만 사용되고 값 자체에는 영향을 주지 않는다.
	 * <p>
	 * 반환 타입은 호출 컨텍스트에서 추론되는 {@code <T>}이며, 캐스팅 안전성은 호출자가 보장해야 한다.
	 *
	 * @param <T>			반환 값의 추정 타입.
	 * @param obj			필드 값을 읽을 대상 객체. {@code null}이면 안 된다.
	 * @param fieldName		읽을 필드의 이름. {@code null}이면 안 된다.
	 * @return 필드의 현재 값. 필드가 원시 타입인 경우 박싱되어 반환된다.
	 * @throws NoSuchFieldException		{@code obj}의 클래스 계층에서 {@code fieldName}에
	 *									해당하는 필드를 찾을 수 없는 경우.
	 * @throws IllegalAccessException	접근 권한 변경(setAccessible)에 실패한 경우.
	 *									일반적인 환경에서는 거의 발생하지 않으며, 보안 매니저가
	 *									설정되어 리플렉션 접근을 막는 경우 등에 발생할 수 있다.
	 */
	public static <T> T getFieldValue(Object obj, String fieldName)
		throws NoSuchFieldException, IllegalAccessException {
		Preconditions.checkNotNullArgument(obj, "object is null");
		Preconditions.checkNotNullArgument(fieldName, "field name is null");

		Field field = FieldUtils.getField(obj.getClass(), fieldName, true);
		if ( field == null ) {
			throw new NoSuchFieldException("Field not found: " + fieldName);
		}
		@SuppressWarnings("unchecked")
		T value = (T)FieldUtils.readField(field, obj, true);
		return value;
	}
	
	/**
	 * 주어진 클래스의 no-arg 생성자를 호출하여 새 인스턴스를 생성한다.
	 * <p>
	 * 접근 제어자(private 포함)와 무관하게 호출 가능하다.
	 * 실패 사유에 따라 다음과 같이 예외가 분리되어 전파된다.
	 * <ul>
	 *   <li>{@link NoSuchMethodException} — no-arg 생성자가 존재하지 않는 경우.
	 *       인터페이스(인스턴스 생성자 자체가 없음) 입력 시에도 이 예외가 발생한다.</li>
	 *   <li>{@link InvocationTargetException} — 생성자 본문에서 예외가 발생한 경우.
	 *       실제 원인은 {@link InvocationTargetException#getTargetException()} 또는
	 *       {@link Throwable#getCause()}로 확인할 수 있다.</li>
	 *   <li>{@link InternalException} — 그 외 사유로 인스턴스화에 실패한 경우.
	 *       대표적으로 {@link InstantiationException}(추상 클래스)이 있다.
	 *       원본 예외는 cause로 보존된다.</li>
	 *   <li>{@link SecurityException} — (unchecked) {@link Constructor#setAccessible(boolean)} 호출이
	 *       SecurityManager에 의해 차단된 경우 그대로 전파된다.</li>
	 * </ul>
	 *
	 * @param <T>	반환 인스턴스 타입.
	 * @param cls	인스턴스를 생성할 대상 클래스. {@code null}이면 안 된다.
	 * @return	{@code cls}의 새 인스턴스.
	 * @throws NoSuchMethodException		no-arg 생성자가 없는 경우 (인터페이스 입력 포함).
	 * @throws InvocationTargetException	생성자 본문에서 예외가 발생한 경우.
	 * @throws InternalException			추상 클래스 등 기타 사유로 인스턴스화에 실패한 경우.
	 * @throws SecurityException			(unchecked) SecurityManager가 {@code setAccessible} 호출을
	 *										차단한 경우.
	 */
	public static <T> T newInstance(Class<? extends T> cls) throws NoSuchMethodException, InvocationTargetException {
		Preconditions.checkNotNullArgument(cls, "cls is null");

		try {
			Constructor<? extends T> ctor = cls.getDeclaredConstructor();
			ctor.setAccessible(true);
			return ctor.newInstance();
		}
		catch ( IllegalAccessException | InstantiationException | IllegalArgumentException e ) {
			throw new InternalException("Failed to create an instance of class: " + cls, e);
		}
	}

	/**
	 * 주어진 클래스 이름을 로드하여 no-arg 생성자로 새 인스턴스를 생성하되,
	 * 로드된 클래스가 {@code typeCls}의 하위 타입인지 검증한다.
	 * <p>
	 * 검증 통과 시 {@code typeCls} 타입으로 캐스팅된 인스턴스를 반환한다.
	 * 실패 사유에 따라 다음과 같이 예외가 분리되어 전파된다.
	 * <ul>
	 *   <li>{@link ClassNotFoundException} — 클래스 이름에 해당하는 클래스를 찾지 못한 경우.</li>
	 *   <li>{@link ClassCastException} — 로드된 클래스가 {@code typeCls}의 하위 타입이 아닌 경우.</li>
	 *   <li>{@link NoSuchMethodException} — no-arg 생성자가 존재하지 않는 경우.</li>
	 *   <li>{@link InvocationTargetException} — 생성자 본문에서 예외가 발생한 경우.</li>
	 *   <li>{@link InternalException} — 추상 클래스이거나 접근 권한 변경 실패 등 기타 사유로
	 *       인스턴스화에 실패한 경우. 원본 예외는 cause로 보존된다.</li>
	 * </ul>
	 *
	 * @param <T>		반환 인스턴스 타입.
	 * @param clsName	로드할 클래스의 fully-qualified 이름. {@code null}이면 안 된다.
	 * @param typeCls	기대 타입의 {@link Class}. {@code null}이면 안 된다.
	 * @return	{@code typeCls}로 캐스팅된 새 인스턴스.
	 * @throws ClassNotFoundException		클래스 로딩 실패.
	 * @throws ClassCastException			로드된 클래스가 {@code typeCls}의 하위 타입이 아닌 경우.
	 * @throws NoSuchMethodException		no-arg 생성자가 없는 경우.
	 * @throws InvocationTargetException	생성자 본문에서 예외가 발생한 경우.
	 * @throws InternalException			추상 클래스이거나 접근 권한 변경 실패 등 기타 사유.
	 */
	public static <T> T newInstance(String clsName, Class<T> typeCls)
		throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
		Preconditions.checkNotNullArgument(clsName, "clsName is null");
		Preconditions.checkNotNullArgument(typeCls, "typeCls is null");

		Class<?> cls = Class.forName(clsName);
		if ( !typeCls.isAssignableFrom(cls) ) {
			String message = String.format("Incompatible class-cast: actual=%s, expected=%s",
											clsName, typeCls.getName());
			throw new ClassCastException(message);
		}

		@SuppressWarnings("unchecked")
		Class<? extends T> typed = (Class<? extends T>)cls;
		return newInstance(typed);
	}
}
