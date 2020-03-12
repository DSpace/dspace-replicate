/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static java.lang.Boolean.TRUE;
import static org.dspace.pack.PackerFactory.OTHER_IDS;
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.PackerFactory.WITHDRAWN;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

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

    // XML constants
    private static final String SCHEMA = "schema";
    private static final String ELEMENT = "element";
    private static final String QUALIFIER = "qualifier";
    private static final String LANGUAGE = "language";
    private static final String NAME = "name";
    private static final String SOURCE = "source";
    private static final String DESCRIPTION = "description";
    private static final String SEQUENCE_ID = "sequence_id";
    private static final String BUNDLE_PRIMARY = "bundle_primary";

    private Item item = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<>();

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
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        // object properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(PackerFactory.BAG_TYPE + "  " + BagItAipWriter.BAG_AIP);
        objectProperties.add(PackerFactory.OBJECT_TYPE + "  " + BagItAipWriter.OBJ_TYPE_ITEM);
        objectProperties.add(PackerFactory.OBJECT_ID + "  " + item.getHandle());

        StringBuilder linked = new StringBuilder();
        for (Collection coll : item.getCollections()) {
            if (itemService.isOwningCollection(item, coll)) {
                objectProperties.add(OWNER_ID + "  " + coll.getHandle());
            } else {
                linked.append(coll.getHandle()).append(",");
            }
        }
        if (linked.length() > 0) {
            // todo: why substring?? is this not just printing the entire string...
            objectProperties.add(OTHER_IDS + "  " + linked.substring(0, linked.length() - 1));
        }
        if (item.isWithdrawn()) {
            objectProperties.add(WITHDRAWN + "  " + TRUE.toString());
        }
        ImmutableMap<String, List<String>> properties = ImmutableMap.of(PackerFactory.OBJFILE, objectProperties);

        // metadata.xml
        final List<XmlElement> metadataElements = new ArrayList<>();
        final List<MetadataValue> metadata = itemService.getMetadata(item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        for (MetadataValue value : metadata) {
            final HashMap<String, String> attributes = new HashMap<>();
            attributes.put(SCHEMA, value.getMetadataField().getMetadataSchema().getName());
            attributes.put(ELEMENT, value.getMetadataField().getElement());
            attributes.put(QUALIFIER, value.getMetadataField().getQualifier());
            attributes.put(LANGUAGE, value.getLanguage());
            metadataElements.add(new XmlElement(value.getValue(), attributes));
        }

        // proceed to bundles, in sub-directories, filtering
        final List<BagBitstream> bitstreams = new ArrayList<>();
        for (Bundle bundle : item.getBundles()) {
            if (accept(bundle.getName())) {
                // only bundle metadata is the primary bitstream - remember it
                // and place in bitstream metadata if defined
                for (Bitstream bs : bundle.getBitstreams()) {
                    // write metadata to xml file
                    final String seqId = String.valueOf(bs.getSequenceID());

                    // field access is hard-coded in Bitstream class, ugh!
                    List<XmlElement> bsElements = new ArrayList<>();
                    bsElements.add(new XmlElement(bs.getName(), ImmutableMap.of(NAME, NAME)));
                    bsElements.add(new XmlElement(bs.getSource(), ImmutableMap.of(NAME, SOURCE)));
                    bsElements.add(new XmlElement(bs.getDescription(), ImmutableMap.of(NAME, DESCRIPTION)));
                    bsElements.add(new XmlElement(seqId, ImmutableMap.of(NAME, SEQUENCE_ID)));
                    if (bs.equals(bundle.getPrimaryBitstream())) {
                        bsElements.add(new XmlElement(TRUE.toString(), ImmutableMap.of(NAME, BUNDLE_PRIMARY)));
                    }

                    // write the bitstream itself, unless reference filter applies
                    String fetchUrl = byReference(bundle, bs);
                    if (fetchUrl != null) {
                        bitstreams.add(new BagBitstream(fetchUrl, bundle.getName(), bsElements));
                    } else {
                        bitstreams.add(new BagBitstream(bs, bundle.getName(), bsElements));
                    }
                }
            }
        }

        final BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, null, properties, metadataElements,
                                                            bitstreams);
        return aipWriter.packageAip();
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
                    size += bs.getSize();
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
                filter.size == bs.getSize())
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
