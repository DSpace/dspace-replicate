/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.dspace.pack.PackerFactory.BAG_PROFILE_KEY;
import static org.dspace.pack.PackerFactory.DEFAULT_PROFILE;

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

import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingOutputStream;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.dspace.pack.bagit.xml.Element;
import org.dspace.pack.bagit.xml.Metadata;
import org.dspace.pack.bagit.xml.Policy;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
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
 * The BagItAipWriter handles the packaging of DSpaceObjects into their respective bags. It processes the metadata and
 * bitstreams given to it by the various {@link org.dspace.pack.Packer}s in order to write the object.properties,
 * metadata.xml, etc for each AIP.
 *
 * @author mikejritter
 * @since 2020-03-02
 */
public class BagItAipWriter {

    // Constants used by the Packers
    public static final String BAG_AIP = "AIP";
    public static final String BAG_MAN = "man";
    public static final String OBJ_TYPE_ITEM = "item";
    public static final String OBJ_TYPE_DELETION = "deletion";
    public static final String OBJ_TYPE_COMMUNITY = "community";
    public static final String OBJ_TYPE_COLLECTION = "collection";
    public static final String XML_NAME_KEY = "name";
    public static final String PROPERTIES_DELIMITER = "  ";
    private static final String BITSTREAM_PREFIX = "bitstream_";

    private final String DATA_DIR = "data";
    private final String LOGO_FILE = "logo";
    private final String METADATA_XML = "metadata.xml";
    private final BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    // Fields used for book keeping
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
     * Pojo for xml metadata on a DSpaceObject
     */
    private final Metadata metadata;

    /**
     * Pojo for xml policy data on a DSpaceObject
     */
    private final Policy policy;

    /**
     * A map of Bitstreams to package with the AIP
     */
    private final List<BagBitstream> bitstreams;

    /**
     * Constructor for a {@link BagItAipWriter}. Takes all the information needed in order to write an AIP for dspace
     * consumption.
     *
     * @param directory  the root {@link File} which the bag will be written to
     * @param archFmt    the serialization format when archiving the bag to a single file
     * @param logo       the {@link Bitstream} of the logo, or null
     * @param properties a {@link Map} which maps a filename with a list of lines to write to the file
     * @param metadata   the {@link Metadata} to write to the bags data/metadata.xml
     * @param policy     the {@link Policy} to write to the bags data/policy.xml
     * @param bitstreams a {@link List} of {@link BagBitstream}s which should be written as payload files for the bag
     */
    public BagItAipWriter(final File directory, final String archFmt, final Bitstream logo,
                          final Map<String, List<String>> properties, final Metadata metadata,
                          final Policy policy, final List<BagBitstream> bitstreams) {
        this.logo = logo;
        this.policy = policy;
        this.metadata = metadata;
        this.archFmt = checkNotNull(archFmt);
        this.directory = checkNotNull(directory);
        this.properties = checkNotNull(properties);
        this.bitstreams = bitstreams != null ? bitstreams : Collections.<BagBitstream>emptyList();
    }

    /**
     * Create a serialized BagIt bag using the parameters the BagItAipWriter was instantiated with
     *
     * @return the location of the serialized bag, as a {@link File}
     * @throws IOException        if there are any errors writing to the bag
     * @throws SQLException       if there is a problem querying {@link BitstreamService#retrieve(Context, Bitstream)}
     * @throws AuthorizeException if there is a problem querying {@link BitstreamService#retrieve(Context, Bitstream)}
     */
    public File packageAip() throws IOException, SQLException, AuthorizeException {
        final Context curationContext = Curator.curationContext();
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        final String profileName = configurationService.getProperty(BAG_PROFILE_KEY, DEFAULT_PROFILE);

        // validate the tag file configuration before starting to write
        // Note: the validateTagFiles method will throw a RuntimeException if validation fails
        final BagProfile profile = new BagProfile(BagProfile.BuiltIn.from(profileName));
        final Map<String, Map<String, String>> tagFiles = BagInfoHelper.getTagFiles();
        profile.validateTagFiles(tagFiles);

        // check if the Bag was already being worked on
        final Path dataDir = directory.toPath().resolve(DATA_DIR);
        if (Files.exists(dataDir)) {
            throw new IllegalStateException("Unable to create bag " + directory.toPath().getFileName() +
                                            ", data directory already exists!: " + dataDir.toString());
        }

        // setup the BagProfile and BagWriter
        final BagItDigest digest = BagItDigest.MD5;
        final MessageDigest messageDigest = digest.messageDigest();
        final BagWriter bag = new BagWriter(directory, Collections.singleton(digest));

        // set up the tag files for the bag
        for (String tag : tagFiles.keySet()) {
            bag.addTags(tag, tagFiles.get(tag));
        }

        // Write the base properties files for the bag
        for (String filename : properties.keySet()) {
            final Path propertiesFile = dataDir.resolve(filename);
            if (Files.notExists(propertiesFile.getParent())) {
                Files.createDirectories(propertiesFile.getParent());
            }

            try (final OutputStream output = Files.newOutputStream(propertiesFile, StandardOpenOption.CREATE_NEW);
                 final CountingOutputStream countingOS = new CountingOutputStream(output);
                 final DigestOutputStream digestOS = new DigestOutputStream(countingOS, messageDigest)) {
                final List<String> lines = properties.get(filename);
                for (String line : lines) {
                    digestOS.write(line.getBytes());
                    digestOS.write("\n".getBytes());
                }

                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOS.getCount());
            }
            final String objFileDigest = Utils.toHex(messageDigest.digest());
            checksums.put(propertiesFile.toFile(), objFileDigest);
        }

        // then metadata
        if (metadata != null) {
            messageDigest.reset();
            final Path metadataXml = dataDir.resolve(METADATA_XML);
            writeXml(metadata, metadataXml, messageDigest);
        }

        // policy info
        if (policy != null) {
            messageDigest.reset();
            final Path policyXml = dataDir.resolve("policy.xml");
            writeXml(policy, policyXml, messageDigest);
        }

        // write any bitstreams
        for (BagBitstream bagBitstream : bitstreams) {
            // create the bundle directory
            final String bundle = bagBitstream.getBundle();
            final Path bitstreamDirectory = dataDir.resolve(bundle);
            Files.createDirectories(bitstreamDirectory);

            // write the bitstream metadata
            final Bitstream bitstream = bagBitstream.getBitstream();
            final String bitstreamID = bitstream.getID().toString();
            if (bagBitstream.getMetadata() != null) {
                final String mdName = BITSTREAM_PREFIX + bitstreamID + "-" + METADATA_XML;
                final Path xml = bitstreamDirectory.resolve(mdName);
                writeXml(bagBitstream.getMetadata(), xml, messageDigest);
            }

            if (bagBitstream.getPolicy() != null) {
                final String mdName = BITSTREAM_PREFIX + bitstreamID + "-policy.xml";
                final Path xml = bitstreamDirectory.resolve(mdName);
                writeXml(bagBitstream.getPolicy(), xml, messageDigest);
            }

            if (bagBitstream.getFetchUrl() != null) {
                throw new UnsupportedOperationException("fetch.txt for bags is not supported at this time");
            } else {
                // copy the bitstream
                messageDigest.reset();
                final String filename = createBitstreamFilename(bitstream, curationContext);
                final Path dataFile = bitstreamDirectory.resolve(filename);
                final InputStream is = bitstreamService.retrieve(curationContext, bitstream);

                try (OutputStream output = Files.newOutputStream(dataFile);
                     CountingOutputStream countingOS = new CountingOutputStream(output);
                     DigestOutputStream digestOS = new DigestOutputStream(countingOS, messageDigest)) {
                    Utils.copy(is, digestOS);

                    successBytes.addAndGet(countingOS.getCount());
                    successFiles.incrementAndGet();
                }

                final String fileChecksum = Utils.toHex(messageDigest.digest());
                checksums.put(dataFile.toFile(), fileChecksum);
            }
        }

        // also add logo if it exists
        if (logo != null) {
            messageDigest.reset();
            final String filename = createBitstreamFilename(logo, curationContext);
            final InputStream logoIS = bitstreamService.retrieve(curationContext, logo);
            final Path logoPath = dataDir.resolve(filename);

            try (OutputStream output = Files.newOutputStream(logoPath);
                 CountingOutputStream countingOS = new CountingOutputStream(output);
                 DigestOutputStream digestOS = new DigestOutputStream(countingOS, messageDigest)) {
                Utils.copy(logoIS, digestOS);

                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOS.getCount());
            }
            checksums.put(logoPath.toFile(), Utils.toHex(messageDigest.digest()));
        }

        // Finalize the Bag (write + serialize)
        bag.registerChecksums(digest, checksums);
        bag.addTags(BagConfig.BAG_INFO_KEY, generateBagInfo(profile));
        bag.write();

        final BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
        final Path serializedBag = serializer.serialize(directory.toPath());
        delete(directory);

        return serializedBag.toFile();
    }

    /**
     * Create a filename for a bitstream in a similar manner to the METSDisseminator:
     * - prefix of bitstream_
     * - Bitstream ID
     * - file extension if found
     *
     * @param bitstream the Bitstream to create the filename for
     * @param context   the curation Context
     * @return the filename
     * @throws SQLException if the BitstreamFormat cannot be queried
     */
    private String createBitstreamFilename(final Bitstream bitstream, final Context context) throws SQLException {
        final List<String> extensions = bitstreamService.getFormat(context, bitstream).getExtensions();

        // build the filename
        final StringBuilder filename = new StringBuilder(BITSTREAM_PREFIX);
        filename.append(bitstream.getID());
        if (!extensions.isEmpty()) {
            filename.append(".").append(extensions.get(0));
        }

        return filename.toString();
    }

    /**
     * Get system generated bag-info fields
     *
     * @param profile The {@link BagProfile} being used to write a bag
     * @return A {@link Map} of the bag-info fields to their values
     */
    private Map<String, String> generateBagInfo(final BagProfile profile) {
        final Map<String, String> bagInfo = new HashMap<>();
        final String identifier = profile.getProfileMetadata().get(BagProfileConstants.BAGIT_PROFILE_IDENTIFIER);
        bagInfo.put(BagProfileConstants.BAGIT_PROFILE_IDENTIFIER, identifier);
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
    private void delete(final File directory) {
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
     * Write the xml {@code elements} to the given {@code metadata} file. After writing the message digest of the
     * written xml file is returned.
     *
     * @param element       the {@link Element} to write to the file
     * @param xml           the {@link Path} to the xml file
     * @param messageDigest the {@link MessageDigest} tracking the digest of the file
     * @throws IOException if there are any errors writing to the {@code metadata}
     */
    private void writeXml(final Element element, final Path xml, final MessageDigest messageDigest) throws IOException {
        if (Files.notExists(xml.getParent())) {
            Files.createDirectories(xml.getParent());
        }

        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        messageDigest.reset();
        try (final OutputStream output = Files.newOutputStream(xml, StandardOpenOption.CREATE_NEW);
             final CountingOutputStream countingOS = new CountingOutputStream(output);
             final DigestOutputStream digestOS = new DigestOutputStream(countingOS, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(digestOS,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            writeXml(xmlWriter, ImmutableList.of(element));
            xmlWriter.writeEndDocument();

            successBytes.addAndGet(countingOS.getCount());
            successFiles.incrementAndGet();
            checksums.put(xml.toFile(), Utils.toHex(messageDigest.digest()));
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Recursive function to write xml from {@link Element}s and their children
     *
     * @param xmlWriter the XmlStreamWriter
     * @param elements  the XmlElements to write
     * @throws XMLStreamException if an exception occurs
     */
    private void writeXml(XMLStreamWriter xmlWriter, List<Element> elements) throws XMLStreamException {
        for (Element element : elements) {
            xmlWriter.writeStartElement(element.getLocalName());

            if (element.hasChildren()) {
                writeXml(xmlWriter, element.getChildren());
            } else {
                for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
                    if (attribute.getKey() != null && !attribute.getKey().isEmpty() &&
                        attribute.getValue() != null && !attribute.getValue().isEmpty()) {
                        xmlWriter.writeAttribute(attribute.getKey(), attribute.getValue());
                    }
                }

                xmlWriter.writeCharacters(element.getBody());
            }

            xmlWriter.writeEndElement();
        }
    }

}
