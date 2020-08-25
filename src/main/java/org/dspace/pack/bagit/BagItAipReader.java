/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.BAG_PROFILE_KEY;
import static org.dspace.pack.PackerFactory.DEFAULT_PROFILE;
import static org.dspace.pack.bagit.BagItAipWriter.METADATA_XML;
import static org.dspace.pack.bagit.BagItAipWriter.POLICY_XML;
import static org.dspace.pack.bagit.BagItAipWriter.ROLES_XML;
import static org.dspace.pack.bagit.BagItAipWriter.TEMPLATE_XML;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.google.common.base.Optional;
import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.exceptions.InvalidBagitFileFormatException;
import gov.loc.repository.bagit.exceptions.MaliciousPathException;
import gov.loc.repository.bagit.exceptions.UnparsableVersionException;
import gov.loc.repository.bagit.exceptions.UnsupportedAlgorithmException;
import gov.loc.repository.bagit.reader.BagReader;
import gov.loc.repository.bagit.verify.BagVerifier;
import org.apache.commons.io.FileUtils;
import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.policy.Policies;
import org.dspace.pack.bagit.xml.policy.Policy;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.duraspace.bagit.BagDeserializer;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.SerializationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assist in reading aips and retrieving information from the package.
 *
 * @author mikejritter
 * @since 2020-03-19
 */
public class BagItAipReader {

    private final Logger logger = LoggerFactory.getLogger(BagItAipReader.class);

    private final String dataDirectory = "data";

    private final Path bag;
    private final BagProfile profile;
    private final Unmarshaller unmarshaller;

    /**
     * Constructor for a {@link BagItAipReader}. If the given path to the {@code bag} is a single file, it is assumed
     * that the path is an archived aip and will be deserialized.
     *
     * @param bag the {@link Path} to the bag
     * @throws IOException if there are any errors while deserializing the aip located at {@link Path}
     */
    public BagItAipReader(final Path bag) throws IOException {
        if (bag == null || Files.notExists(bag)) {
            throw new IOException("Missing archive: " + bag);
        }

        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(Metadata.class, Policies.class);
            unmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            throw new IOException("Unable to create JAXBContext!", e);
        }

        // get the BagProfile
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        final String profileName = configurationService.getProperty(BAG_PROFILE_KEY, DEFAULT_PROFILE);
        this.profile = new BagProfile(BagProfile.BuiltIn.from(profileName));

        // deserialize if necessary
        if (Files.isRegularFile(bag)) {
            final BagDeserializer deserializer = SerializationSupport.deserializerFor(bag, profile);
            this.bag = deserializer.deserialize(bag);
        } else {
            this.bag = bag;
        }
    }

    /**
     * Validate that an AIP is in a BagIt format which passes both bagit-bag validation and bagit-profile validation
     *
     * @throws RuntimeException if there is an error during validation
     */
    public void validateBag() {
        final Bag locBag;
        final BagReader bagReader = new BagReader();
        final BagVerifier verifier = new BagVerifier() ;
        try {
            locBag = bagReader.read(bag);
        } catch (UnparsableVersionException | InvalidBagitFileFormatException | UnsupportedAlgorithmException
            | MaliciousPathException | IOException e) {
            throw new RuntimeException("Unable to read aip as a BagIt bag!", e);
        }

        try {
            profile.validateBag(locBag);
            verifier.isValid(locBag, false);
        } catch (Exception e) {
            throw new RuntimeException("Unable to verify BagIt bag!", e);
        }
    }

    /**
     * Retrieve the object.properties for an aip
     *
     * @return the {@link Properties}, loaded from the data/object.properties
     * @throws IOException if there is an error loading the properties
     */
    public Properties readProperties() throws IOException {
        final Properties properties = new Properties();
        final Path objectProperties = bag.resolve(dataDirectory).resolve("object.properties");
        try (InputStream is = Files.newInputStream(objectProperties)) {
            properties.load(is);
        }
        return properties;
    }

    /**
     * Read a file's lines given a relative path to the file. If a file does not exist, or an error is encountered while
     * trying to read the file an empty list is returned.
     *
     * Note: the path given should be in relation to the data directory, not the root of the bag
     *
     * @param relativePath the relative path of the file to retrieve
     * @return the lines of the files, as a {@link List} of {@link String}s. If the file does not exist, an empty list
     * will be returned so this method should not be used
     */
    public List<String> readFile(String relativePath) {
        List<String> lines;
        final Path file = bag.resolve(dataDirectory).resolve(relativePath);

        try {
            lines = Files.readAllLines(file, Charset.defaultCharset());
        } catch (IOException exception) {
            logger.warn("Error reading data file {} in BagIt AIP", relativePath, exception);
            lines = Collections.emptyList();
        }

        return lines;
    }

    /**
     * Retrieve the logo for an aip, if it is available
     *
     * @return an {@link Optional} containing the {@link InputStream} of the logo if it exists, and
     * {@link Optional#absent()} otherwise
     * @throws IOException if there is an error getting the {@link InputStream} for the logo
     */
    public Optional<Path> findLogo() throws IOException {
        // Search for the bitstream in the data directory
        final DirectoryStream.Filter<Path> bitstreamFilter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path path) {
                // using startWith() directly on the Path doesn't work as expected, so get the relative name and make
                // it a String
                final String filename = path.getFileName().toString();
                return path.toFile().isFile() && filename.startsWith("bitstream");
            }
        };

        Optional<Path> logo = Optional.absent();
        try(DirectoryStream<Path> bitstreams = Files.newDirectoryStream(bag.resolve(dataDirectory), bitstreamFilter)) {
            final Iterator<Path> iterator = bitstreams.iterator();
            if (iterator.hasNext()) {
                logo = Optional.of(iterator.next());
            }
        }

        return logo;
    }

    /**
     * Read the metadata.xml file located in a bags data directory
     *
     * @return the {@link Metadata} with values read from data/metadata.xml
     * @throws IOException if there was an error reading the file or parsing the xml
     */
    public Metadata readMetadata() throws IOException {
        final Path xml = bag.resolve(dataDirectory).resolve(METADATA_XML);
        try {
            return (Metadata) unmarshaller.unmarshal(xml.toFile());
        } catch (JAXBException e) {
            throw new IOException("Unable to read metadata.xml!", e);
        }
    }

    /**
     * Search for {@link Metadata} for an Item Template in a Bag
     *
     * @return the {@link Metadata} with values read from data/template-metadata.xml if it exists
     */
    public Optional<Metadata> findItemTemplate() throws IOException {
        final Path template = bag.resolve(dataDirectory).resolve(TEMPLATE_XML);
        Metadata metadata = null;
        if (Files.exists(template)) {
            try {
                metadata = (Metadata) unmarshaller.unmarshal(template.toFile());
            } catch (JAXBException e) {
                throw new IOException("Unable to read template-metadata.xml for Collection!", e);
            }
        }

        return Optional.fromNullable(metadata);
    }


    /**
     * Read the policy.xml file located in a bags data directory
     *
     * @return the {@link Policy} with values read from data/policy.xml
     * @throws IOException if there was an error reading the file or parsing the xml
     */
    public Policies readPolicy() throws IOException {
        final Path xml = bag.resolve(dataDirectory).resolve(POLICY_XML);
        try {
            return (Policies) unmarshaller.unmarshal(xml.toFile());
        } catch (JAXBException e) {
            throw new IOException("Unable to read policy.xml!", e);
        }
    }

    /**
     * Attempt to find the data/roles.xml within a Bag. If it does not exist, return an empty {@link Optional}.
     *
     * @return an {@link Optional} containing the {@link Path} for the roles.xml, if it exists
     */
    public Optional<Path> findRoles() {
        final Path roles = bag.resolve(dataDirectory).resolve(ROLES_XML);
        return Files.exists(roles) ? Optional.of(roles) : Optional.<Path>absent();
    }

    /**
     * Find any {@link org.dspace.content.Bitstream}s packaged in an aip and return path, bundle name, and metadata
     *
     * @return a list of {@link PackagedBitstream}s found
     * @throws IOException if there are any errors searching for bitstreams
     */
    public List<PackagedBitstream> findBitstreams() throws IOException {
        final Path data = bag.resolve(dataDirectory);

        // build our regex
        // matches bitstream_uuid OR bitstream_uuid.extension with a group for bitstream_uuid
        final String bitstreamStart = "bitstream_";
        final String relaxedUuid = "[\\w]{8}-[\\w]{4}-[\\w]{4}-[\\w]{4}-[\\w]{12}";
        final String extension = "(\\..*)?";
        final Pattern uuid = Pattern.compile("(?<uuid>" + bitstreamStart + relaxedUuid + ")" + extension);

        // filter to find only directories (for bundle names)
        final DirectoryStream.Filter<Path> directoryFilter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path path) {
                return Files.isDirectory(path);
            }
        };

        // filter to find only the bitstream file
        final DirectoryStream.Filter<Path> bitstreamFilter = new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path path) {
                return path.toFile().isFile() && uuid.matcher(path.getFileName().toString()).matches();
            }
        };

        final List<PackagedBitstream> packagedBitstreams = new ArrayList<>();

        // iterate all bundles
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(data, directoryFilter)) {
            for (Path bundle : directories) {
                final String bundleName = bundle.getFileName().toString();

                // iterate all bitstreams
                try(DirectoryStream<Path> bitstreams = Files.newDirectoryStream(bundle, bitstreamFilter)) {
                    for (Path bitstream : bitstreams) {
                        final String bitstreamName = bitstream.getFileName().toString();

                        // load the bitstream metadata
                        final Matcher matcher = uuid.matcher(bitstreamName);
                        if (matcher.matches()) {
                            final Policies policies;
                            final Metadata metadata;

                            final String uuidPath = matcher.group("uuid");
                            final Path bsPolicy = bundle.resolve(uuidPath + "-" + POLICY_XML);
                            final Path bsMetadata = bundle.resolve(uuidPath + "-" + METADATA_XML);
                            try {
                                policies = (Policies) unmarshaller.unmarshal(bsPolicy.toFile());
                                metadata = (Metadata) unmarshaller.unmarshal(bsMetadata.toFile());
                            } catch (JAXBException e) {
                                throw new IOException("Unable to read bitstream xml!", e);
                            }

                            packagedBitstreams.add(new PackagedBitstream(bundleName, bitstream, metadata, policies));
                        }
                    }
                }
            }
        }

        return packagedBitstreams;
    }

    /**
     * Finish operations and remove the aip
     *
     * @throws IOException if there are any errors removing the aip
     */
    public void clean() throws IOException {
        FileUtils.deleteDirectory(bag.toFile());
    }

}
