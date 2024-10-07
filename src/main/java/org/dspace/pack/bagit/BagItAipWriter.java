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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.io.CountingOutputStream;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.apache.commons.io.FileUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.policy.Policies;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.duraspace.bagit.BagConfig;
import org.duraspace.bagit.BagItDigest;
import org.duraspace.bagit.BagWriter;
import org.duraspace.bagit.profile.BagProfile;
import org.duraspace.bagit.profile.BagProfileConstants;
import org.duraspace.bagit.serialize.BagSerializer;
import org.duraspace.bagit.serialize.SerializationSupport;
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
    public static final String PROPERTIES_DELIMITER = "  ";

    private static final String DATA_DIR = "data";
    public static final String ROLES_XML = "roles.xml";
    public static final String POLICY_XML = "policy.xml";
    public static final String METADATA_XML = "metadata.xml";
    public static final String TEMPLATE_XML = "template-metadata.xml";
    private static final String BITSTREAM_PREFIX = "bitstream_";

    protected static final long DEFAULT_MODIFIED_DATE = 1036368000L * 1000;

    private final BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    // Fields used for bookkeeping
    private final AtomicLong successBytes = new AtomicLong();
    private final AtomicLong successFiles = new AtomicLong();
    private final LinkedHashMap<File, String> checksums = new LinkedHashMap<>();

    /**
     * The context to use
     */
    private final Context context;

    /**
     * The directory to which the BagIt bag will be written
     */
    private final File directory;

    /**
     * The serialization format for the bag
     */
    private final String archFmt;

    /**
     * A Mapping of filenames to the properties
     */
    private final Map<String, List<String>> properties;

    /**
     * A logo for the package, or null if one does not exist
     */
    private Bitstream logo;

    /**
     * Pojo for xml policy data on a DSpaceObject
     */
    private Policies policies;

    /**
     * Pojo for xml metadata on a DSpaceObject
     */
    private Metadata metadata;

    /**
     * Pojo for xml item template metadata on a DSpaceObject
     */
    private Metadata itemTemplate;

    /**
     * Pojo for xml roles
     */
    private DSpaceRoles dSpaceRoles;

    /**
     * A map of Bitstreams to package with the AIP
     */
    private List<BagBitstream> bitstreams;

    /**
     * Last modified time of DSpace object
     */
    private Long lastModifiedTime;

    /**
     * Constructor for a {@link BagItAipWriter}. Takes a minimal set of information needed in order to write an AIP as a
     * BagIt bag for dspace consumption.
     *
     * @param context    the context to use
     * @param directory  the root {@link File} which the bag will be written to
     * @param archFmt    the serialization format when archiving the bag to a single file
     * @param properties a {@link Map} which maps a filename with a list of lines to write to the file
     */
    public BagItAipWriter(final Context context, final File directory, final String archFmt, final Map<String,
            List<String>> properties) {
        this.context = context;
        this.logo = null;
        this.policies = null;
        this.metadata = null;
        this.lastModifiedTime = DEFAULT_MODIFIED_DATE;
        this.archFmt = checkNotNull(archFmt);
        this.directory = checkNotNull(directory);
        this.properties = checkNotNull(properties);
        this.bitstreams = Collections.emptyList();
    }

    /**
     * @param logo the {@link Bitstream} of the logo, or null
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withLogo(final Bitstream logo) {
        this.logo = logo;
        return this;
    }

    /**
     * @param policies the {@link Policies} to write to the bags data/policy.xml
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withPolicies(final Policies policies) {
        this.policies = policies;
        return this;
    }

    /**
     * @param metadata the {@link Metadata} to write to the bags data/metadata.xml
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withMetadata(final Metadata metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * @param itemTemplate the {@link Metadata} for an Item Template
     * @return the {@link BagItAipWriter}
     */
    public BagItAipWriter withItemTemplate(Metadata itemTemplate) {
        this.itemTemplate = itemTemplate;
        return this;
    }

    /**
     * @param dSpaceRoles the {@link DSpaceRoles} to write to the bags data/roles.xml
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withDSpaceRoles(final DSpaceRoles dSpaceRoles) {
        this.dSpaceRoles = dSpaceRoles;
        return this;
    }

    /**
     * @param bitstreams a {@link List} of {@link BagBitstream}s which should be written as payload files for the bag
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withBitstreams(final List<BagBitstream> bitstreams) {
        this.bitstreams = bitstreams != null ? bitstreams : Collections.<BagBitstream>emptyList();
        return this;
    }

    /**
     * @param lastModifiedTime the {@link Item} to use when writing AIP files
     * @return the {@link BagItAipWriter} used for creating the aip
     */
    public BagItAipWriter withLastModifiedTime(final long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
        return this;
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
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        final String profileName = configurationService.getProperty(BAG_PROFILE_KEY, DEFAULT_PROFILE);

        // setup xml marshalling
        final Marshaller marshaller;
        try {
            final JAXBContext context = JAXBContext.newInstance(Metadata.class, Policies.class, DSpaceRoles.class);
            marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        } catch (JAXBException e) {
            throw new IOException("Unable to create JAXBContext!", e);
        }

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

        // then xml files: metadata, policies, roles
        writeXml(metadata, dataDir.resolve(METADATA_XML), marshaller, messageDigest);
        writeXml(itemTemplate, dataDir.resolve(TEMPLATE_XML), marshaller, messageDigest);
        writeXml(policies, dataDir.resolve(POLICY_XML), marshaller, messageDigest);
        writeXml(dSpaceRoles, dataDir.resolve(ROLES_XML), marshaller, messageDigest);

        // write any bitstreams
        for (BagBitstream bagBitstream : bitstreams) {
            // create the bundle directory
            final String bundle = bagBitstream.getBundle();
            final Path bitstreamDirectory = dataDir.resolve(bundle);
            Files.createDirectories(bitstreamDirectory);

            // get the bitstream uuid
            final Bitstream bitstream = bagBitstream.getBitstream();
            final String bitstreamID = bitstream.getID().toString();

            // write the bitstream metadata + policy
            final String mdName = BITSTREAM_PREFIX + bitstreamID + "-" + METADATA_XML;
            final Path mdXml = bitstreamDirectory.resolve(mdName);
            writeXml(bagBitstream.getMetadata(), mdXml, marshaller, messageDigest);

            final String polName = BITSTREAM_PREFIX + bitstreamID + "-" + POLICY_XML;
            final Path polXml = bitstreamDirectory.resolve(polName);
            writeXml(bagBitstream.getPolicies(), polXml, marshaller, messageDigest);

            if (bagBitstream.getFetchUrl() != null) {
                throw new UnsupportedOperationException("fetch.txt for bags is not supported at this time");
            } else {
                // copy the bitstream
                messageDigest.reset();
                final String filename = createBitstreamFilename(bitstream, context);
                final Path dataFile = bitstreamDirectory.resolve(filename);
                final InputStream is = bitstreamService.retrieve(context, bitstream);

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
            final String filename = createBitstreamFilename(logo, context);
            final InputStream logoIS = bitstreamService.retrieve(context, logo);
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
        final Path serializedBag = serializer.serializeWithTimestamp(directory.toPath(), lastModifiedTime);
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
     * Create an xml document for a given object and its path
     *
     * @param object the object to marshal
     * @param xml the path of the xml file to create
     * @param marshaller the jaxb {@link Marshaller}
     * @param messageDigest the message digest to capture what is written
     * @throws IOException if there's an error writing the file
     */
    private void writeXml(final Object object, final Path xml, final Marshaller marshaller,
                          final MessageDigest messageDigest) throws IOException {
        messageDigest.reset();

        if (object != null) {
            try (OutputStream output = Files.newOutputStream(xml);
                 CountingOutputStream countingOS = new CountingOutputStream(output);
                 DigestOutputStream digestOS = new DigestOutputStream(countingOS, messageDigest)) {
                marshaller.marshal(object, digestOS);
                successFiles.incrementAndGet();
                successBytes.addAndGet(countingOS.getCount());
            } catch (JAXBException e) {
                throw new IOException("Error writing xml for " + xml.getFileName(), e);
            }
            checksums.put(xml.toFile(), Utils.toHex(messageDigest.digest()));
        }
    }

}
