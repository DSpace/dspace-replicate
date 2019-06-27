/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;

import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import static org.dspace.pack.PackerFactory.*;

/**
 * ItemPacker packs and unpacks Item AIPs in BagIt bag compressed archives
 *
 * @author richardrodgers
 */
public class ItemPacker implements Packer
{
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private Item item = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<String>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<RefFilter>();

    public ItemPacker(Item item, String archFmt)
    {
        this.item = item;
        this.archFmt = archFmt;
    }

    public Item getItem()
    {
        return item;
    }

    public void setItem(Item item)
    {
        this.item = item;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException
    {
        Bag bag = new Bag(packDir);
        // set base object properties
        Bag.FlatWriter fwriter = bag.flatWriter(OBJFILE);
        fwriter.writeProperty(BAG_TYPE, "AIP");
        fwriter.writeProperty(OBJECT_TYPE, "item");
        fwriter.writeProperty(OBJECT_ID, item.getHandle());
        // get collections
        StringBuilder linked = new StringBuilder();
        for (Collection coll : item.getCollections())
        {
            if (itemService.isOwningCollection(item, coll))
            {
                fwriter.writeProperty(OWNER_ID, coll.getHandle());
            }
            else
            {
                linked.append(coll.getHandle()).append(",");
            }
        }
        String linkedStr = linked.toString();
        if (linkedStr.length() > 0)
        {
            fwriter.writeProperty(OTHER_IDS, linkedStr.substring(0, linkedStr.length() - 1));
        }
        if (item.isWithdrawn())
        {
            fwriter.writeProperty(WITHDRAWN, "true");
        }
        fwriter.close();

        // start with metadata
        Bag.XmlWriter writer = bag.xmlWriter("metadata.xml");
        // first user metadata
        writer.startStanza("metadata");
        Bag.Value value = new Bag.Value();
        List<MetadataValue> vals = itemService.getMetadata(item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        for (MetadataValue val : vals)
        {
            value.addAttr("schema", val.getMetadataField().getMetadataSchema().getName());
            value.addAttr("element", val.getMetadataField().getElement());
            value.addAttr("qualifier", val.getMetadataField().getQualifier());
            value.addAttr("language", val.getLanguage());
            value.val = val.getValue();
            writer.writeValue(value);
        }
        writer.endStanza();
        writer.close();
        // proceed to bundles, in sub-directories, filtering
        for (Bundle bundle : item.getBundles())
        {
            if (accept(bundle.getName()))
            {
                // only bundle metadata is the primary bitstream - remember it
                // and place in bitstream metadata if defined
                UUID primaryId = bundle.getPrimaryBitstream().getID();
                for (Bitstream bs : bundle.getBitstreams())
                {
                    // write metadata to xml file
                    String seqId = String.valueOf(bs.getSequenceID());
                    String relPath = bundle.getName() + "/";
                    writer = bag.xmlWriter(relPath + seqId + "-metadata.xml");
                    writer.startStanza("metadata");
                    // field access is hard-coded in Bitstream class, ugh!
                    writer.writeValue("name", bs.getName());
                    writer.writeValue("source", bs.getSource());
                    writer.writeValue("description", bs.getDescription());
                    writer.writeValue("sequence_id", seqId);
                    if (bs.getID() == primaryId)
                    {
                       writer.writeValue("bundle_primary", "true"); 
                    }
                    writer.endStanza();
                    writer.close();
                    // write the bitstream itself, unless reference filter applies
                    String url = byReference(bundle, bs);
                    if (url != null)
                    {
                        // add reference to bag
                        bag.addDataRef(relPath + seqId, bs.getSizeBytes(), url);
                    }
                    else
                    {
                        // add bytes to bag
                        bag.addData(relPath + seqId, bs.getSizeBytes(), bitstreamService.retrieve(Curator.curationContext(), bs));
                    }
                }
            }
        }
        bag.close();
        File archive = bag.deflate(archFmt);
        bag.empty();
        return archive;
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null || ! archive.exists())
        {
            throw new IOException("Missing archive for item: " + item.getHandle());
        }
        Bag bag = new Bag(archive);
        // add the metadata first
        Bag.XmlReader reader = bag.xmlReader("metadata.xml");
        if (reader != null && reader.findStanza("metadata"))
        {
            Bag.Value value = null;
            while((value = reader.nextValue()) != null)
            {
                itemService.addMetadata(Curator.curationContext(),
                                 item,
                                 value.attrs.get("schema"),
                                 value.attrs.get("element"),
                                 value.attrs.get("qualifier"),
                                 value.attrs.get("language"),
                                 value.val);
            }
            reader.close();
        }
        // proceed to bundle data & metadata
        for (File bfile : bag.listDataFiles())
        {
            // only bundles are directories
            if (! bfile.isDirectory())
            {
                continue;
            }
            Bundle bundle = bundleService.create(Curator.curationContext(), item, bfile.getName());
            for (File file : bfile.listFiles(new FileFilter() {
                            public boolean accept(File file) {
                                return ! file.getName().endsWith(".xml");
                            }
            })) {
                String relPath = bundle.getName() + File.separator + file.getName();
                InputStream in = bag.dataStream(relPath);
                if (in != null)
                {
                    Bitstream bs = bitstreamService.create(Curator.curationContext(), bundle, in);
                    // now set bitstream metadata
                    reader = bag.xmlReader(relPath + "-metadata.xml");
                    if (reader != null && reader.findStanza("metadata"))
                    {
                        Bag.Value value = null;
                        // field access is hard-coded in Bitstream class
                        while((value = reader.nextValue()) != null)
                        {
                            String name = value.name;
                            if ("name".equals(name))
                            {
                                bs.setName(Curator.curationContext(), value.val);
                            }
                            else if ("source".equals(name))
                            {
                                bs.setSource(Curator.curationContext(), value.val);
                            }
                            else if ("description".equals(name))
                            {
                                bs.setDescription(Curator.curationContext(), value.val);
                            }
                            else if ("sequence_id".equals(name))
                            {
                                bs.setSequenceID(Integer.valueOf(value.val));
                            }
                            else if ("bundle_primary".equals(name))
                            {
                                // special case - bundle metadata in bitstream
                                bundle.setPrimaryBitstreamID(bs);
                            }
                        }
                        reader.close();
                    }
                    else
                    {
                        String missing = relPath + "-metadata.xml";
                        throw new IOException("Cannot locate bitstream metadata file: " + missing);
                    }
                    bitstreamService.update(Curator.curationContext(), bs);
                }
                in.close();
            }
        }
        // clean up bag
        bag.empty();
    }

    @Override
    public long size(String method) throws SQLException
    {
        long size = 0L;
        // just total bitstream sizes, respecting filters
        for (Bundle bundle : item.getBundles())
        {
            if (accept(bundle.getName()))
            {
                for (Bitstream bs : bundle.getBitstreams())
                {
                    size += bs.getSizeBytes();
                }
            }
        }
        return size;
    }
    
    @Override
    public void setContentFilter(String filter) 
    {
        //If our filter list of bundles begins with a '+', then this list
        // specifies all the bundles to *include*. Otherwise all 
        // bundles *except* the listed ones are included
        if(filter.startsWith("+"))
        {
            exclude = false;
            //remove the preceding '+' from our bundle list
            filter = filter.substring(1);
        }
        
        filterBundles = Arrays.asList(filter.split(","));
    }

    private boolean accept(String name)
    {
        boolean onList = filterBundles.contains(name);
        return exclude ? ! onList : onList;
    }

    @Override
    public void setReferenceFilter(String filterSet)
    {
        // parse ref filter list
        for (String filter : filterSet.split(","))
        {
            refFilters.add(new RefFilter(filter));
        }
    }

    private String byReference(Bundle bundle, Bitstream bs)
    {
        for (RefFilter filter : refFilters)
        {
            if (filter.bundle.equals(bundle.getName()) &&
                filter.size == bs.getSizeBytes())
            {
                return filter.url;
            }
        }
        return null;
    }

    private class RefFilter
    {
        public String bundle;
        public long size;
        public String url;

        public RefFilter(String filter)
        {
            String[] parts = filter.split(" ");
            bundle = parts[0];
            size = Long.valueOf(parts[1]);
            url = parts[2];
        }
    }
}
