package tools.jvm.mvn;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.jcabi.xml.XPathContext;
import com.sun.org.apache.xml.internal.serializer.OutputPropertiesFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.cactoos.Input;
import org.cactoos.Text;
import org.cactoos.text.UncheckedText;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

public interface Pom {

    String NAMESPACE_XPATH = "/*/namespace::*[name()='']";
    String POM_NS_URI = "http://maven.apache.org/POM/4.0.0";
    String POM_NS = "pom";


    @SuppressWarnings("UnstableApiUsage")
    static Pom load(Path path) {
        return new Cached(new Standard(Files.asByteSource(path.toFile())));
    }

    /**
     * Global xpath context.
     */
    XPathContext XPATH_CONTEXT = new XPathContext()
            .add(POM_NS, POM_NS_URI)
            .add("xe", "http://www.w3.org/1999/xhtml");


    /**
     * Pom as XMP
     * @return xml
     */
    XML xml();


    /**
     * Group id
     * @return str
     */
    default String groupId() {
        final XML xml = xml();
        List<String> text = xml.xpath("/project/groupId/text()");
        if (text.isEmpty()) {
            text = xml.xpath("/project/parent/groupId/text()");
        }
        return text.get(0);
    }

    /**
     * Artifact id
     * @return str
     */
    default String artifactId() {
        return xml().xpath("/project/artifactId/text()").get(0);
    }

    /**
     * Version
     * @return str
     */
    default String version() {
        return xml().xpath("/project/version/text()").get(0);
    }

    /**
     * Pretty print.
     * @return str
     */
    default String toPrettyPrint() {
        return new PrettyPrintXml(xml()).asString();
    }

    /**
     * Loaded pom xml.
     */
    @AllArgsConstructor
    class Standard implements Pom {

        /**
         * Ctor.
         * @param src byte source
         */
        public Standard(ByteSource src) {
            this(src::openStream);
        }

        /**
         * XML.
         */
        private final Input xml;


        @Override
        @SneakyThrows
        public XML xml() {
            try (InputStream src = xml.stream()) {
                return new SanitizedXML(
                        new XMLDocument(src).merge(XPATH_CONTEXT)
                );
            }
        }

        @Override
        public String toString() {
            return xml().toString();
        }
    }

    @SuppressWarnings("Guava")
    class Cached implements Pom {

        public Cached(Pom pom) {
            this((Supplier<XML>) pom::xml);
        }

        public Cached(Supplier<XML> xml) {
            this.xml = Suppliers.memoize(xml);
        }

        /**
         * XML.
         */
        private final Supplier<XML> xml;

        @Override
        public XML xml() {
            return xml.get();
        }
    }


    /**
     * Sanitized XML with auto prefixing xpath queries.
     */
    class SanitizedXML implements XML {

        /**
         * XML.
         */
        @Delegate(excludes = {WithXPath.class})
        private final XML origin;

        /**
         * XPath Query mapper
         */
        @Getter
        private final XemblerAugment.XPathQuery xpathQuery;


        @SuppressWarnings("Guava")
        private final Supplier<String> xml;

        /**
         * Ctor
         * @param node original node
         */
        public SanitizedXML(Node node) {
            this(new XMLDocument(node).merge(Pom.XPATH_CONTEXT));
        }

        /**
         * Ctor.
         * @param input original xml
         */
        @SuppressWarnings("Guava")
        public SanitizedXML(XML input) {
            XML xml = input;
            XemblerAugment.XPathQuery xPathQuery = null;
            Supplier<String> asString = Suppliers.memoize(
                    () -> new PrettyPrintXml(input).asString()
            );

            if (xml instanceof SanitizedXML) {
                xml = ((SanitizedXML) input).origin;
                xPathQuery = ((SanitizedXML) input).xpathQuery;
                asString = ((SanitizedXML) input).xml;
            }

            this.origin = xml;
            this.xpathQuery = xPathQuery != null ? xPathQuery : newXPathQuery(xml);
            this.xml = asString;
        }


        private static XemblerAugment.XPathQuery newXPathQuery(XML orig) {
            XemblerAugment.XPathQuery queryMap = s -> s;
            final List<String> namespaces = orig.xpath(NAMESPACE_XPATH);
            if (namespaces.contains(POM_NS_URI)) {
                queryMap = new XemblerAugment.XPathQueryPref(POM_NS);
            }
            return queryMap;
        }

        @Override
        public List<String> xpath(String query) {
            return origin.xpath(xpathQuery.apply(query));
        }

        @Override
        public List<XML> nodes(String query) {
            return origin.nodes(xpathQuery.apply(query));
        }

        @SuppressWarnings("unused")
        public interface WithXPath {
            List<String> xpath(String query);
            List<XML> nodes(String query);
        }

        @Override
        public String toString() {
            return this.xml.get();
        }
    }



    @AllArgsConstructor
    class PrettyPrintXml implements Text {

        private static final TransformerFactory TFACTORY =
                TransformerFactory.newInstance();


        private final XML input;

        @Override
        public String asString() {
            final Node node = input.node();
            trimWhitespace(node);
            return prettyPrint(node);
        }

        @Override
        public int compareTo(@SuppressWarnings("NullableProblems") Text t) {
            return new UncheckedText(t).compareTo(this);
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
    }

}
