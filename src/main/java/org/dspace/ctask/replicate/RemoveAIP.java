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
import java.util.Iterator;
import java.util.List;

import org.dspace.content.*;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

import org.dspace.pack.bagit.CatalogPacker;

/**
 * RemoveAIP task will remove requested objects from the replica store. If the
 * object is a container, all its children (members) will also be removed.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
public class RemoveAIP extends AbstractCurationTask {

    private String archFmt;

    // Group where all AIPs are stored
    private String storeGroupName;
    
    // Group where object deletion catalog/records are stored
    private String deleteGroupName;

    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
        deleteGroupName = configurationService.getProperty("replicate.group.delete.name");
    }

    /**
     * Removes replicas of passed object from the replica store.
     * If a container, removes all the member replicas, in addition
     * to the replica of the container object. No change is made to
     * the DSPace object itself.
     * 
     * @param dso the DSpace object
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException 
    {
        ReplicaManager repMan = ReplicaManager.instance();
        try {
            remove(Curator.curationContext(), repMan, dso);
        } catch (SQLException e) {
            throw new IOException(e);
        }
        setResult("AIP for '" + dso.getHandle() + "' has been removed");
        return Curator.CURATE_SUCCESS;
    }

    /**
     * Remove replica(s) of the passed in DSpace object from a particular
     * replica ObjectStore.
     *
     * @param context the context to use
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param dso the DSpace object whose replicas we will remove
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    private void remove(Context context, ReplicaManager repMan, DSpaceObject dso) throws IOException, SQLException
    {
        //Remove object from AIP storage
        String objId = repMan.storageId(context, dso.getHandle(), archFmt);
        repMan.removeObject(storeGroupName, objId);
        report("Removing AIP for: " + objId);

        //If it is a Collection, also remove all Items from AIP storage
        if (dso instanceof Collection) {
            Collection coll = (Collection) dso;
            Iterator<Item> iter = itemService.findByCollection(context, coll);
            while (iter.hasNext()) {
                remove(context, repMan, iter.next());
            }
        } // else if it a Community, also remove all sub-communities, collections (and items) from AIP storage
        else if (dso instanceof Community) {
            Community comm = (Community) dso;
            for (Community subcomm : comm.getSubcommunities()) {
                remove(context, repMan, subcomm);
            }
            for (Collection coll : comm.getCollections()) {
                remove(context, repMan, coll);
            }
        } //else if it is a Site object, remove all top-level communities (and everything else) from AIP storage
        else if (dso instanceof Site) {
            List<Community> topCommunities = communityService.findAllTop(context);

            for (Community subcomm : topCommunities) {
                remove(context, repMan, subcomm);
            }
        }
    }

    /**
     * Removes replicas of passed id from the replica store. This can act in
     * one of two ways: either there is an existing DSpace Object with
     * this id, in which case it behaves like the previous method, or there
     * is no DSpace Object, in which case we assume that the object has been
     * deleted. In this case, the replica store is purged of the deleted
     * object, or objects, if the id is (was) a container.
     *
     * @param ctx current DSpace Context
     * @param id Identifier of the object to be removed.
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException 
    {
        ReplicaManager repMan = ReplicaManager.instance();
   
        //If the object is still in DSpace, call perform(dso) instead.
        DSpaceObject dso = dereference(ctx, id);
        if (dso != null) {
            return perform(dso);
        }
        // Otherwise, this object was already previously deleted from DSpace.
        // So, we'll treat this as a deletion "garbage clean"

        // Locate the deletion catalog associated with this object
        // (This catalog should exist, as the object was previously deleted)
        String catId = repMan.deletionCatalogId(id, archFmt);
        int status = Curator.CURATE_FAIL;
        String result;
        File catFile = repMan.fetchObject(ctx, deleteGroupName, catId);
        if (catFile != null) {
            CatalogPacker cpack = new CatalogPacker(ctx, id);
            cpack.unpack(catFile);
            // remove the object AIP itself
            String objId = repMan.storageId(ctx, id, archFmt);
            repMan.removeObject(storeGroupName, objId);
            report("Removing AIP for: " + objId);
            // remove all member/child object's AIPs
            for (String mem : cpack.getMembers()) {
                String memId = repMan.storageId(ctx, mem, archFmt);
                repMan.removeObject(storeGroupName, memId);
                report("Removing AIP for: " + memId);
            }
            
            // remove local deletion catalog
            catFile.delete();
            // remove remote deletion catalog
            repMan.removeObject(deleteGroupName, catId);

            result = "AIP for '" + id + "' has been removed (along with any child object AIPs)";
            status = Curator.CURATE_SUCCESS;
        }
        else
        {
            result = "Deletion record for '" + id + "' could not be found in Replica Store. Perhaps this object's AIP was already removed?";
        }

        setResult(result);
        return status;
    }
}
