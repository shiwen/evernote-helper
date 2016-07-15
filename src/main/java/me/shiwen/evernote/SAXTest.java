package me.shiwen.evernote;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SAXTest {
    public static void main(String... args) {
        try {
            String s = "";
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            UserHandler userhandler = new UserHandler();
            saxParser.parse(new ByteArrayInputStream(s.getBytes()), userhandler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class UserHandler extends DefaultHandler {
    private Stack<String> stack = new Stack<>();
    private StringBuilder sb = new StringBuilder();
    private List<String> lines = new ArrayList<>();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        stack.push(qName);
        String line;
        switch (qName) {
            case "div":
            case "p":
            case "pre":
            case "blockquote":
                line = sb.toString();
                sb = new StringBuilder();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                for (String s : lines) {
                    System.out.println("<" + qName + ">" + s + "</" + qName + ">");
                }
                lines.clear();
                break;
            case "br":
                break;
            default:
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String line;
        switch (qName) {
            case "div":
            case "p":
            case "pre":
            case "blockquote":
                line = sb.toString();
                sb = new StringBuilder();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                for (String s : lines) {
                    System.out.println("<" + qName + ">" + s + "</" + qName + ">");
                }
                lines.clear();
                break;
            case "br":
                line = sb.toString();
                sb = new StringBuilder();
                lines.add(line.isEmpty() ? "<br/>" : line);
                break;
        }

        String tag;
        do {
            tag = stack.pop();
        } while (!tag.equalsIgnoreCase(qName));
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        System.err.println(new String(ch, start, length));
        sb.append(new String(ch, start, length));
    }
}
