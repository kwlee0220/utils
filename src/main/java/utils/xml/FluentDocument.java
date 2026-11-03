package utils.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * DOM {@link Document}를 감싸는 {@link FluentNode} 구현.
 * <p>
 * XML 파싱은 {@link XmlUtils}의 namespace-aware {@code DocumentBuilder}를 사용한다.
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FluentDocument implements FluentNode {
	private Document m_doc;
	
	/**
	 * 입력 스트림의 XML을 파싱하여 {@link FluentDocument}를 생성한다.
	 * <p>
	 * 입력 스트림은 닫지 않으므로 호출자가 닫아야 한다.
	 *
	 * @param is	XML을 읽을 입력 스트림.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws IOException	입력 스트림을 읽는 중 오류가 발생한 경우.
	 */
	public static FluentDocument from(InputStream is) throws SAXException, IOException {
		return new FluentDocument(XmlUtils.parse(is));
	}
	
	/**
	 * XML 파일을 파싱하여 {@link FluentDocument}를 생성한다.
	 *
	 * @param file	파싱할 XML 파일.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 * @throws IOException	파일을 읽는 중 오류가 발생한 경우.
	 */
	public static FluentDocument from(File file) throws SAXException, IOException {
		return new FluentDocument(XmlUtils.parse(file));
	}
	
	/**
	 * XML 문자열을 UTF-8로 인코딩한 뒤 파싱하여 {@link FluentDocument}를 생성한다.
	 *
	 * @param xmlString	파싱할 XML 문자열.
	 * @return	파싱된 문서.
	 * @throws SAXException	XML 파싱에 실패한 경우.
	 */
	public static FluentDocument from(String xmlString) throws SAXException {
		return new FluentDocument(XmlUtils.parse(xmlString));
	}
	
	/**
	 * 루트 element가 없는 빈 문서를 생성한다.
	 * <p>
	 * 루트 element는 {@link #appendChild(String)}로 추가한다.
	 *
	 * @return	빈 문서.
	 */
	public static FluentDocument newInstance() {
		return new FluentDocument(XmlUtils.createDocument());
//		Document doc = XmlUtils.createDocument();
//		doc.appendChild(doc.createElementNS(null, rootElmName));
//		return new FluentDocument(doc);
	}
	
	/**
	 * 주어진 DOM 문서를 감싸는 {@link FluentDocument}를 생성한다.
	 *
	 * @param doc	감쌀 DOM 문서.
	 */
	public FluentDocument(Document doc) {
		m_doc = doc;
	}

	@Override
	public Node asNode() {
		return m_doc;
	}
	
	/**
	 * 문서의 루트 element를 반환한다.
	 * <p>
	 * <b>주의:</b> 루트 element가 없는 문서(예: {@link #newInstance()} 직후)이면
	 * {@code null} element를 감싼 객체가 반환되어, 이후 접근 시 {@link NullPointerException}이 발생한다.
	 *
	 * @return	루트 element.
	 */
	public FluentElementImpl getDocumentElement() {
		return new FluentElementImpl(m_doc.getDocumentElement());
	}

	/**
	 * 주어진 이름의 element를 생성하여 문서의 루트 element로 추가한다.
	 * <p>
	 * DOM 문서는 루트 element를 하나만 가질 수 있으므로, 이미 루트 element가 있으면
	 * {@link org.w3c.dom.DOMException}이 발생한다.
	 *
	 * @param childName	루트 element의 이름.
	 * @return	추가된 루트 element.
	 */
	@Override
	public FluentElement appendChild(String childName) {
		Element elm = m_doc.createElementNS(null, childName);
		m_doc.appendChild(elm);
		
		return FluentElementImpl.of(elm);
	}
}
