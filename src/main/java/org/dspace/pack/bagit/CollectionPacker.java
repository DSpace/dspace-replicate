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
import static org.dspace.pack.bagit.BagItAipWriter.DEFAULT_MODIFIED_DATE;
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_COLLECTION;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.MetadataFieldName;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.PackageException;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.metadata.Value;
import org.dspace.pack.bagit.xml.policy.Policies;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;

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

    private final Context context;
    private Collection collection = null;
    private String archFmt = null;

    public CollectionPacker(Context context, Collection collection, String archFmt)
    {
        this.context = context;
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
        final Metadata metadata = new Metadata();
        for (String field : fields) {
            final String body = collectionService.getMetadata(collection, field);
            metadata.addValue(new Value(body, field));
        }

        // capture the item template if it exists
        Metadata templateMd = null;
        final Item templateItem = collection.getTemplateItem();
        if (templateItem != null) {
            templateMd = new Metadata();
            final List<MetadataValue> templateMetadata =
                itemService.getMetadata(templateItem, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
            for (MetadataValue metadataValue : templateMetadata) {
                templateMd.addValue(new Value(metadataValue));
            }
        }

        // collect xml policy
        final Policies policy = BagItPolicyUtil.getPolicy(context, collection);

        // roles
        DSpaceRoles dSpaceRoles = null;
        try {
            dSpaceRoles = BagItRolesUtil.getDSpaceRoles(context, collection);
        } catch (PackageException exception) {
            throw new IOException(exception);
        }

        return new BagItAipWriter(context, packDir, archFmt, properties).withLogo(logo)
            .withPolicies(policy)
            .withMetadata(metadata)
            .withItemTemplate(templateMd)
            .withDSpaceRoles(dSpaceRoles)
            .withLastModifiedTime(DEFAULT_MODIFIED_DATE)
            .packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || !archive.exists()) {
            throw new IOException("Missing archive for collection: " + collection.getHandle());
        }

        final BagItAipReader reader = new BagItAipReader(archive.toPath());
        reader.validateBag();

        try {
            // Ingest roles first in case there are policies which depend on them
            final Optional<Path> rolesPath = reader.findRoles();
            if (rolesPath.isPresent()) {
                BagItRolesUtil.ingest(context, collection, rolesPath.get());
            }

            final Policies policies = reader.readPolicy();
            BagItPolicyUtil.registerPolicies(context, collection, policies);
        } catch (PackageException e) {
            throw new IOException(e.getMessage(), e);
        }

        final Metadata metadata = reader.readMetadata();
        for (Value value : metadata.getValues()) {
            MetadataFieldName field = value.getMetadataField();
            collectionService.setMetadataSingleValue(context, collection, field.schema, field.element, field.qualifier,
                    value.getLanguage(), value.getBody());
        }

        final Optional<Metadata> templateMetadata = reader.findItemTemplate();
        if (templateMetadata.isPresent()) {
            // overwrite the template item if it exists
            collectionService.removeTemplateItem(context, collection);
            final Item templateItem = itemService.createTemplateItem(context, collection);
            for (Value value : templateMetadata.get().getValues()) {
                itemService.addMetadata(context, templateItem, value.getSchema(), value.getElement(),
                                        value.getQualifier(), value.getLanguage(), value.getBody());
            }
            itemService.update(context, templateItem);
        }

        final Optional<Path> logo = reader.findLogo();
        if (logo.isPresent()) {
            try (InputStream logoStream = Files.newInputStream(logo.get())) {
                collectionService.setLogo(context, collection, logoStream);
            }
        }

        collectionService.update(context, collection);

        reader.clean();
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
            Iterator<Item> itemIter = itemService.findByCollection(context, collection);
            ItemPacker iPup = null;
            while (itemIter.hasNext())
            {
                if (iPup == null)
                {
                    iPup = (ItemPacker)PackerFactory.instance(context, itemIter.next());
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
