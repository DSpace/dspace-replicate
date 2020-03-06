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
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_COLLECTION;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;
import static org.dspace.pack.bagit.BagItAipWriter.XML_NAME_KEY;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
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

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.FileUtils;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.duraspace.bagit.BagDeserializer;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.SerializationSupport;

/**
 * CollectionPacker packs and unpacks Collection AIPs in BagIt bags
 *
 * @author richardrodgers
 */
public class CollectionPacker implements Packer
{
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    // NB - these values must remain synchronized with DB schema
    // they represent the persistent object state
    private static final String[] fields =
    {
        "name",
        "short_description",
        "introductory_text",
        "provenance_description",
        "license",
        "copyright_text",
        "side_bar_text"
    };

    private Collection collection = null;
    private String archFmt = null;

    public CollectionPacker(Collection collection, String archFmt)
    {
        this.collection = collection;
        this.archFmt = archFmt;
    }

    public Collection getCollection()
    {
        return collection;
    }

    public void setCollection(Collection collection)
    {
        this.collection = collection;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        final Bitstream logo = collection.getLogo();

        // collect the object.properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_AIP);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + OBJ_TYPE_COLLECTION);
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + collection.getHandle());
        final List<Community> communities = collection.getCommunities();
        if (!communities.isEmpty()) {
            final Community parent = communities.get(0);
            objectProperties.add(OWNER_ID + PROPERTIES_DELIMITER + parent.getHandle());
        }
        final Map<String, List<String>> properties = ImmutableMap.of(OBJFILE, objectProperties);

        // collect the xml metadata
        final List<XmlElement> elements = new ArrayList<>();
        for (String field : fields) {
            final String metadata = collectionService.getMetadata(collection, field);
            final XmlElement element = new XmlElement(metadata, ImmutableMap.of(XML_NAME_KEY, field));
            elements.add(element);
        }

        final BagItAipWriter writer = new BagItAipWriter(packDir, archFmt, logo, properties, elements,
                                                         Collections.<BagBitstream>emptyList());
        return writer.packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null) {
            throw new IOException("Missing archive for collection: " + collection.getHandle());
        }

        final Path bagPath;
        if (archive.isFile()) {
            // todo: this might fail, might want to push to BagProfile
            final URL url = this.getClass().getResource(bagProfile);
            final BagProfile profile = new BagProfile(url.openStream());
            final BagDeserializer deserializer = SerializationSupport.deserializerFor(archive.toPath(), profile);
            bagPath = deserializer.deserialize(archive.toPath());
        } else {
            bagPath = archive.toPath();
        }

        final XMLStreamReader reader;
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        final Path metadataXml = bagPath.resolve("data").resolve("metadata.xml");
        try {
            reader = factory.createXMLStreamReader(Files.newInputStream(metadataXml));
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        List<XmlElement> elements = new ArrayList<>();
        try {
            // todo: push this somewhere else
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT &&
                    reader.getLocalName().equalsIgnoreCase("metadata")) {
                    String body = null;
                    Map<String, String> attributes = null;
                    while (reader.hasNext()) {
                        switch (reader.next()) {
                            case XMLStreamConstants.START_ELEMENT:
                                attributes = new HashMap<>();
                                for (int i = 0; i < reader.getAttributeCount(); i++) {
                                    attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                                }
                                break;
                            case XMLStreamConstants.ATTRIBUTE:
                                break;
                            case XMLStreamConstants.CHARACTERS:
                                body = reader.getText();
                                break;
                            case XMLStreamConstants.END_ELEMENT:
                                elements.add(new XmlElement(body, attributes));
                                break;
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        // todo: this can completely break the metadata of a collection in dspace
        for (XmlElement element : elements) {
            final String name = element.getAttributes().get("name");
            final String value = element.getBody();
            collectionService.setMetadata(Curator.curationContext(), collection, name, value);
        }

        final Path logo = bagPath.resolve("data").resolve("logo");
        // todo: do we need to set to null?
        if (Files.exists(logo)) {
            collectionService.setLogo(Curator.curationContext(), collection, Files.newInputStream(logo));
        }

        collectionService.update(Curator.curationContext(), collection);

        FileUtils.deleteDirectory(bagPath.toFile());
    }

    @Override
    public long size(String method) throws SQLException
    {
        long size = 0L;
        // start with logo size, if present
        Bitstream logo = collection.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        // proceed to items, unless 'norecurse' set
        if (! "norecurse".equals(method))
        {
            Iterator<Item> itemIter = itemService.findByCollection(Curator.curationContext(), collection);
            ItemPacker iPup = null;
            while (itemIter.hasNext())
            {
                if (iPup == null)
                {
                    iPup = (ItemPacker)PackerFactory.instance(itemIter.next());
                }
                else
                {
                    iPup.setItem(itemIter.next());
                }
                size += iPup.size(method);
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
