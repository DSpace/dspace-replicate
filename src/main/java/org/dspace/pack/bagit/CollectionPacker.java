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
import java.util.Iterator;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;

import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

import static org.dspace.pack.PackerFactory.*;

/**
 * CollectionPacker packs and unpacks Collection AIPs in BagIt bags
 *
 * @author richardrodgers
 */
public class CollectionPacker implements Packer
{
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    // NB - these values must remain synchronized with DB schema
    // they represent the peristent object state
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
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException
    {
        Bag bag = new Bag(packDir);
        // set base object properties
        Bag.FlatWriter fwriter = bag.flatWriter(OBJFILE);
        fwriter.writeProperty(BAG_TYPE, "AIP");
        fwriter.writeProperty(OBJECT_TYPE, "collection");
        fwriter.writeProperty(OBJECT_ID, collection.getHandle());
        Community parent = collection.getCommunities().get(0);
        if (parent != null)
        {
            fwriter.writeProperty(OWNER_ID, parent.getHandle());
        }
        fwriter.close();
        // then metadata
        Bag.XmlWriter writer = bag.xmlWriter("metadata.xml");
        writer.startStanza("metadata");
        for (String field : fields)
        {
            String val = collectionService.getMetadata(collection, field);
            if (val != null)
            {
                writer.writeValue(field, val);
            }
        }
        writer.endStanza();
        writer.close();
        // also add logo if it exists
        Bitstream logo = collection.getLogo();
        if (logo != null)
        {
            bag.addData("logo", logo.getSizeBytes(), bitstreamService.retrieve(Curator.curationContext(), logo));
        }
        bag.close();
        File archive = bag.deflate(archFmt);
        // clean up undeflated bag
        bag.empty();
        return archive;
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
            size += logo.getSizeBytes();
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
