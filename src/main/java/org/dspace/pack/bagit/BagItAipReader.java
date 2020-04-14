/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.google.common.base.Optional;
import org.apache.commons.io.FileUtils;
import org.duraspace.bagit.BagDeserializer;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.SerializationSupport;

/**
 * Assist in reading aips and retrieving information from the package.
 *
 * @author mikejritter
 * @since 2020-03-19
 */
public class BagItAipReader {

    private final String xmlSuffix = ".xml";
    private final String dataDirectory = "data";
    private final String metadataLocation = dataDirectory + "/metadata.xml";
    private final String objectPropertiesLocation = dataDirectory + "/object.properties";

    private final Path bag;

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

        // deserialize if necessary
        if (Files.isRegularFile(bag)) {
            final BagProfile profile = new BagProfile(BagProfile.BuiltIn.BEYOND_THE_REPOSITORY);
            final BagDeserializer deserializer = SerializationSupport.deserializerFor(bag, profile);
            this.bag = deserializer.deserialize(bag);
        } else {
            this.bag = bag;
        }
    }

    /**
     * Retrieve the object.properties for an aip
     *
     * @return the {@link Properties}, loaded from the object.properties
     * @throws IOException if there is an error loading the properties
     */
    public Properties readProperties() throws IOException {
        final Properties properties = new Properties();
        try (InputStream is = Files.newInputStream(bag.resolve(objectPropertiesLocation))) {
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
        } catch (IOException ignored) {
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
                // checking start with on the Path doesn't really work as expected, so get the relative name and make
                // it a String
                final String filename = path.getFileName().toString();
                return path.toFile().isFile() && filename.startsWith("bitstream");
            }
        };
        final DirectoryStream<Path> bitstreams = Files.newDirectoryStream(bag.resolve(dataDirectory), bitstreamFilter);

        Optional<Path> logo = Optional.absent();
        final Iterator<Path> iterator = bitstreams.iterator();
        if (iterator.hasNext()) {
            logo = Optional.of(iterator.next());
        }

        return logo;
    }

    /**
     * Read the metadata.xml file located in a bags data directory
     *
     * @return a list of {@link XmlElement}s which were read from the file
     * @throws IOException if there was an error reading the file or parsing the xml
     */
    public List<XmlElement> readMetadata() throws IOException {
        final Path metadata = bag.resolve(metadataLocation);
        try (InputStream metadataStream = Files.newInputStream(metadata)) {
            return readXml(metadataStream);
        }
    }

    /**
     * Find any {@link org.dspace.content.Bitstream}s packaged in an aip and return path, bundle name, and metadata
     *
     * @return a list of {@link PackagedBitstream}s found
     * @throws IOException if there are any errors searching for bitstreams
     */
    public List<PackagedBitstream> findBitstreams() throws IOException {
        final Path data = bag.resolve(dataDirectory);

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
                return path.toFile().isFile() && !path.getFileName().toString().endsWith(xmlSuffix);
            }
        };

        final List<PackagedBitstream> packagedBitstreams = new ArrayList<>();
        final DirectoryStream<Path> directories = Files.newDirectoryStream(data, directoryFilter);
        for (Path bundle : directories) {
            final String bundleName = bundle.getFileName().toString();
            final DirectoryStream<Path> bitstreams = Files.newDirectoryStream(bundle, bitstreamFilter);
            for (Path bitstream : bitstreams) {
                final String bitstreamName = bitstream.getFileName().toString();

                // load the bitstream metadata
                final Path bitstreamXml = bundle.resolve(bitstreamName + "-metadata.xml");
                final List<XmlElement> xmlElements = readXml(bitstreamXml);

                packagedBitstreams.add(new PackagedBitstream(bundleName, bitstream, xmlElements));
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

    /**
     * Read an xml file in order to read metadata for a DSpaceObject. This requires the file to conform to having a
     * metadata stanza as well as have value stanzas which store the data to read.
     *
     * @param inputStream the InputStream for the metadata file
     * @return a list of {@link XmlElement}s read from the file
     * @throws IOException if there is an error reading the file or parsing the xml
     */
    private List<XmlElement> readXml(final InputStream inputStream) throws IOException {
        final XMLStreamReader reader;
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        try {
            reader = factory.createXMLStreamReader(inputStream);
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final List<XmlElement> elements = new ArrayList<>();
        try {
            // search for metadata stanza
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                    reader.getLocalName().equalsIgnoreCase("metadata")) {

                    // search for value stanzas
                    while (reader.hasNext()) {
                        if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                            reader.getLocalName().equalsIgnoreCase("value")) {
                            XmlElement element = readElement(reader);
                            if (element != null) {
                                elements.add(element);
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        return elements;
    }

    private XmlElement readElement(XMLStreamReader reader) throws XMLStreamException {
        // we begin on a start element so initialize the attributes first
        final Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
        }

        // now iterate to find the body and end element
        String body = null;
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.CHARACTERS:
                    body = reader.getText();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    return new XmlElement(body, attributes);
                default:
                    break;
            }
        }

        return null;
    }

}
