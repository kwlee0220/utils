package utils.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.w3c.dom.Node;

import utils.io.IOUtils;



/**
 * DOM {@link Node}를 감싸 자식 element 추가와 XML 출력을 fluent 방식으로 제공하는 인터페이스.
 * <p>
 * 문서 노드는 {@link FluentDocument}, element 노드는 {@link FluentElement}가 구현한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FluentNode {
	/**
	 * 본 객체가 감싸는 DOM {@link Node}를 반환한다.
	 *
	 * @return	DOM Node 객체.
	 */
	public Node asNode();
	
	/**
	 * 주어진 이름의 element를 생성하여 본 노드의 마지막 자식으로 추가한다.
	 * <p>
	 * element는 namespace 없이({@code createElementNS(null, childName)}) 생성된다.
	 *
	 * @param childName	추가할 자식 element의 이름.
	 * @return	추가된 자식 element.
	 */
	public FluentElement appendChild(String childName);
	
	/**
	 * 주어진 이름의 자식 element를 추가하고, {@code child}의
	 * {@link XmlSerializable#toXml(FluentElement)}를 호출하여 그 내용을 채운다.
	 *
	 * @param childElmName	추가할 자식 element의 이름.
	 * @param child			자식 element에 기록할 객체.
	 * @return	추가된 자식 element.
	 */
	public default FluentElement appendChild(String childElmName, XmlSerializable child) {
		FluentElement topElm = appendChild(childElmName);
		child.toXml(topElm);
		
		return topElm;
	}
	
	/**
	 * 주어진 이름의 자식 element를 추가하고 {@code class} 속성에 {@code child}의 클래스 이름을 기록한다.
	 * <p>
	 * {@code child}가 {@link XmlSerializable}이면 {@link XmlSerializable#toXml(FluentElement)}로
	 * 그 내용을 채우고, 그렇지 않으면 {@code class} 속성만 기록된다.
	 * {@code child}가 {@code null}이면 {@link NullPointerException}이 발생한다.
	 *
	 * @param childElmName	추가할 자식 element의 이름.
	 * @param child			자식 element에 기록할 객체.
	 * @return	추가된 자식 element.
	 */
	public default FluentElement appendTypedChild(String childElmName, Object child) {
		FluentElement topElm = appendChild(childElmName)
									.attr("class", child.getClass().getName());
		if ( child instanceof XmlSerializable ) {
			((XmlSerializable)child).toXml(topElm);
		}
		
		return topElm;
	}
	
	/**
	 * 본 노드를 XML 텍스트로 변환하여 주어진 writer에 출력한다.
	 * <p>
	 * <b>주의:</b> 출력이 끝나면(실패한 경우에도) {@code writer}가 닫힌다.
	 *
	 * @param omitXmlDecl	XML 선언({@code <?xml ...?>})을 생략할지 여부.
	 * @param indent		들여쓰기 칸 수. 0이면 들여쓰기를 하지 않는다.
	 * @param writer		출력 대상 writer.
	 * @throws IllegalArgumentException	{@code indent}가 음수인 경우.
	 * @throws RuntimeException	XML 변환에 실패한 경우.
	 */
	public default void toXml(boolean omitXmlDecl, int indent, Writer writer) {
		XmlWrite.create()
				.from(asNode())
				.to(writer)
				.indent(indent)
				.omitXmlDeclaration(omitXmlDecl)
				.run();
	}
	
	/**
	 * 본 노드를 XML 문자열로 변환한다.
	 *
	 * @param omitXmlDecl	XML 선언({@code <?xml ...?>})을 생략할지 여부.
	 * @param indent		들여쓰기 칸 수. 0이면 들여쓰기를 하지 않는다.
	 * @return	XML 문자열.
	 * @throws IllegalArgumentException	{@code indent}가 음수인 경우.
	 * @throws RuntimeException	XML 변환에 실패한 경우.
	 */
	public default String toXmlString(boolean omitXmlDecl, int indent) {
		StringWriter writer = new StringWriter();
		try {
			toXml(omitXmlDecl, indent, writer);
			return writer.toString();
		}
		finally {
			IOUtils.closeQuietly(writer);
		}
	}
	
	/**
	 * 본 노드를 XML 텍스트로 변환하여 주어진 파일에 기록한다.
	 * <p>
	 * 파일이 이미 존재하면 덮어쓴다. 파일은 {@link FileWriter}로 열리므로 플랫폼 기본 문자 인코딩으로 기록된다.
	 *
	 * @param xmlFile		기록할 대상 파일.
	 * @param omitXmlDecl	XML 선언({@code <?xml ...?>})을 생략할지 여부.
	 * @param indent		들여쓰기 칸 수. 0이면 들여쓰기를 하지 않는다.
	 * @throws IOException	파일을 열지 못한 경우.
	 * @throws IllegalArgumentException	{@code indent}가 음수인 경우.
	 * @throws RuntimeException	XML 변환에 실패한 경우.
	 */
	public default void toXmlFile(File xmlFile, boolean omitXmlDecl, int indent) throws IOException {
		FileWriter writer = new FileWriter(xmlFile);
		try {
			toXml(omitXmlDecl, indent, writer);
		}
		finally {
			IOUtils.closeQuietly(writer);
		}
	}
}
