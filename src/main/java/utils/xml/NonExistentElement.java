package utils.xml;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
		return null;
	}

	@Override
	public Node asNode() {
		return null;
	}

	@Override
	public FluentElement parent() {
		return m_parent;
	}
	
	public FluentElement with(Consumer<FluentElement> consumer) {
		return this;
	}

	@Override
	public FluentElement firstChild() {
		return new NonExistentElement(this);
	}

	@Override
	public FluentElement firstChild(String name) {
		return new NonExistentElement(this);
	}

	@Override
	public FluentElement tryFirstChild(String name) {
		return new NonExistentElement(this);
	}

	@Override
	public FluentElement tryFirstChild() {
		return new NonExistentElement(this);
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
	public FluentElement appendChild(String childName) {
		return new NonExistentElement(this);
	}

	@Override
	public Optional<String> attr(String name) {
		return Optional.empty();
	}
	@Override
	public int attrInt(String name, int defValue) {
		return defValue;
	}
	@Override
	public long attrLong(String name, long defValue) {
		return defValue;
	}

	@Override
	public double attrDouble(String name, double defValue) {
		return defValue;
	}

	@Override
	public FluentElement attr(String name, Object value) {
		return this;
	}

	@Override
	public Optional<String> text() {
		return Optional.empty();
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
	public FluentElement withText(Consumer<String> consumer) {
		return this;
	}
	
	@Override
	public FluentElement withInt(Consumer<Integer> consumer) {
		return this;
	}

	@Override
	public FluentElement withLong(Consumer<Long> consumer) {
		return this;
	}

	@Override
	public FluentElement withDouble(Consumer<Double> consumer) {
		return this;
	}

	@Override
	public FluentElement withBoolean(Consumer<Boolean> consumer) {
		return this;
	}

	@Override
	public <T> Optional<T> map(Function<FluentElement, T> func) {
		return Optional.empty();
	}
}