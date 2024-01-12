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
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public class XmlWrite implements Runnable {
	private Source m_source;
	private Writer m_writer;
	private int m_indent = 0;
	private boolean m_omitDecl = false;
	
	public static XmlWrite create() {
		return new XmlWrite();
	}
	
	private XmlWrite() { }
	
	public int indent() {
		return m_indent;
	}
	
	public XmlWrite indent(int size) {
		Preconditions.checkArgument(size >= 0);
		m_indent = size;
		
		return this;
	}
	
	public boolean omitXmlDeclaration() {
		return m_omitDecl;
	}
	
	public XmlWrite omitXmlDeclaration(boolean flag) {
		m_omitDecl = flag;
		return this;
	}
	
	public XmlWrite from(Node node) {
		m_source = new DOMSource(node);
		return this;
	}
	
	public XmlWrite from(FluentNode node) {
		m_source = new DOMSource(node.asNode());
		return this;
	}
	
	public XmlWrite to(Writer writer) {
		m_writer = writer;
		return this;
	}
	
	public XmlWrite to(OutputStream os) throws IOException {
		m_writer = new OutputStreamWriter(os);
		return this;
	}
	
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