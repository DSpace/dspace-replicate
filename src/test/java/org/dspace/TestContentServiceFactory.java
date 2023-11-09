/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import static org.mockito.Mockito.mock;

import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.content.RelationshipMetadataService;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.DSpaceObjectLegacySupportService;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.EntityService;
import org.dspace.content.service.EntityTypeService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.RelationshipService;
import org.dspace.content.service.RelationshipTypeService;
import org.dspace.content.service.SiteService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.eperson.service.SubscribeService;

/**
 * A {@link ContentServiceFactory} which returns mock services
 *
 * @author mikejritter
 */
public class TestContentServiceFactory extends ContentServiceFactory {

    public static final String CONTENT_SERVICE_FACTORY = "contentServiceFactory";

    private final SiteService siteService = mock(SiteService.class);
    private final BitstreamService bitstreamService = mock(BitstreamService.class);
    private final BitstreamFormatService bitstreamFormatService = mock(BitstreamFormatService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final InstallItemService installItemService = mock(InstallItemService.class);
    private final WorkspaceItemService workspaceItemService = mock(WorkspaceItemService.class);
    private final CollectionService collectionService = mock(CollectionService.class);
    private final CommunityService communityService = mock(CommunityService.class);
    private final BundleService bundleService = mock(BundleService.class);

    @Override
    public List<DSpaceObjectService<? extends DSpaceObject>> getDSpaceObjectServices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<DSpaceObjectLegacySupportService<? extends DSpaceObject>> getDSpaceObjectLegacySupportServices() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BitstreamFormatService getBitstreamFormatService() {
        return bitstreamFormatService;
    }

    @Override
    public BitstreamService getBitstreamService() {
        return bitstreamService;
    }

    @Override
    public BundleService getBundleService() {
        return bundleService;
    }

    @Override
    public CollectionService getCollectionService() {
        return collectionService;
    }

    @Override
    public CommunityService getCommunityService() {
        return communityService;
    }

    @Override
    public ItemService getItemService() {
        return itemService;
    }

    @Override
    public MetadataFieldService getMetadataFieldService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetadataSchemaService getMetadataSchemaService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MetadataValueService getMetadataValueService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkspaceItemService getWorkspaceItemService() {
        return workspaceItemService;
    }

    @Override
    public InstallItemService getInstallItemService() {
        return installItemService;
    }

    @Override
    public SiteService getSiteService() {
        return siteService;
    }

    @Override
    public SubscribeService getSubscribeService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RelationshipTypeService getRelationshipTypeService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RelationshipService getRelationshipService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityTypeService getEntityTypeService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EntityService getEntityService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RelationshipMetadataService getRelationshipMetadataService() {
        throw new UnsupportedOperationException();
    }
}
