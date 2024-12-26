package io.opentakserver.opentakicu.parser;

import android.util.Log;

import com.ctc.wstx.shaded.msv_core.verifier.jarv.Const;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import io.opentakserver.opentakicu.utils.Constants;

public class CoT {
    public static final String TAG = Constants.TAG_PREFIX + CoT.class.getSimpleName();
    private final String CoTString;
    private Document CoTXML;

    private final XPath xPath = XPathFactory.newInstance().newXPath();

    public CoT(final String cot) {
        this.CoTString = cot;

        parseXML();
    }

    public String getType() {
        String path = "/event/@type";
        String result = "x-x-x-x";
        try {
            NodeList nodes = (NodeList) xPath.evaluate(path, CoTXML, XPathConstants.NODESET);

            // The XML should always have a result but just in case
            if(nodes.getLength() > 0) {
                result = nodes.item(0).getNodeValue();
            }
        } catch(XPathExpressionException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to parse CoT Type: " + ex.getMessage() + " - " + sw);
        } finally {
            return result;
        }
    }

    public String getCallsign() {
        String path = "/event/detail/contact/@callsign";
        String result = "RED 5";
        try {
            NodeList nodes = (NodeList) xPath.evaluate(path, CoTXML, XPathConstants.NODESET);

            // The XML should always have a type but just in case
            if(nodes.getLength() > 0) {
                result = nodes.item(0).getNodeValue();
            }
        } catch(XPathExpressionException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to parse CoT Callsign: " + ex.getMessage() + " - " + sw);
        } finally {
            return result;
        }
    }

    public String getDestCallsign() {
        String result = "RED 5";
        String path = "/event/detail/marti/dest/@callsign";
        try {
            NodeList nodes = (NodeList) xPath.evaluate(path, CoTXML, XPathConstants.NODESET);

            // The XML should always have a type but just in case
            if(nodes.getLength() > 0) {
                result = nodes.item(0).getNodeValue();
            }
        } catch(XPathExpressionException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to parse CoT Callsign: " + ex.getMessage() + " - " + sw);
        }
        return result;
    }

    public void parseXML() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            CoTXML = builder.parse(new ByteArrayInputStream(CoTString.getBytes()));
        } catch(ParserConfigurationException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to load document builder: " + ex.getMessage() + " - " + sw);
        } catch (IOException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to parse document (IOException): " + ex.getMessage() + " - " + sw);
        } catch (SAXException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unable to parse document (SAXException): " + ex.getMessage() + " - " + sw);
        }
    }

    public String toString() { return CoTString; }
}
