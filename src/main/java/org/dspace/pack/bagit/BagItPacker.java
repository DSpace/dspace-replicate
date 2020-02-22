package org.dspace.pack.bagit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.Charsets;
import org.dspace.core.Utils;
import org.dspace.pack.Packer;

public abstract class BagItPacker implements Packer {

    public void copy(InputStream is, OutputStream os) {
    }

    public String writeMetadata(final Map<String, String> metadata, final Path manifestXml,
                              final MessageDigest messageDigest) throws IOException {
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(xmlOut, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                if (key != null && value != null) {
                    xmlWriter.writeStartElement("value");
                    xmlWriter.writeAttribute("name", key);
                    xmlWriter.writeCharacters(value);
                    xmlWriter.writeEndElement();
                }
            }
            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final String digest = Utils.toHex(messageDigest.digest());
        messageDigest.reset();

        return digest;
    }

    public static String writeXmlMeta(final Map<String, String> metadata, final Path manifestXml,
                                      final MessageDigest messageDigest) throws IOException {
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(xmlOut, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                if (key != null && value != null) {
                    xmlWriter.writeStartElement("value");
                    xmlWriter.writeAttribute("name", key);
                    xmlWriter.writeCharacters(value);
                    xmlWriter.writeEndElement();
                }
            }
            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final String digest = Utils.toHex(messageDigest.digest());
        messageDigest.reset();

        return digest;
    }

}
