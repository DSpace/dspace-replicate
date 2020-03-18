/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.BAG_TYPE;
import static org.dspace.pack.PackerFactory.OBJECT_ID;
import static org.dspace.pack.PackerFactory.OBJECT_TYPE;
import static org.dspace.pack.PackerFactory.OBJFILE;
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_AIP;
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_COMMUNITY;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;
import static org.dspace.pack.bagit.BagItAipWriter.XML_NAME_KEY;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.duraspace.bagit.BagDeserializer;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.SerializationSupport;

/**
 * CommunityPacker Packs and unpacks Community AIPs in Bagit format.
 *
 * @author richardrodgers
 */
public class CommunityPacker implements Packer
{
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();

    // NB - these values must remain synchronized with DB schema -
    // they represent the persistent object state
    private static final String[] fields = {
        "name",
        "short_description",
        "introductory_text",
        "copyright_text",
        "side_bar_text"
    };

    private Community community = null;
    private String archFmt = null;

    public CommunityPacker(Community community, String archFmt)
    {
        this.community = community;
        this.archFmt = archFmt;
    }

    public Community getCommunity()
    {
        return community;
    }

    public void setCommunity(Community community)
    {
        this.community = community;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, SQLException, IOException {
        final Bitstream logo = community.getLogo();

        // object.properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_AIP);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + OBJ_TYPE_COMMUNITY);
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + community.getHandle());

        final List<Community> parents = community.getParentCommunities();
        if (parents != null && !parents.isEmpty()) {
            objectProperties.add(OWNER_ID + PROPERTIES_DELIMITER + parents.get(0).getHandle());
        }
        final Map<String, List<String>> properties = ImmutableMap.of(OBJFILE, objectProperties);

        // collect the xml metadata
        final List<XmlElement> elements = new ArrayList<>();
        for (String field : fields) {
            final String metadata = communityService.getMetadata(community, field);
            final XmlElement element = new XmlElement(metadata, ImmutableMap.of(XML_NAME_KEY, field));
            elements.add(element);
        }

        final BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, logo, properties, elements,
                                                            Collections.<BagBitstream>emptyList());
        return aipWriter.packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null) {
            throw new IOException("Missing archive for community: " + community.getHandle());
        }

        final Path bagPath;
        if (archive.isFile()) {
            final BagProfile profile = new BagProfile(BagProfile.BuiltIn.BEYOND_THE_REPOSITORY);
            final BagDeserializer deserializer = SerializationSupport.deserializerFor(archive.toPath(), profile);
            bagPath = deserializer.deserialize(archive.toPath());
        } else {
            bagPath = archive.toPath();
        }

        final List<XmlElement> xmlElements = readXml(bagPath.resolve("data/metadata.xml"));
        for (XmlElement xmlElement : xmlElements) {
            final String name = xmlElement.getAttributes().get("name");
            final String value = xmlElement.getBody();
            communityService.setMetadata(Curator.curationContext(), community, name, value);
        }

        try (InputStream is = Files.newInputStream(bagPath.resolve("data/logo"))) {
            communityService.setLogo(Curator.curationContext(), community, is);
        } catch (FileNotFoundException ignored) {
        }

        communityService.update(Curator.curationContext(), community);

        FileUtils.deleteDirectory(bagPath.toFile());
    }

    private List<XmlElement> readXml(Path metadata) throws IOException {
        final XMLStreamReader reader;
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        try {
            reader = factory.createXMLStreamReader(Files.newInputStream(metadata));
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final List<XmlElement> elements = new ArrayList<>();
        try {
            // todo: push this somewhere else
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
        Map<String, String> attributes = new HashMap<>();
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

    @Override
    public long size(String method) throws SQLException
    {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        // proceed to children, unless 'norecurse' set
        if (! "norecurse".equals(method))
        {
            for (Community comm : community.getSubcommunities())
            {
                size += PackerFactory.instance(comm).size(method);
            }
            for (Collection coll : community.getCollections())
            {
                size += PackerFactory.instance(coll).size(method);
            }
        }
        return size;
    }

    @Override
    public void setContentFilter(String filter)
    {
        // no-op
    }

    @Override
    public void setReferenceFilter(String filter)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
