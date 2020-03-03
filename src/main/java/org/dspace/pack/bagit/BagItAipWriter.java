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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.google.common.io.CountingOutputStream;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.duraspace.bagit.BagConfig;
import org.duraspace.bagit.BagItDigest;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.BagProfileConstants;
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

    private final AtomicLong successBytes = new AtomicLong();
    private final AtomicLong successFiles = new AtomicLong();
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
        // check if the Bag was already being worked on
        final Path dataDir = directory.toPath().resolve(DATA_DIR);
        if (Files.exists(dataDir)) {
            throw new IllegalStateException("Unable to create bag " + directory.toPath().getFileName() +
                                            ", data directory already exists!");
        }

        // setup the BagProfile and BagWriter
        // todo: this might fail, might want to push to BagProfile
        final URL url = this.getClass().getResource(bagProfile);
        final BagProfile profile = new BagProfile(url.openStream());

        // todo - on bag init add: tag files, bag metadata, track size written
        final BagItDigest digest = BagItDigest.MD5;
        final MessageDigest messageDigest = digest.messageDigest();
        final BagWriter bag = new BagWriter(directory, Collections.singleton(digest.bagitName()));

        // Write the base object properties
        for (String filename : fileProperties.keySet()) {
            final Path objfile = dataDir.resolve(filename);
            if (Files.notExists(objfile.getParent())) {
                Files.createDirectories(objfile.getParent());
            }

            try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
                 final CountingOutputStream countingOs = new CountingOutputStream(objOS);
                 final DigestOutputStream objDigest = new DigestOutputStream(countingOs, messageDigest)) {
                final Properties properties = fileProperties.get(filename);
                for (String property : properties.stringPropertyNames()) {
                    final String value = properties.getProperty(property);
                    final String line = property + "  " + value + "\n";
                    objDigest.write(line.getBytes());
                }

                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOs.getCount());
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
            // create the bundle directory
            final String bundle = bagBitstream.getBundle();
            final Path bsDirectory = dataDir.resolve(bundle);
            Files.createDirectories(bsDirectory);

            // write the bitstream metadata
            // todo: save checksum
            final Bitstream bitstream = bagBitstream.getBitstream();
            final String seqId = String.valueOf(bitstream.getSequenceID());
            final Path bitstreamXml = bsDirectory.resolve(seqId + "-metadata.xml");
            final String bsXmlDigest = writeXmlMetadata(bagBitstream.getXml(), bitstreamXml, messageDigest);
            checksums.put(bitstreamXml.toFile(), bsXmlDigest);

            if (bagBitstream.getFetchUrl() != null) {
                // todo: handle fetch
            } else {
                // copy the bitstream
                messageDigest.reset();
                final Path dataFile = bsDirectory.resolve(seqId);
                final InputStream is = bitstreamService.retrieve(Curator.curationContext(), bitstream);

                try (OutputStream fout = Files.newOutputStream(dataFile);
                     CountingOutputStream countingOs = new CountingOutputStream(fout);
                     DigestOutputStream dout = new DigestOutputStream(countingOs, messageDigest)) {
                    Utils.copy(is, dout);

                    successBytes.addAndGet(countingOs.getCount());
                    successFiles.incrementAndGet();
                }

                final String fileChecksum = Utils.toHex(messageDigest.digest());
                checksums.put(dataFile.toFile(), fileChecksum);
            }
        }

        // also add logo if it exists
        if (logo != null) {
            messageDigest.reset();
            final InputStream logoIS = bitstreamService.retrieve(Curator.curationContext(), logo);
            final Path logoPath = dataDir.resolve(LOGO_FILE);

            try (OutputStream os = Files.newOutputStream(logoPath);
                 CountingOutputStream countingOs = new CountingOutputStream(os);
                 DigestOutputStream dos = new DigestOutputStream(countingOs, messageDigest)) {
                Utils.copy(logoIS, dos);

                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOs.getCount());
            }
            checksums.put(logoPath.toFile(), Utils.toHex(messageDigest.digest()));
        }

        // Finalize the Bag (write + serialize)
        // todo: get extra tag/bag-info data through some configuration
        bag.registerChecksums(digest.bagitName(), checksums);
        bag.addTags(BagConfig.BAG_INFO_KEY, generateBagInfo());
        bag.write();

        BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
        Path serializedBag = serializer.serialize(directory.toPath());
        delete(directory);

        return serializedBag.toFile();
    }

    /**
     * Get system generated bag-info fields
     *
     * @return A {@link Map} of the bag-info fields to their values
     */
    private Map<String, String> generateBagInfo() {
        final Map<String, String> bagInfo = new HashMap<>();
        bagInfo.put(BagProfileConstants.BAGIT_PROFILE_IDENTIFIER, bagProfile);
        bagInfo.put("Bag-Size", FileUtils.byteCountToDisplaySize(successBytes.get()));
        bagInfo.put("Payload-Oxum", successBytes.toString() + "." + successFiles.toString());
        bagInfo.put("Bagging-Date", DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now()));
        return Collections.emptyMap();
    }

    /**
     * Delete a directory and it's files/subdirectories
     *
     * @param directory the directory to delete
     */
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

    /**
     * Write the xml {@code elements} to the given {@code manifestXml} file. After writing the message digest of the
     * written xml file is returned.
     *
     * @param elements the {@link XmlElement}s to write to the file
     * @param manifestXml the {@link Path} to the xml file
     * @param messageDigest the {@link MessageDigest} tracking the digest of the file
     * @return the value of the {@link MessageDigest}
     * @throws IOException if there are any errors writing to the {@code manifestXml}
     */
    private String writeXmlMetadata(final List<XmlElement> elements, final Path manifestXml,
                                    final MessageDigest messageDigest) throws IOException {
        if (Files.notExists(manifestXml.getParent())) {
            Files.createDirectories(manifestXml.getParent());
        }

        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final CountingOutputStream countingOs = new CountingOutputStream(xmlOut);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(countingOs, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");

            for (XmlElement element : elements) {
                if (element.getBody() != null) {
                    xmlWriter.writeStartElement("value");
                    for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
                        if (attribute.getKey() != null && attribute.getValue() != null) {
                            xmlWriter.writeAttribute(attribute.getKey(), attribute.getValue());
                        }
                    }
                    xmlWriter.writeCharacters(element.getBody());
                    xmlWriter.writeEndElement();
                }
            }

            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();

            successBytes.addAndGet(countingOs.getCount());
            successFiles.incrementAndGet();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        return Utils.toHex(messageDigest.digest());
    }

}
