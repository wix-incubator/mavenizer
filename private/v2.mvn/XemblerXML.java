package tools.jvm.v2.mvn;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.sun.org.apache.xml.internal.serializer.OutputPropertiesFactory;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

public class XemblerXML implements XML {

    /**
     * XML.
     */
    @Delegate(excludes = {WithXPath.class})
    private final XML origin;

    /**
     * XPath Query mapper
     */
    @Getter
    private final XPathTypo xpathTypo;


    @SuppressWarnings("Guava")
    private final Supplier<String> xml;

    /**
     * Ctor
     *
     * @param node original node
     */
    public XemblerXML(Node node, XPathTypo typo) {
        this(new XMLDocument(node).merge(Pom.XPATH_CONTEXT));
    }

    /**
     * Ctor.
     *
     * @param input xml
     */
    public XemblerXML(final XML input) {
        this(input, null);
    }

    /**
     * Ctor.
     *
     * @param input original xml
     */
    @SuppressWarnings("Guava")
    public XemblerXML(final XML input, XPathTypo typo) {
        XML xml = input;
        XPathTypo xPathQuery = typo;
        Supplier<String> asString = Suppliers.memoize(
                () -> getString(input)
        );

        if (xml instanceof XemblerXML) {
            xml = ((XemblerXML) input).origin;
            xPathQuery = ((XemblerXML) input).xpathTypo;
            asString = ((XemblerXML) input).xml;
        }

        this.origin = xml;
        this.xpathTypo = xPathQuery != null ? xPathQuery : newXPathQuery(xml);
        this.xml = asString;
    }

    private static XPathTypo newXPathQuery(XML orig) {
        XPathTypo queryMap = s -> s;
        final List<String> namespaces = orig.xpath(Pom.NAMESPACE_XPATH);
        if (namespaces.contains(Pom.POM_NS_URI)) {
            queryMap = new XPathTypo.Prefix(Pom.POM_NS);
        }
        return queryMap;
    }

    @Override
    public List<String> xpath(String query) {
        return origin.xpath(xpathTypo.apply(query));
    }

    @Override
    public List<XML> nodes(String query) {
        return origin.nodes(xpathTypo.apply(query));
    }

    @Override
    public String toString() {
        return getString(this.origin);
    }

    @SuppressWarnings("unused")
    public interface WithXPath {
        List<String> xpath(String query);

        List<XML> nodes(String query);
    }


    private static String getString(XML xml) {
        final Node node = xml.node();
        trimWhitespace(node);
        return prettyPrint(node);
    }

    private static void trimWhitespace(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); ++i) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                System.out.println("");
            }
            if (child.getNodeType() == Node.TEXT_NODE) {
                child.setTextContent(child.getTextContent().trim());
            }
            trimWhitespace(child);
        }
    }

    private static String prettyPrint(Node node) {
        final StringWriter writer = new StringWriter();
        try {
            final Transformer trans = TFACTORY.newTransformer();
            trans.setOutputProperty(OutputKeys.INDENT, "yes");
            trans.setOutputProperty(OutputKeys.VERSION, "1.0");
            trans.setOutputProperty(OutputPropertiesFactory.S_KEY_INDENT_AMOUNT, "2");
            if (!(node instanceof Document)) {
                trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            }
            trans.transform(new DOMSource(node), new StreamResult(writer));
        } catch (TransformerException ex) {
            throw new IllegalStateException(ex);
        }
        return writer.toString();
    }

    private static final TransformerFactory TFACTORY =
            TransformerFactory.newInstance();
}
