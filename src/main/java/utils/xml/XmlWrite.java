package utils.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Node;

import com.google.common.base.Preconditions;

import utils.io.IOUtils;


/**
 * DOM 노드를 XML 텍스트로 출력하는 작업을 설정하고 수행하는 builder 형태의 클래스.
 * <p>
 * {@link #create()}로 객체를 생성한 뒤 {@code from(...)}으로 출력할 노드를, {@code to(...)}로
 * 출력 대상을 지정하고 {@link #run()}을 호출하여 출력한다.
 * <b>주의:</b> {@link #run()}은 출력이 끝나면(실패한 경우 포함) 지정된 출력 {@link Writer}를 항상 닫는다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class XmlWrite implements Runnable {
	private Source m_source;
	private Writer m_writer;
	private int m_indent = 0;
	private boolean m_omitDecl = false;
	
	/**
	 * 기본 설정(들여쓰기 0, XML 선언 포함)의 {@link XmlWrite} 객체를 생성한다.
	 *
	 * @return	생성된 객체.
	 */
	public static XmlWrite create() {
		return new XmlWrite();
	}
	
	private XmlWrite() { }
	
	/**
	 * 설정된 들여쓰기 크기를 반환한다.
	 *
	 * @return	들여쓰기 크기. 0이면 들여쓰기를 하지 않는다.
	 */
	public int indent() {
		return m_indent;
	}
	
	/**
	 * 들여쓰기 크기를 설정한다. 0보다 크면 들여쓰기를 적용하여 출력한다.
	 *
	 * @param size	들여쓰기 크기.
	 * @return	본 객체.
	 * @throws IllegalArgumentException	{@code size}가 음수인 경우.
	 */
	public XmlWrite indent(int size) {
		Preconditions.checkArgument(size >= 0);
		m_indent = size;
		
		return this;
	}
	
	/**
	 * XML 선언({@code <?xml ...?>})을 생략하도록 설정되었는지 여부를 반환한다.
	 *
	 * @return	XML 선언을 생략하는 경우 {@code true}.
	 */
	public boolean omitXmlDeclaration() {
		return m_omitDecl;
	}
	
	/**
	 * XML 선언({@code <?xml ...?>})의 생략 여부를 설정한다.
	 *
	 * @param flag	XML 선언을 생략하려면 {@code true}.
	 * @return	본 객체.
	 */
	public XmlWrite omitXmlDeclaration(boolean flag) {
		m_omitDecl = flag;
		return this;
	}
	
	/**
	 * 출력할 DOM 노드를 설정한다.
	 *
	 * @param node	출력할 노드.
	 * @return	본 객체.
	 */
	public XmlWrite from(Node node) {
		m_source = new DOMSource(node);
		return this;
	}
	
	/**
	 * 출력할 노드를 {@link FluentNode}로 설정한다.
	 *
	 * @param node	출력할 노드.
	 * @return	본 객체.
	 * @throws NullPointerException	{@code node}가 {@code null}인 경우.
	 */
	public XmlWrite from(FluentNode node) {
		m_source = new DOMSource(node.asNode());
		return this;
	}
	
	/**
	 * 출력 대상 {@link Writer}를 설정한다. 이 writer는 {@link #run()} 종료 시 닫힌다.
	 *
	 * @param writer	출력 대상 writer.
	 * @return	본 객체.
	 */
	public XmlWrite to(Writer writer) {
		m_writer = writer;
		return this;
	}
	
	/**
	 * 출력 대상 {@link OutputStream}을 설정한다.
	 * <p>
	 * 스트림은 플랫폼 기본 문자셋의 {@link OutputStreamWriter}로 감싸지며,
	 * {@link #run()} 종료 시 이 writer와 함께 주어진 스트림도 닫힌다.
	 *
	 * @param os	출력 대상 스트림.
	 * @return	본 객체.
	 * @throws IOException	실제로는 발생하지 않는다.
	 */
	public XmlWrite to(OutputStream os) throws IOException {
		m_writer = new OutputStreamWriter(os);
		return this;
	}
	
	/**
	 * 출력 대상 파일을 설정한다.
	 * <p>
	 * 이 메소드 호출 시점에 플랫폼 기본 문자셋의 {@link FileWriter}로 파일을 열며(기존 내용은 지워진다),
	 * 열린 writer는 {@link #run()} 종료 시 닫힌다.
	 *
	 * @param file	출력 대상 파일.
	 * @return	본 객체.
	 * @throws IOException	파일을 열 수 없는 경우.
	 */
	public XmlWrite to(File file) throws IOException {
		m_writer = new FileWriter(file);
		return this;
	}
	
	public void run() {
		StreamResult result = new StreamResult(m_writer);
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, m_omitDecl ? "yes" : "no");
			transformer.setOutputProperty(OutputKeys.INDENT, m_indent > 0 ? "yes" : "no");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", ""+m_indent);
			transformer.transform(m_source, result);
		}
		catch ( Exception e ) {
			throw new RuntimeException("fails to create XML", e);
		}
		finally {
			IOUtils.closeQuietly(m_writer);
		}
	}
}