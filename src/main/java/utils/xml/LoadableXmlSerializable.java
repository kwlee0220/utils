package utils.xml;

/**
 * 
 * @author Kang-Woo Lee (ETRI)
 */
public interface LoadableXmlSerializable extends XmlSerializable {
	public void loadFromXml(FluentElement topElm);
}
