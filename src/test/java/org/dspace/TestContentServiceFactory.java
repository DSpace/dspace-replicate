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
import org.dspace.content.service.*;

/**
 * A {@link ContentServiceFactory} which returns mock services
 *
 * @author mikejritter
 */
public class TestContentServiceFactory extends ContentServiceFactory {

    public static final String CONTENT_SERVICE_FACTORY = "contentServiceFactory";

    private BitstreamService bitstreamService = mock(BitstreamService.class);
    private ItemService itemService = mock(ItemService.class);
    private CollectionService collectionService = mock(CollectionService.class);
    private CommunityService communityService = mock(CommunityService.class);
    private BundleService bundleService = mock(BundleService.class);

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
        throw new UnsupportedOperationException();
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
        throw new UnsupportedOperationException();
    }

    @Override
    public InstallItemService getInstallItemService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SupervisedItemService getSupervisedItemService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SiteService getSiteService() {
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
