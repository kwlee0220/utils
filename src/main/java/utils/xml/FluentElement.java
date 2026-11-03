package utils.xml;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import utils.func.FOption;


/**
 * DOM {@link Element}를 감싸 속성·텍스트·자식 element를 fluent 방식으로 읽고 쓰는 인터페이스.
 * <p>
 * 존재하지 않는 element(예: {@link #tryFirstChild(String)}에서 찾지 못한 경우)도 본 인터페이스로
 * 표현되며, 이때 {@link #exists()}는 {@code false}이고 읽기 연산은 빈 결과를, 쓰기 연산은 아무 일도 하지 않는다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FluentElement extends FluentNode {
	/**
	 * 본 FluentElement에 해당하는 DOM {@link Element} 객체를 반환한다.
	 * 
	 * @return	DOM Element 객체.
	 */
	public Element get();
	
	/**
	 * 본 FluentElement의 부모 element를 반환한다.
	 * <p>
	 * 부모 노드가 없거나 element가 아닌 경우(예: 루트 element)에는 존재하지 않는 element
	 * ({@link #exists()}가 {@code false})가 반환된다.
	 * 
	 * @return	부모 element.
	 */
	public FluentElement parent();

	/**
	 * 본 FluentElement에 해당하는 DOM {@link Node} 객체를 반환한다.
	 * 
	 * @return	DOM Node 객체.
	 */
	@Override
	public default Node asNode() {
		return get();
	}
	
	/**
	 * 본 FluentElement가 속한 문서를 {@link FluentDocument}로 반환한다.
	 * <p>
	 * 호출할 때마다 새 {@link FluentDocument} 객체가 생성된다.
	 * 
	 * @return	소속 문서.
	 */
	public default FluentDocument getDocument() {
		return new FluentDocument(get().getOwnerDocument());
	}
	
	/**
	 * 본 FluentElement가 XML 문서에 실제로 존재하는 element인지 여부를 반환한다.
	 * 
	 * @return	존재 여부.
	 */
	public boolean exists();

	/**
	 * 본 element의 local name(namespace prefix를 제외한 이름)을 반환한다.
	 *
	 * @return	local name.
	 */
	public default String localName() {
		return get().getLocalName();
	}

	/**
	 * 주어진 이름의 속성 값을 반환한다.
	 * 
	 * @param name	대상 속성 이름
	 * @return	속성 이름에 해당하는 값. 만일 속성 이름에 해당하는 속성이 정의되지 않는 경우는
	 * 			{@link FOption#empty()}
	 */
	public FOption<String> attr(String name);
	
	/**
	 * 주어진 이름의 속성 값을 설정한다.
	 * 
	 * @param name	대상 속성 이름
	 * @param value	설정할 속성 값.
	 * @return	본 객체.
	 */
	public FluentElement attr(String name, Object value);
	
	/**
	 * 주어진 이름의 속성 값을 {@code int}로 변환하여 반환한다.
	 *
	 * @param name	대상 속성 이름.
	 * @return	속성 값. 속성이 없으면 {@link FOption#empty()}.
	 * @throws NumberFormatException	속성 값이 정수 형식이 아닌 경우.
	 */
	public default FOption<Integer> attrInt(String name) {
		return attr(name).map(Integer::parseInt);
	}
	/**
	 * 주어진 이름의 속성 값을 {@code long}으로 변환하여 반환한다.
	 *
	 * @param name	대상 속성 이름.
	 * @return	속성 값. 속성이 없으면 {@link FOption#empty()}.
	 * @throws NumberFormatException	속성 값이 정수 형식이 아닌 경우.
	 */
	public default FOption<Long> attrLong(String name) {
		return attr(name).map(Long::parseLong);
	}
	/**
	 * 주어진 이름의 속성 값을 {@code double}로 변환하여 반환한다.
	 *
	 * @param name	대상 속성 이름.
	 * @return	속성 값. 속성이 없으면 {@link FOption#empty()}.
	 * @throws NumberFormatException	속성 값이 실수 형식이 아닌 경우.
	 */
	public default FOption<Double> attrDouble(String name) {
		return attr(name).map(Double::parseDouble);
	}
	/**
	 * 주어진 이름의 속성 값을 {@code boolean}으로 변환하여 반환한다.
	 * <p>
	 * 값이 대소문자 구분 없이 {@code "false"} 또는 {@code "no"}이면 {@code false},
	 * 그 밖의 비어 있지 않은 값(예: {@code "0"})은 모두 {@code true}로 해석한다.
	 *
	 * @param name	대상 속성 이름.
	 * @return	속성 값. 속성이 없으면 {@link FOption#empty()}.
	 */
	public default FOption<Boolean> attrBoolean(String name) {
		return attr(name).map(s -> {
			if ( s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") ) {
				return false;
			}
			else {
				return s.length() > 0;
			}
		});
	}

	/**
	 * 본 element의 텍스트 내용을 반환한다.
	 * <p>
	 * 앞쪽부터 연속된 텍스트/CDATA 자식 노드의 값을 각각 trim하여 이어 붙이며, 첫 번째 다른 종류의
	 * 자식 노드를 만나면 멈춘다. 존재하는 element는 자식 노드가 없어도 빈 문자열을 반환하며,
	 * 존재하지 않는 element이면 {@link FOption#empty()}를 반환한다.
	 *
	 * @return	텍스트 내용.
	 */
	public FOption<String> text();
	/**
	 * 본 element의 텍스트 내용({@link #text()})을 {@code int}로 변환하여 반환한다.
	 *
	 * @return	변환된 값. 존재하지 않는 element이면 {@link FOption#empty()}.
	 * @throws NumberFormatException	텍스트가 정수 형식이 아닌 경우(빈 텍스트 포함).
	 */
	public default FOption<Integer> textInt() {
		return text().map(Integer::parseInt);
	}
	/**
	 * 본 element의 텍스트 내용({@link #text()})을 {@code long}으로 변환하여 반환한다.
	 *
	 * @return	변환된 값. 존재하지 않는 element이면 {@link FOption#empty()}.
	 * @throws NumberFormatException	텍스트가 정수 형식이 아닌 경우(빈 텍스트 포함).
	 */
	public default FOption<Long> textLong() {
		return text().map(Long::parseLong);
	}
	/**
	 * 본 element의 텍스트 내용({@link #text()})을 {@code boolean}으로 변환하여 반환한다.
	 * <p>
	 * 텍스트가 대소문자 구분 없이 {@code "false"} 또는 {@code "no"}이거나 빈 문자열이면 {@code false},
	 * 그 밖의 값은 모두 {@code true}로 해석한다.
	 *
	 * @return	변환된 값. 존재하지 않는 element이면 {@link FOption#empty()}.
	 */
	public default FOption<Boolean> textBoolean() {
		return text().map(s -> {
			if ( s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") ) {
				return false;
			}
			else {
				return s.length() > 0;
			}
		});
	}
	/**
	 * 본 element의 텍스트 내용({@link #text()})을 {@code float}로 변환하여 반환한다.
	 *
	 * @return	변환된 값. 존재하지 않는 element이면 {@link FOption#empty()}.
	 * @throws NumberFormatException	텍스트가 실수 형식이 아닌 경우(빈 텍스트 포함).
	 */
	public default FOption<Float> textFloat() {
		return text().map(Float::parseFloat);
	}
	/**
	 * 본 element의 텍스트 내용({@link #text()})을 {@code double}로 변환하여 반환한다.
	 *
	 * @return	변환된 값. 존재하지 않는 element이면 {@link FOption#empty()}.
	 * @throws NumberFormatException	텍스트가 실수 형식이 아닌 경우(빈 텍스트 포함).
	 */
	public default FOption<Double> textDouble() {
		return text().map(Double::parseDouble);
	}
	/**
	 * 본 element의 텍스트 내용({@link #text()})의 첫 문자를 반환한다.
	 *
	 * @return	첫 문자. 존재하지 않는 element이면 {@link FOption#empty()}.
	 * @throws StringIndexOutOfBoundsException	텍스트가 빈 문자열인 경우.
	 */
	public default FOption<Character> textChar() {
		return text().map(s -> s.charAt(0));
	}
	
	/**
	 * 주어진 문자열을 텍스트 노드로 만들어 본 element의 마지막 자식으로 추가한다.
	 * <p>
	 * 기존 텍스트를 대체하지 않고 덧붙인다. 존재하지 않는 element이면 아무 일도 하지 않는다.
	 *
	 * @param text	추가할 텍스트.
	 * @return	본 객체.
	 */
	public FluentElement text(String text);
	/**
	 * 주어진 문자를 텍스트 노드로 추가한다({@link #text(String)} 참조).
	 *
	 * @param c	추가할 문자.
	 * @return	본 객체.
	 */
	public default FluentElement text(char c) {
		return text("" + c);
	}
	/**
	 * 주어진 정수 값의 문자열 표현을 텍스트 노드로 추가한다({@link #text(String)} 참조).
	 *
	 * @param value	추가할 값.
	 * @return	본 객체.
	 */
	public default FluentElement text(int value) {
		return text("" + value);
	}
	/**
	 * 주어진 정수 값의 문자열 표현을 텍스트 노드로 추가한다({@link #text(String)} 참조).
	 *
	 * @param value	추가할 값.
	 * @return	본 객체.
	 */
	public default FluentElement text(long value) {
		return text("" + value);
	}
	/**
	 * 주어진 실수 값의 문자열 표현을 텍스트 노드로 추가한다({@link #text(String)} 참조).
	 *
	 * @param value	추가할 값.
	 * @return	본 객체.
	 */
	public default FluentElement text(double value) {
		return text("" + value);
	}
	/**
	 * 주어진 boolean 값의 문자열 표현({@code "true"}/{@code "false"})을 텍스트 노드로 추가한다
	 * ({@link #text(String)} 참조).
	 *
	 * @param value	추가할 값.
	 * @return	본 객체.
	 */
	public default FluentElement text(boolean value) {
		return text("" + value);
	}
	/**
	 * 주어진 문자열을 CDATA section으로 만들어 본 element의 마지막 자식으로 추가한다.
	 * <p>
	 * 존재하지 않는 element이면 아무 일도 하지 않는다.
	 *
	 * @param text	CDATA section의 내용.
	 * @return	본 객체.
	 */
	public FluentElement cdata(String text);

	/**
	 * 본 element의 자식 element들을 문서 순서대로 반환한다.
	 * <p>
	 * element가 아닌 자식 노드(텍스트, 주석 등)는 제외된다.
	 *
	 * @return	자식 element 스트림.
	 */
	public Stream<FluentElement> children();
	/**
	 * 본 element의 자식 element 중 local name이 주어진 이름과 같은 것들을 문서 순서대로 반환한다.
	 *
	 * @param name	찾을 자식 element의 local name.
	 * @return	자식 element 스트림.
	 */
	public Stream<FluentElement> children(String name);
	
	/**
	 * 본 element의 자식 element들을 순회하는 {@link Iterable}을 반환한다.
	 * <p>
	 * element가 아닌 자식 노드는 제외된다.
	 *
	 * @return	자식 element {@link Iterable}.
	 */
	public Iterable<FluentElement> iterateChildren();
	/**
	 * 본 element의 자식 element 중 local name이 주어진 이름과 같은 것들을 순회하는
	 * {@link Iterable}을 반환한다.
	 *
	 * @param name	찾을 자식 element의 local name.
	 * @return	자식 element {@link Iterable}.
	 */
	public Iterable<FluentElement> iterateChildren(String name);
	
	/**
	 * 본 element의 첫 번째 자식 element를 반환한다.
	 *
	 * @return	첫 번째 자식 element.
	 * @throws XmlSerializationException	자식 element가 없는 경우.
	 */
	public FluentElement firstChild();
	/**
	 * 본 element의 자식 element 중 local name이 주어진 이름과 같은 첫 번째 element를 반환한다.
	 *
	 * @param name	찾을 자식 element의 local name.
	 * @return	첫 번째 자식 element.
	 * @throws XmlSerializationException	해당 이름의 자식 element가 없는 경우.
	 */
	public FluentElement firstChild(String name);

	/**
	 * 본 element의 첫 번째 자식 element를 반환한다.
	 *
	 * @return	첫 번째 자식 element. 자식 element가 없으면 {@link FOption#empty()}.
	 */
	public default FOption<FluentElement> tryFirstChild() {
		return FOption.from(children().findFirst());
	}
	
	/**
	 * 본 element의 자식 element 중 local name이 주어진 이름과 같은 첫 번째 element를 반환한다.
	 * <p>
	 * {@link #tryFirstChild()}와 달리 {@link FOption}이 아니라, 찾지 못한 경우 존재하지 않는
	 * element({@link #exists()}가 {@code false}이고 부모가 본 element인 객체)를 반환한다.
	 *
	 * @param name	찾을 자식 element의 local name.
	 * @return	첫 번째 자식 element 또는 존재하지 않는 element.
	 */
	public default FluentElement tryFirstChild(String name) {
		return children(name).findFirst().orElse(new NonExistentElement(this));
	}
	
	/**
	 * 존재하는 element이면 본 객체를 인자로 주어진 consumer를 호출한다.
	 * 존재하지 않는 element이면 아무 일도 하지 않는다.
	 *
	 * @param consumer	호출할 consumer.
	 * @return	본 객체.
	 */
	public FluentElement with(Consumer<FluentElement> consumer);

	/**
	 * {@link #textInt()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 * @throws NumberFormatException	텍스트가 정수 형식이 아닌 경우.
	 */
	public default FluentElement withInt(Consumer<Integer> consumer) {
		textInt().ifPresent(consumer);
		return this;
	}
	/**
	 * {@link #textLong()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 * @throws NumberFormatException	텍스트가 정수 형식이 아닌 경우.
	 */
	public default FluentElement withLong(Consumer<Long> consumer) {
		textLong().ifPresent(consumer);
		return this;
	}
	/**
	 * {@link #textDouble()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 * @throws NumberFormatException	텍스트가 실수 형식이 아닌 경우.
	 */
	public default FluentElement withDouble(Consumer<Double> consumer) {
		textDouble().ifPresent(consumer);
		return this;
	}
	/**
	 * {@link #textBoolean()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 */
	public default FluentElement withBoolean(Consumer<Boolean> consumer) {
		textBoolean().ifPresent(consumer);
		return this;
	}
	/**
	 * {@link #text()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 */
	public default FluentElement withText(Consumer<String> consumer) {
		text().ifPresent(consumer);
		return this;
	}
	/**
	 * {@link #textChar()} 값이 있으면 주어진 consumer에 전달한다.
	 *
	 * @param consumer	값을 받을 consumer.
	 * @return	본 객체.
	 * @throws StringIndexOutOfBoundsException	텍스트가 빈 문자열인 경우.
	 */
	public default FluentElement withChar(Consumer<Character> consumer) {
		textChar().ifPresent(consumer);
		return this;
	}
	
	/**
	 * 존재하는 element이면 본 객체를 인자로 주어진 consumer를 호출한다.
	 *
	 * @param c	호출할 consumer.
	 * @return	본 객체.
	 */
	public default FluentElement ifPresent(Consumer<FluentElement> c) {
		if ( exists() ) {
			c.accept(this);
		}
		return this;
	}
	
	/**
	 * 존재하지 않는 element이면 주어진 작업을 실행한다.
	 *
	 * @param c	실행할 작업.
	 * @return	본 객체.
	 */
	public default FluentElement ifNotPresent(Runnable c) {
		if ( !exists() ) {
			c.run();
		}
		return this;
	}
	
	/**
	 * 존재하는 element이면 {@code thenPart}를 본 객체를 인자로 호출하고,
	 * 그렇지 않으면 {@code elsePart}를 실행한다.
	 *
	 * @param thenPart	존재하는 경우 호출할 consumer.
	 * @param elsePart	존재하지 않는 경우 실행할 작업.
	 * @return	본 객체.
	 */
	public default FluentElement ifPresentOrElse(Consumer<FluentElement> thenPart, Runnable elsePart) {
		if ( exists() ) {
			thenPart.accept(this);
		}
		else {
			elsePart.run();
		}
		
		return this;
	}
	
	/**
	 * 존재하는 element이면 주어진 함수를 적용한 결과를 반환한다.
	 * <p>
	 * 함수가 {@code null}을 반환하면 {@code null}을 담은 {@link FOption}이 반환된다.
	 *
	 * @param <T>	함수 결과 타입.
	 * @param func	본 객체에 적용할 함수.
	 * @return	함수 적용 결과. 존재하지 않는 element이면 {@link FOption#empty()}.
	 */
	public default <T> FOption<T> map(Function<FluentElement,T> func) {
		return (exists()) ? FOption.of(func.apply(this)) : FOption.empty();
	}
	
	/**
	 * 존재하는 element이면 {@code thenPart}를 적용한 결과를, 그렇지 않으면 {@code elsePart}가
	 * 제공하는 값을 반환한다.
	 *
	 * @param <T>		결과 타입.
	 * @param thenPart	존재하는 경우 본 객체에 적용할 함수.
	 * @param elsePart	존재하지 않는 경우 값을 제공할 supplier.
	 * @return	결과 값.
	 */
	public default <T> T getOrElse(Function<FluentElement,T> thenPart, Supplier<T> elsePart) {
		return exists() ? thenPart.apply(this) : elsePart.get();
	}
}
