package org.dspace.pack.bagit;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

/**
 * Handle the actual copying and writing of files into a Bag
 *
 * @author mikejritter
 * @since 2020-03-02
 */
public class BagItAipWriter {

    // Constants used in the packers
    public static final String BAG_AIP = "AIP";
    public static final String BAG_MAN = "man";
    public static final String OBJ_TYPE_ITEM = "item";
    public static final String OBJ_TYPE_DELETION = "deletion";
    public static final String OBJ_TYPE_COMMUNITY = "community";
    public static final String OBJ_TYPE_COLLECTION = "collection";
    public static final String XML_NAME_KEY = "name";

    private final String DATA_DIR = "data";
    private final String LOGO_FILE = "logo";
    private final String METADATA_XML = "metadata.xml";
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
     */
    private final Map<String, List<String>> properties;

    /**
     * Key-Value xml properties
     */
    private final List<XmlElement> metadata;

    /**
     * A map of Bitstreams to package with the AIP
     */
    private final List<BagBitstream> bitstreams;

    /**
     * Constructor for a {@link BagItAipWriter}. Takes all the information needed in order to write an AIP for dspace
     * consumption.
     *
     * @param directory the root {@link File} which the bag will be written to
     * @param archFmt the serialization format when archiving the bag to a single file
     * @param logo the {@link Bitstream} of the logo, or null
     * @param properties a {@link Map} which maps a filename with a list of lines to write to the file
     * @param metadata a {@link List} of {@link XmlElement}s to write to the bags data/metadata.xml
     * @param bitstreams a {@link List} of {@link BagBitstream}s which should be written as payload files for the bag
     */
    public BagItAipWriter(File directory, String archFmt, Bitstream logo, Map<String, List<String>> properties,
                          List<XmlElement> metadata, List<BagBitstream> bitstreams) {
        this.logo = logo;
        this.archFmt = checkNotNull(archFmt);
        this.directory = checkNotNull(directory);
        this.metadata = checkNotNull(metadata);
        this.properties = checkNotNull(properties);
        this.bitstreams = bitstreams != null ? bitstreams : Collections.<BagBitstream>emptyList();
    }

    public File packageAip() throws IOException, SQLException, AuthorizeException {
        // check if the Bag was already being worked on
        final Path dataDir = directory.toPath().resolve(DATA_DIR);
        if (Files.exists(dataDir)) {
            throw new IllegalStateException("Unable to create bag " + directory.toPath().getFileName() +
                                            ", data directory already exists!");
        }

        // setup the BagProfile and BagWriter
        final BagItDigest digest = BagItDigest.MD5;
        final MessageDigest messageDigest = digest.messageDigest();
        final BagProfile profile = new BagProfile(BagProfile.BuiltIn.BEYOND_THE_REPOSITORY);
        final BagWriter bag = new BagWriter(directory, Collections.singleton(digest.bagitName()));

        // Write the base properties files for the bag
        for (String filename : properties.keySet()) {
            final Path objfile = dataDir.resolve(filename);
            if (Files.notExists(objfile.getParent())) {
                Files.createDirectories(objfile.getParent());
            }

            try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
                 final CountingOutputStream countingOs = new CountingOutputStream(objOS);
                 final DigestOutputStream objDigest = new DigestOutputStream(countingOs, messageDigest)) {
                final List<String> lines = properties.get(filename);
                for (String line : lines) {
                    objDigest.write(line.getBytes());
                    objDigest.write("\n".getBytes());
                }

                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOs.getCount());
            }
            final String objFileDigest = Utils.toHex(messageDigest.digest());
            checksums.put(objfile.toFile(), objFileDigest);
        }

        // then metadata
        messageDigest.reset();
        final Path metadataXml = dataDir.resolve(METADATA_XML);
        final String xmlDigest = writeXmlMetadata(metadata, metadataXml, messageDigest);
        checksums.put(metadataXml.toFile(), xmlDigest);

        // write any bitstreams
        for (BagBitstream bagBitstream : bitstreams) {
            // create the bundle directory
            final String bundle = bagBitstream.getBundle();
            final Path bsDirectory = dataDir.resolve(bundle);
            Files.createDirectories(bsDirectory);

            // write the bitstream metadata
            final Bitstream bitstream = bagBitstream.getBitstream();
            final String seqId = String.valueOf(bitstream.getSequenceID());
            final Path bitstreamXml = bsDirectory.resolve(seqId + "-" + METADATA_XML);
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
        final BagProfile.BuiltIn btr = BagProfile.BuiltIn.BEYOND_THE_REPOSITORY;
        bagInfo.put(BagProfileConstants.BAGIT_PROFILE_IDENTIFIER, btr.getIdentifier());
        bagInfo.put(BagConfig.BAG_SIZE_KEY, FileUtils.byteCountToDisplaySize(successBytes.get()));
        bagInfo.put(BagConfig.PAYLOAD_OXUM_KEY, successBytes.toString() + "." + successFiles.toString());
        bagInfo.put(BagConfig.BAGGING_DATE_KEY, ISODateTimeFormat.date().print(LocalDate.now()));
        return bagInfo;
    }

    /**
     * Delete a directory and it's files/subdirectories
     *
     * @param directory the directory to delete
     */
    private void delete(File directory) {
        // protect against being sent a file instead of a directory
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    delete(file);
                } else {
                    file.delete();
                }
            }
        }

        directory.delete();
    }

    /**
     * Write the xml {@code elements} to the given {@code metadataXml} file. After writing the message digest of the
     * written xml file is returned.
     *
     * @param elements the {@link XmlElement}s to write to the file
     * @param metadataXml the {@link Path} to the xml file
     * @param messageDigest the {@link MessageDigest} tracking the digest of the file
     * @return the value of the {@link MessageDigest}
     * @throws IOException if there are any errors writing to the {@code metadataXml}
     */
    private String writeXmlMetadata(final List<XmlElement> elements, final Path metadataXml,
                                    final MessageDigest messageDigest) throws IOException {
        if (Files.notExists(metadataXml.getParent())) {
            Files.createDirectories(metadataXml.getParent());
        }

        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(metadataXml, StandardOpenOption.CREATE_NEW);
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
