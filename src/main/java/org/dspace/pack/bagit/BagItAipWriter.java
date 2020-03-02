package org.dspace.pack.bagit;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.Charsets;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.duraspace.bagit.BagItDigest;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.BagSerializer;
import org.duraspace.bagit.BagWriter;
import org.duraspace.bagit.SerializationSupport;

/**
 * Handle the actual copying and writing of files into a Bag
 *
 * @author mikejritter
 * @since 2020-03-02
 */
public class BagItAipWriter {

    private final String DATA_DIR = "data";
    private final String LOGO_FILE = "logo";
    private final String METADATA_XML = "metadata.xml";
    private final String bagProfile = "/profiles/beyondtherepository.json";
    private final BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    private final Map<File, String> checksums = new HashMap<>();

    /**
     * The directory to which the BagIt bag will be written
     */
    private final File directory;

    /**
     * The serialization format for the bag
     */
    private final String archFmt;

    /**
     * A logo for the package, or null if one does not exist
     */
    private final Bitstream logo;

    /**
     * A Mapping of filenames to the properties
     * todo: this is a little weird, maybe encapsulate properties some other way
     */
    private final Map<String, Properties> fileProperties;

    /**
     * Key-Value xml properties
     */
    private final List<XmlElement> metadata;

    /**
     * A map of Bitstreams to package with the AIP
     */
    private final List<BagBitstream> bitstreams;

    public BagItAipWriter(File directory, String archFmt, Bitstream logo, Map<String, Properties> fileProperties,
                          List<XmlElement> metadata, List<BagBitstream> bitstreams) {
        this.logo = logo;
        this.archFmt = checkNotNull(archFmt);
        this.directory = checkNotNull(directory);
        this.metadata = checkNotNull(metadata);
        this.fileProperties = checkNotNull(fileProperties);
        this.bitstreams = bitstreams != null ? bitstreams : Collections.emptyList();
    }

    public File packageAip() throws IOException, SQLException, AuthorizeException {
        // setup BagWriter and the like
        final BagItDigest digest = BagItDigest.MD5;
        final MessageDigest messageDigest = digest.messageDigest();
        final Path dataDir = directory.toPath().resolve(DATA_DIR);

        // todo: this might fail, might want to push to BagProfile
        final URL url = this.getClass().getResource(bagProfile);
        final BagProfile profile = new BagProfile(url.openStream());

        // todo - on bag init add: tag files, bag metadata, track size written
        final BagWriter bag = new BagWriter(directory, Collections.singleton(digest.bagitName()));

        // Write the base object properties
        for (String filename : fileProperties.keySet()) {
            final Path objfile = dataDir.resolve(filename);
            try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
                 final DigestOutputStream objDigest = new DigestOutputStream(objOS, messageDigest)) {
                final Properties properties = fileProperties.get(filename);
                for (String property : properties.stringPropertyNames()) {
                    final String value = properties.getProperty(property);
                    final String line = property + "  " + value + "\n";
                    objDigest.write(line.getBytes());
                }
            }
            final String objFileDigest = Utils.toHex(messageDigest.digest());
            checksums.put(objfile.toFile(), objFileDigest);
        }

        // then metadata
        messageDigest.reset();
        final Path manifestXml = dataDir.resolve(METADATA_XML);
        final String xmlDigest = writeXmlMetadata(metadata, manifestXml, messageDigest);
        checksums.put(manifestXml.toFile(), xmlDigest);

        // write any bitstreams
        for (BagBitstream bagBitstream : bitstreams) {
            final String bundle = bagBitstream.getBundle();
            if (bagBitstream.getFetchUrl() != null) {
                // todo: handle fetch
            } else {
                // bitstream metadata
                final Bitstream bitstream = bagBitstream.getBitstream();
                final String seqId = String.valueOf(bitstream.getSequenceID());
                final Path bitstreamXml = dataDir.resolve(bundle).resolve(seqId + "-metadata.xml");
                writeXmlMetadata(bagBitstream.getXml(), bitstreamXml, messageDigest);

                // bitstream... stream
                messageDigest.reset();
                final Path dataFile = dataDir.resolve(bundle + seqId);
                final InputStream is = bitstreamService.retrieve(Curator.curationContext(), bitstream);

                try (OutputStream fout = Files.newOutputStream(dataFile);
                     DigestOutputStream dout = new DigestOutputStream(fout, messageDigest)) {
                    Utils.copy(is, dout);
                }

                final String fileChecksum = Utils.toHex(messageDigest.digest());
                checksums.put(dataFile.toFile(), fileChecksum);
            }
        }

        // also add logo if it exists
        if (logo != null) {
            final InputStream logoIS = bitstreamService.retrieve(Curator.curationContext(), logo);
            final Path logoPath = dataDir.resolve(LOGO_FILE);
            try (OutputStream os = Files.newOutputStream(logoPath);
                 DigestOutputStream dos = new DigestOutputStream(os, messageDigest)) {
                messageDigest.reset();
                Utils.copy(logoIS, dos);
                checksums.put(logoPath.toFile(), Utils.toHex(messageDigest.digest()));
            }
        }

        // Finalize the Bag (write + serialize)
        bag.registerChecksums(digest.bagitName(), checksums);
        bag.write();

        BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
        Path serializedBag = serializer.serialize(directory.toPath());
        delete(directory);

        return serializedBag.toFile();
    }

    private void delete(File directory) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                delete(file);
            } else {
                file.delete();
            }
        }

        directory.delete();
    }

    private String writeXmlMetadata(final List<XmlElement> elements, final Path manifestXml,
                                    final MessageDigest messageDigest) throws IOException {
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(xmlOut, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");

            for (XmlElement element : elements) {
                xmlWriter.writeStartElement("value");
                for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
                    xmlWriter.writeAttribute(attribute.getKey(), attribute.getValue());
                }
                xmlWriter.writeCharacters(element.getBody());
                xmlWriter.writeEndElement();
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

    /**
     * Write the metadata.xml file
     *
     * This is being copied a few times while code is being reorganized. No need to attempt DRY before we know how
     * things will look
     *
     * @param metadata The map of metadata key/value pairs to write
     * @param manifestXml the Path of the metadata.xml file to write
     * @param messageDigest the MessageDigest for tracking the digest of the written stream
     * @return the checksum of the manifest.xml
     * @throws IOException if there's any exception
    private String writeXmlMetadata(final List<XmlElement> metadata, final Path manifestXml,
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
     */

}
