/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static java.lang.Boolean.TRUE;
import static org.dspace.pack.PackerFactory.BAG_TYPE;
import static org.dspace.pack.PackerFactory.OBJECT_ID;
import static org.dspace.pack.PackerFactory.OBJECT_TYPE;
import static org.dspace.pack.PackerFactory.OBJFILE;
import static org.dspace.pack.PackerFactory.OTHER_IDS;
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.PackerFactory.WITHDRAWN;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_AIP;
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_ITEM;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.bagit.xml.Element;
import org.dspace.pack.bagit.xml.Metadata;
import org.dspace.pack.bagit.xml.Policy;
import org.dspace.pack.bagit.xml.Value;

/**
 * ItemPacker packs and unpacks Item AIPs in BagIt bag compressed archives
 *
 * @author richardrodgers
 */
public class ItemPacker implements Packer {
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
    public File pack(final File packDir) throws AuthorizeException, IOException, SQLException {
        final BagItPolicyUtil policyUtil = new BagItPolicyUtil();

        // object properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_AIP);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + OBJ_TYPE_ITEM);
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + item.getHandle());

        final StringBuilder linked = new StringBuilder();
        for (Collection coll : item.getCollections()) {
            if (itemService.isOwningCollection(item, coll)) {
                objectProperties.add(OWNER_ID + PROPERTIES_DELIMITER + coll.getHandle());
            } else {
                linked.append(coll.getHandle()).append(",");
            }
        }
        if (linked.length() > 0) {
            objectProperties.add(OTHER_IDS + PROPERTIES_DELIMITER + linked.substring(0, linked.length() - 1));
        }
        if (item.isWithdrawn()) {
            objectProperties.add(WITHDRAWN + PROPERTIES_DELIMITER + TRUE.toString());
        }
        final ImmutableMap<String, List<String>> properties = ImmutableMap.of(OBJFILE, objectProperties);

        // metadata.xml
        final Metadata metadata = new Metadata();
        final List<MetadataValue> itemMetadata = itemService.getMetadata(item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
        for (MetadataValue value : itemMetadata) {
            final HashMap<String, String> attributes = new HashMap<>();
            attributes.put(SCHEMA, value.getMetadataField().getMetadataSchema().getName());
            attributes.put(ELEMENT, value.getMetadataField().getElement());
            attributes.put(QUALIFIER, value.getMetadataField().getQualifier());
            attributes.put(LANGUAGE, value.getLanguage());
            metadata.addChild(new Value(value.getValue(), attributes));
        }

        // policy.xml
        final Policy policy = policyUtil.getPolicy(Curator.curationContext(), item);

        // proceed to bundles, in sub-directories, filtering
        final List<BagBitstream> bitstreams = new ArrayList<>();
        for (Bundle bundle : item.getBundles()) {
            bundle.getResourcePolicies();
            final String bundleName = bundle.getName();
            if (accept(bundleName)) {
                // only bundle metadata is the primary bitstream - remember it
                // and place in bitstream metadata if defined
                for (Bitstream bs : bundle.getBitstreams()) {
                    // write metadata to xml file
                    final String seqId = String.valueOf(bs.getSequenceID());

                    // field access is hard-coded in Bitstream class, ugh!
                    final Metadata bitstreamMetadata = new Metadata();
                    bitstreamMetadata.addChild(new Value(bs.getName(), ImmutableMap.of(NAME, NAME)));
                    bitstreamMetadata.addChild(new Value(bs.getSource(), ImmutableMap.of(NAME, SOURCE)));
                    bitstreamMetadata.addChild(new Value(bs.getDescription(), ImmutableMap.of(NAME, DESCRIPTION)));
                    bitstreamMetadata.addChild(new Value(seqId, ImmutableMap.of(NAME, SEQUENCE_ID)));
                    if (bs.equals(bundle.getPrimaryBitstream())) {
                        bitstreamMetadata.addChild(new Value(TRUE.toString(), ImmutableMap.of(NAME, BUNDLE_PRIMARY)));
                    }

                    // bitstream policy
                    final Policy bitstreamPolicy = policyUtil.getPolicy(Curator.curationContext(), bs);

                    // write the bitstream itself, unless reference filter applies
                    final String fetchUrl = byReference(bundle, bs);
                    if (fetchUrl != null) {
                        bitstreams.add(new BagBitstream(fetchUrl, bs, bundleName, bitstreamPolicy, bitstreamMetadata));
                    } else {
                        bitstreams.add(new BagBitstream(bs, bundleName, bitstreamPolicy, bitstreamMetadata));
                    }
                }
            }
        }

        final BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, null, properties, metadata, policy,
                                                            bitstreams);
        return aipWriter.packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || !archive.exists()) {
            throw new IOException("Missing archive for item: " + item.getHandle());
        }

        final Context context = Curator.curationContext();
        final BagItAipReader reader = new BagItAipReader(archive.toPath());
        reader.validateBag();

        // load the item metadata
        final Metadata metadata = reader.readMetadata();
        for (Element element : metadata.getChildren()) {
            final Map<String, String> attrs = element.getAttributes();
            itemService.addMetadata(context, item,
                                    attrs.get(SCHEMA),
                                    attrs.get(ELEMENT),
                                    attrs.get(QUALIFIER),
                                    attrs.get(LANGUAGE),
                                    element.getBody());
        }

        final List<PackagedBitstream> bitstreams = reader.findBitstreams();
        for (PackagedBitstream packaged : bitstreams) {
            // get or create a bundle
            final Bundle bundle;
            final List<Bundle> bundles = itemService.getBundles(item, packaged.getBundle());
            if (bundles != null && !bundles.isEmpty()) {
                bundle = bundles.get(0);
            } else {
                bundle = bundleService.create(context, item, packaged.getBundle());
            }

            // create a bitstream
            final Bitstream bitstream = bitstreamService.create(context, bundle,
                                                                Files.newInputStream(packaged.getBitstream()));

            // load the bitstream metadata
            for (Element element : packaged.getMetadata().getChildren()) {
                final String bitstreamField = element.getAttributes().get(NAME);
                if (NAME.equalsIgnoreCase(bitstreamField)) {
                    bitstream.setName(context, element.getBody());
                } else if (SOURCE.equalsIgnoreCase(bitstreamField)) {
                    bitstream.setSource(context, element.getBody());
                } else if (SEQUENCE_ID.equalsIgnoreCase(bitstreamField)) {
                    bitstream.setSequenceID(Integer.parseInt(element.getBody()));
                } else if (DESCRIPTION.equalsIgnoreCase(bitstreamField)) {
                    bitstream.setDescription(context, element.getBody());
                } else if (BUNDLE_PRIMARY.equalsIgnoreCase(bitstreamField)) {
                    bundle.setPrimaryBitstreamID(bitstream);
                }
            }

            bitstreamService.update(context, bitstream);
        }

        reader.clean();
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
