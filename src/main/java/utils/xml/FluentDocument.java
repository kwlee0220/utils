package utils.xml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;


/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class FluentDocument implements FluentNode {
	private Document m_doc;
	
	public static FluentDocument from(InputStream is) throws SAXException, IOException {
		return new FluentDocument(XmlUtils.parse(is));
	}
	
	public static FluentDocument from(File file) throws SAXException, IOException {
		return new FluentDocument(XmlUtils.parse(file));
	}
	
	public static FluentDocument from(String xmlString) throws SAXException {
		return new FluentDocument(XmlUtils.parse(xmlString));
	}
	
	public static FluentDocument newInstance() {
		return new FluentDocument(XmlUtils.createDocument());
//		Document doc = XmlUtils.createDocument();
//		doc.appendChild(doc.createElementNS(null, rootElmName));
//		return new FluentDocument(doc);
	}
	
	public FluentDocument(Document doc) {
		m_doc = doc;
	}

	@Override
	public Node asNode() {
		return m_doc;
	}
	
	public FluentElementImpl getDocumentElement() {
		return new FluentElementImpl(m_doc.getDocumentElement());
	}

	@Override
	public FluentElement appendChild(String childName) {
		Element elm = m_doc.createElementNS(null, childName);
		m_doc.appendChild(elm);
		
		return FluentElementImpl.of(elm);
	}
}
