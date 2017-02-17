package utils.xml;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;


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

	public default String id() {
		return attr("id").orElse(null);
	}
	public default FluentElement id(String value) {
		return attr("id", value);
	}

	public Stream<FluentElement> children();
	public Stream<FluentElement> children(String name);
	
	public Iterable<FluentElement> iterateChildren();
	public Iterable<FluentElement> iterateChildren(String name);
	
	public FluentElement firstChild();
	public FluentElement firstChild(String name);
	public FluentElement tryFirstChild();
	public FluentElement tryFirstChild(String name);
	
	public FluentElement with(Consumer<FluentElement> consumer);
	public FluentElement withInt(Consumer<Integer> consumer);
	public FluentElement withLong(Consumer<Long> consumer);
	public FluentElement withDouble(Consumer<Double> consumer);
	public FluentElement withBoolean(Consumer<Boolean> consumer);
	public FluentElement withText(Consumer<String> consumer);
	
	public <T> Optional<T> map(Function<FluentElement,T> func);

	public Optional<String> attr(String name);
	public int attrInt(String name, int defValue);
	public long attrLong(String name, long defValue);
	public double attrDouble(String name, double defValue);
	public FluentElement attr(String name, Object value);

	public default Optional<String> text() {
		return XmlUtils.getText(get());
	}
	public default Optional<Integer> textInt() {
		return text().map(Integer::parseInt);
	}
	public default Optional<Long> textLong() {
		return text().map(Long::parseLong);
	}
	public default Optional<Boolean> textBoolean() {
		return text().map(s -> {
			if ( s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") ) {
				return false;
			}
			else {
				return s.length() > 0;
			}
		});
	}
	public default Optional<Float> textFloat() {
		return text().map(Float::parseFloat);
	}
	public default Optional<Double> textDouble() {
		return text().map(Double::parseDouble);
	}
	public default FluentElement text(String text) {
		XmlUtils.appendText(get(), text);
		return this;
	}
	public default FluentElement text(char c) {
		XmlUtils.appendText(get(), ""+c);
		return this;
	}
	public default FluentElement text(int text) {
		XmlUtils.appendText(get(), ""+text);
		return this;
	}
	public default FluentElement text(long text) {
		XmlUtils.appendText(get(), ""+text);
		return this;
	}
	public default FluentElement text(double text) {
		XmlUtils.appendText(get(), ""+text);
		return this;
	}
	public default FluentElement text(boolean text) {
		XmlUtils.appendText(get(), ""+text);
		return this;
	}
	public default FluentElement cdata(String text) {
		XmlUtils.appendCDATASection(get(), text);
		return this;
	}
	
	public default FluentElement ifPresentOrElse(Consumer<FluentElement> thenPart, Runnable elsePart) {
		if ( !(this instanceof NonExistentElement) ) {
			thenPart.accept(this);
		}
		else {
			elsePart.run();
		}
		
		return this;
	}
	
	public default <T> T getOrElse(Function<FluentElement,T> thenPart, Supplier<T> elsePart) {
		return !(this instanceof NonExistentElement) ? thenPart.apply(this) : elsePart.get();
	}
	
	public default FluentElement ifPresent(Consumer<FluentElement> c) {
		if ( !(this instanceof NonExistentElement) ) {
			c.accept(this);
		}
		
		return this;
	}
	
	public default FluentElement ifNotPresent(Runnable c) {
		if ( this instanceof NonExistentElement ) {
			c.run();
		}
		return this;
	}
}
