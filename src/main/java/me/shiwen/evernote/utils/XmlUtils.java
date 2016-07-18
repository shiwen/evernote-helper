package me.shiwen.evernote.utils;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class XmlUtils {
    private static final String INDENT_NUMBER = "indent-number";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String YES = "yes";

    private static final DocumentBuilder DOCUMENT_BUILDER;

    static {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(LOAD_EXTERNAL_DTD, false);
            DOCUMENT_BUILDER = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Document getDocument(String s) throws ParserConfigurationException, IOException, SAXException {
        DOCUMENT_BUILDER.reset();
        return DOCUMENT_BUILDER.parse(new ByteArrayInputStream(s.getBytes()));
    }

    public static void transform(Document d) {
        for (Node node : getNodeArray(d.getElementsByTagName("div"))) {
            flatten(node);
        }
        for (Node node : getNodeArray(d.getElementsByTagName("p"))) {
            flatten(node);
        }
    }

    public static void flatten(Node node) {
        Node parentNode = node.getParentNode();
        Document doc = node.getOwnerDocument();
        if (node.hasChildNodes()) {
            Node[] children = getNodeArray(node.getChildNodes());
            StringBuilder sb = new StringBuilder();
            for (Node child : children) {
                if (child.getNodeType() == Node.TEXT_NODE) {
                    if (sb.length() != 0) {
                        sb.append(" ");
                    }
                    sb.append(child.getTextContent());
                    node.removeChild(child);
                } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                    switch (child.getNodeName()) {
                        case "br":
                            if (sb.length() != 0) {
                                insertNewNode(doc, parentNode, node, sb);
                                node.removeChild(child);
                            } else {
                                insertNewNode(doc, parentNode, node, child);
                            }
                            break;
                        case "div":
                        case "p":
                        case "pre":
                        case "blockquote":
                            if (sb.length() != 0) {
                                insertNewNode(doc, parentNode, node, sb);
                            }
                            parentNode.insertBefore(child, node);
                            break;
                    }
                }
            }
            if (sb.length() != 0) {
                insertNewNode(doc, parentNode, node, sb);
            }
            if (!node.hasChildNodes()) {
                parentNode.removeChild(node);
            }
        }
    }

    private static void insertNewNode(Document doc, Node parentNode, Node node, StringBuilder sb) {
        Text text = doc.createTextNode(sb.toString().replaceAll("\\s{2,}", " "));
        sb.setLength(0);
        Element newNode = doc.createElement(node.getNodeName());
        newNode.appendChild(text);
        parentNode.insertBefore(newNode, node);
    }

    private static void insertNewNode(Document doc, Node parentNode, Node node, Node br) {
        Element newNode = doc.createElement(node.getNodeName());
        newNode.appendChild(br);
        parentNode.insertBefore(newNode, node);
    }

    private static Node[] getNodeArray(NodeList nodeList) {
        int length = nodeList.getLength();
        Node[] array = new Node[length];
        for (int i = 0; i < length; i++) {
            array[i] = nodeList.item(i);
        }
        return array;
    }

    public static String prettyFormat(String xml) throws TransformerException {
        Source source = new StreamSource(new StringReader(xml));
        StringWriter writer = new StringWriter();
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(INDENT_NUMBER, 2);
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, YES);
        transformer.transform(source, new StreamResult(writer));
        return writer.toString();
    }

    public static String format(String xml, boolean indent) throws IOException, SAXException,
            ParserConfigurationException, TransformerException {
        return format(getDocument(xml), indent);
    }

    public static String format(Document document, boolean indent) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(INDENT_NUMBER, 2);
        Transformer transformer = tf.newTransformer();
        if (indent) {
            transformer.setOutputProperty(OutputKeys.INDENT, YES);
        }
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, YES);
        DocumentType documentType = document.getDoctype();
        if (documentType != null) {
            String publicId = documentType.getPublicId();
            if (publicId != null) {
                transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, publicId);
            }
            String systemId = documentType.getSystemId();
            if (systemId != null) {
                transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, systemId);
            }
        }
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        String result = writer.toString();
        if (!indent) {
            result = result.replaceAll(">\\s+<", "><");
        }
        return result;
    }
}
