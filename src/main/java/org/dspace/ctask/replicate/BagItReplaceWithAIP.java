/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.PackageUtils;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.SiteService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * BagItReplaceWithAIP task will instate the replica representation of the object in
 * place of the current (repository) one.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Mutative
public class BagItReplaceWithAIP extends AbstractCurationTask {

    private String archFmt;

    // Group where all AIPs are stored
    private String storeGroupName;

    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private SiteService siteService = ContentServiceFactory.getInstance().getSiteService();

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
    }
    
    /**
     * Perform the 'Replace with AIP' task.
     * <P>
     * Actually overwrite any existing object data in the repository with
     * whatever information is contained in the AIP.
     * @param dso the DSpace object to replace
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        final ReplicaManager repMan = ReplicaManager.instance();
        
        // overwrite with AIP data
        try {
            Context context = Curator.curationContext();
            final Packer packer = PackerFactory.instance(context, dso);
            int status = Curator.CURATE_FAIL;
            String result = null;
            String objId = repMan.storageId(context, dso.getHandle(), archFmt);
            File archive = repMan.fetchObject(context, storeGroupName, objId);
            if (archive != null) {
                // clear object where necessary
                if (dso.getType() == Constants.ITEM) {
                    PackageUtils.removeAllBitstreams(context, dso);
                    PackageUtils.clearAllMetadata(context, dso);
                }
                packer.unpack(archive);
                // now update the dso
                int type = dso.getType();
                if (type == Constants.ITEM) {
                    itemService.update(context, (Item) dso);
                } else if (type == Constants.COLLECTION) {
                    collectionService.update(context, (Collection) dso);
                } else if (type == Constants.COMMUNITY) {
                    communityService.update(context, (Community) dso);
                } else if (type == Constants.SITE) {
                    siteService.update(context, (Site) dso);
                }
                status = Curator.CURATE_SUCCESS;
                result = "Object: " + dso.getHandle() + " replaced from AIP";
            } else {
                result = "Failed to replace Object. AIP could not be found in Replica Store.";
            }
            report(result);
            setResult(result);
            return status;
        } catch (AuthorizeException | SQLException e) {
            throw new IOException(e);
        }
    }
}
