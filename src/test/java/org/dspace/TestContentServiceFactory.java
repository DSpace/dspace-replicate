package org.dspace;

import static org.mockito.Mockito.mock;

import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamFormatService;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.DSpaceObjectLegacySupportService;
import org.dspace.content.service.DSpaceObjectService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.content.service.MetadataValueService;
import org.dspace.content.service.SiteService;
import org.dspace.content.service.SupervisedItemService;
import org.dspace.content.service.WorkspaceItemService;

/**
 * The bottom of the food chain. Return mocks.
 *
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
}
