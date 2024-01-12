package utils.xml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.w3c.dom.Node;

import utils.io.IOUtils;



/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface FluentNode {
	public Node asNode();
	
	public FluentElement appendChild(String childName);
	
	public default FluentElement appendChild(String childElmName, XmlSerializable child) {
		FluentElement topElm = appendChild(childElmName);
		child.toXml(topElm);
		
		return topElm;
	}
	
	public default FluentElement appendTypedChild(String childElmName, Object child) {
		FluentElement topElm = appendChild(childElmName)
									.attr("class", child.getClass().getName());
		if ( child instanceof XmlSerializable ) {
			((XmlSerializable)child).toXml(topElm);
		}
		
		return topElm;
	}
	
	public default void toXml(boolean omitXmlDecl, int indent, Writer writer) {
		XmlWrite.create()
				.from(asNode())
				.to(writer)
				.indent(indent)
				.omitXmlDeclaration(omitXmlDecl)
				.run();
	}
	
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
