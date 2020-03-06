/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.google.common.collect.ImmutableMap;
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
        objectProperties.add(PackerFactory.BAG_TYPE + "  " + BagItAipWriter.BAG_AIP);
        objectProperties.add(PackerFactory.OBJECT_TYPE + "  " + BagItAipWriter.OBJ_TYPE_COLLECTION);
        objectProperties.add(PackerFactory.OBJECT_ID + "  " + collection.getHandle());
        final Community parent = collection.getCommunities().get(0);
        if (parent != null) {
            objectProperties.add(PackerFactory.OWNER_ID + "  " + parent.getHandle());
        }
        final Map<String, List<String>> properties = ImmutableMap.of(PackerFactory.OBJFILE, objectProperties);

        // collect the xml metadata
        final List<XmlElement> elements = new ArrayList<>();
        for (String field : fields) {
            final String metadata = collectionService.getMetadata(collection, field);
            final XmlElement element = new XmlElement(metadata, ImmutableMap.of(BagItAipWriter.XML_NAME_KEY, field));
            elements.add(element);
        }

        BagItAipWriter writer = new BagItAipWriter(packDir, archFmt, logo, properties, elements,
                                                   Collections.<BagBitstream>emptyList());
        return writer.packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null)
        {
            throw new IOException("Missing archive for collection: " + collection.getHandle());
        }
        Bag bag = new Bag(archive);
        // add the metadata
        Bag.XmlReader reader = bag.xmlReader("metadata.xml");
        if (reader != null && reader.findStanza("metadata"))
        {
            Bag.Value value = null;
            while((value = reader.nextValue()) != null)
            {
                collectionService.setMetadata(Curator.curationContext(), collection, value.name, value.val);
            }
            reader.close();
        }
          // also install logo or set to null
        collectionService.setLogo(Curator.curationContext(), collection, bag.dataStream("logo"));
        // now write data back to DB
        collectionService.update(Curator.curationContext(), collection);
         // clean up bag
        bag.empty();
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
