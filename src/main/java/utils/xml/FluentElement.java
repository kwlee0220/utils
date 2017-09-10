package utils.xml;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import io.vavr.control.Option;


/**
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
	 * 본 FluentElement의 부모 노드를 반환한다.
	 * 
	 * @return	부모 노트
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
	 * 본 FluentElement의 FluentDocument 노드를 반환한다.
	 * 
	 * @return	부모 노트
	 */
	public default FluentDocument getDocument() {
		return new FluentDocument(get().getOwnerDocument());
	}
	
	/**
	 * 본 FluentElement가 XML 문제가 존재하는지 여부를 반환한다.
	 * 
	 * @return	존재 여부.
	 */
	public boolean exists();

	public default String localName() {
		return get().getLocalName();
	}

	/**
	 * 주어진 이름의 속성 값을 반환한다.
	 * 
	 * @param name	대상 속성 이름
	 * @return	속성 이름에 해당하는 값. 만일 속성 이름에 해당하는 속성이 정의되지 않는 경우는
	 * 			{@link Option#none()}
	 */
	public Option<String> attr(String name);
	
	/**
	 * 주어진 이름의 속성 값을 설정한다.
	 * 
	 * @param name	대상 속성 이름
	 * @param value	설정할 속성 값.
	 * @return	본 객체.
	 */
	public FluentElement attr(String name, Object value);
	
	public default Option<Integer> attrInt(String name) {
		return attr(name).map(Integer::parseInt);
	}
	public default Option<Long> attrLong(String name) {
		return attr(name).map(Long::parseLong);
	}
	public default Option<Double> attrDouble(String name) {
		return attr(name).map(Double::parseDouble);
	}
	public default Option<Boolean> attrBoolean(String name) {
		return attr(name).map(s -> {
			if ( s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") ) {
				return false;
			}
			else {
				return s.length() > 0;
			}
		});
	}

	public Option<String> text();
	public default Option<Integer> textInt() {
		return text().map(Integer::parseInt);
	}
	public default Option<Long> textLong() {
		return text().map(Long::parseLong);
	}
	public default Option<Boolean> textBoolean() {
		return text().map(s -> {
			if ( s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") ) {
				return false;
			}
			else {
				return s.length() > 0;
			}
		});
	}
	public default Option<Float> textFloat() {
		return text().map(Float::parseFloat);
	}
	public default Option<Double> textDouble() {
		return text().map(Double::parseDouble);
	}
	public default Option<Character> textChar() {
		return text().map(s -> s.charAt(0));
	}
	
	public FluentElement text(String text);
	public default FluentElement text(char c) {
		return text("" + c);
	}
	public default FluentElement text(int value) {
		return text("" + value);
	}
	public default FluentElement text(long value) {
		return text("" + value);
	}
	public default FluentElement text(double value) {
		return text("" + value);
	}
	public default FluentElement text(boolean value) {
		return text("" + value);
	}
	public FluentElement cdata(String text);

	public Stream<FluentElement> children();
	public Stream<FluentElement> children(String name);
	
	public Iterable<FluentElement> iterateChildren();
	public Iterable<FluentElement> iterateChildren(String name);
	
	public FluentElement firstChild();
	public FluentElement firstChild(String name);

	public default FluentElement tryFirstChild() {
		return children().findFirst().orElse(new NonExistentElement(this));
	}
	
	public default FluentElement tryFirstChild(String name) {
		return children(name).findFirst().orElse(new NonExistentElement(this));
	}
	
	public FluentElement with(Consumer<FluentElement> consumer);

	public default FluentElement withInt(Consumer<Integer> consumer) {
		textInt().forEach(consumer);
		return this;
	}
	public default FluentElement withLong(Consumer<Long> consumer) {
		textLong().forEach(consumer);
		return this;
	}
	public default FluentElement withDouble(Consumer<Double> consumer) {
		textDouble().forEach(consumer);
		return this;
	}
	public default FluentElement withBoolean(Consumer<Boolean> consumer) {
		textBoolean().forEach(consumer);
		return this;
	}
	public default FluentElement withText(Consumer<String> consumer) {
		text().forEach(consumer);
		return this;
	}
	public default FluentElement withChar(Consumer<Character> consumer) {
		textChar().forEach(consumer);
		return this;
	}
	
	public default FluentElement ifPresent(Consumer<FluentElement> c) {
		if ( exists() ) {
			c.accept(this);
		}
		return this;
	}
	
	public default FluentElement ifNotPresent(Runnable c) {
		if ( !exists() ) {
			c.run();
		}
		return this;
	}
	
	public default FluentElement ifPresentOrElse(Consumer<FluentElement> thenPart, Runnable elsePart) {
		if ( exists() ) {
			thenPart.accept(this);
		}
		else {
			elsePart.run();
		}
		
		return this;
	}
	
	public default <T> Option<T> map(Function<FluentElement,T> func) {
		return (exists()) ? Option.some(func.apply(this)) : Option.none();
	}
	
	public default <T> T getOrElse(Function<FluentElement,T> thenPart, Supplier<T> elsePart) {
		return exists() ? thenPart.apply(this) : elsePart.get();
	}
}
