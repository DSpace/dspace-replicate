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
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
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

    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
    
    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    // Group where all AIPs are temporarily moved when deleted
    private final String deleteGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");

    /**
     * Removes replicas of passed object from the replica store.
     * If a container, removes all the member replicas, in addition
     * to the replica of the container object. No change is made to
     * the DSPace object itself.
     * 
     * @param dso the DSpace object
     * @return integer which represents Curator return status
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException 
    {
        ReplicaManager repMan = ReplicaManager.instance();
        remove(repMan, dso);
        setResult("AIP for '" + dso.getHandle() + "' has been removed");
        return Curator.CURATE_SUCCESS;
    }

    /**
     * Remove replica(s) of the passed in DSpace object from a particular
     * replica ObjectStore.
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param dso the DSpace object whose replicas we will remove
     * @throws IOException 
     */
    private void remove(ReplicaManager repMan, DSpaceObject dso) throws IOException 
    {
        //Remove object from AIP storage
        String objId = repMan.storageId(dso.getHandle(), archFmt);
        repMan.removeObject(storeGroupName, objId);
        report("Removing AIP for: " + objId);
        
        //If it is a Collection, also remove all Items from AIP storage
        if (dso instanceof Collection) {
            Collection coll = (Collection)dso;
            try {
                ItemIterator iter = coll.getItems();
                while (iter.hasNext()) {
                    remove(repMan, iter.next());
                }
            } catch (SQLException sqlE) {
                throw new IOException(sqlE);
            }
        } // else if it a Community, also remove all sub-communities, collections (and items) from AIP storage 
        else if (dso instanceof Community) {
            Community comm = (Community)dso;
            try {
                for (Community subcomm : comm.getSubcommunities()) {
                    remove(repMan, subcomm);
                }
                for (Collection coll : comm.getCollections()) {
                    remove(repMan, coll);
                }
            } catch (SQLException sqlE) {
                throw new IOException(sqlE);
            }
        } //else if it is a Site object, remove all top-level communities (and everything else) from AIP storage
        else if (dso instanceof Site) {
            try {
                Community[] topCommunities = Community.findAllTop(Curator.curationContext());
                
                for (Community subcomm : topCommunities) {
                    remove(repMan, subcomm);
                }
            } catch (SQLException sqlE) {
                throw new IOException(sqlE);
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
     * @throws IOException
     */
    @Override
    public int perform(Context ctx, String id) throws IOException 
    {
        ReplicaManager repMan = ReplicaManager.instance();
        DSpaceObject dso = dereference(ctx, id);
        if (dso != null) {
            return perform(dso);
        }
        // treat as a deletion GC
        String objId = repMan.storageId(id, archFmt);
        int status = Curator.CURATE_FAIL;
        File catFile = repMan.fetchObject(deleteGroupName, objId);
        if (catFile != null) {
            CatalogPacker cpack = new CatalogPacker(id);
            cpack.unpack(catFile);
            // remove the object, then all members, last of all the deletion catalog
            repMan.removeObject(storeGroupName, objId);
            for (String mem : cpack.getMembers()) {
                String memId = repMan.storageId(mem, archFmt);
                repMan.removeObject(storeGroupName, memId);
            }
            repMan.removeObject(deleteGroupName, objId);
            status = Curator.CURATE_SUCCESS;
        }
        return status;
    }
}
