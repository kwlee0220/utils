package utils.xml;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import utils.func.FOption;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
final class NonExistentElement implements FluentElement {
	private FluentElement m_parent;

	NonExistentElement(FluentElement parent) {
		m_parent = parent;
	}

	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public Element get() {
		throw new IllegalStateException("NonExistentElement.get()");
	}

	@Override
	public Node asNode() {
		throw new IllegalStateException("NonExistentElement.asNode()");
	}

	@Override
	public FluentElement parent() {
		return m_parent;
	}

	@Override
	public String localName() {
		return null;
	}

	@Override
	public FOption<String> attr(String name) {
		return FOption.empty();
	}

	@Override
	public FluentElement attr(String name, Object value) {
		return this;
	}

	@Override
	public FOption<String> text() {
		return FOption.empty();
	}

	@Override
	public FluentElement text(String text) {
		return this;
	}

	@Override
	public FluentElement cdata(String text) {
		return this;
	}

	@Override
	public Stream<FluentElement> children() {
		return Stream.empty();
	}

	@Override
	public Stream<FluentElement> children(String name) {
		return Stream.empty();
	}

	@Override
	public Iterable<FluentElement> iterateChildren() {
		return Collections.emptyList();
	}

	@Override
	public Iterable<FluentElement> iterateChildren(String name) {
		return Collections.emptyList();
	}

	@Override
	public FluentElement firstChild() {
		throw new XmlSerializationException("no child element");
	}
	
	@Override
	public FluentElement firstChild(String name) {
		throw new XmlSerializationException("no child element: name="+ name);
	}
	
	public FluentElement with(Consumer<FluentElement> consumer) {
		return this;
	}

	@Override
	public FluentElement appendChild(String childName) {
		return new NonExistentElement(this);
	}
}