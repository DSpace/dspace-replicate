/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import org.dspace.content.Collection;
import org.dspace.content.Community;

import org.dspace.content.DSpaceObject;
import org.dspace.content.ItemIterator;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * RemoveManifest task will remove the manifest of requested objects from the
 * replica store. If the manifest is multi-level, all the manifests of its 
 * children (members) will also be removed.
 * <p>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 * @see TransmitManifest
 */
@Distributive
public class RemoveManifest extends AbstractCurationTask {

    // Group where all Manifests are stored
    private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");
    
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
        setResult("Manifest for '" + dso.getHandle() + "' has been removed");
        return Curator.CURATE_SUCCESS;
    }

    /**
     * Removes a DSpace Object's Manifest from the Replica ObjectStore.
     * If object has any associated child objects, their existing manifests
     * are also removed from the Replica ObjectStore.
     * <p>
     * NOTE: this method does NOT remove the DSpace Object itself (nor any 
     * children) from the DSpace repository. It only removes the associated
     * manifests from the Replica ObjectStore.
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param dso the DSpace Object
     * @throws IOException 
     */
    private void remove(ReplicaManager repMan, DSpaceObject dso) throws IOException 
    {    
        String objId = ReplicaManager.safeId(dso.getHandle());
        repMan.removeObject(manifestGroupName, objId);
        report("Removing manifest for: " + objId);
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
        } else if (dso instanceof Community) {
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
        }
    }

    /**
     * Removes replicas of passed id from the replica store. This can act in
     * one of two ways: either there is an existing DSpace Object with
     * this id, in which case it behaves like the previous method, or there
     * is no DSpace Object, in which case we assume that the object has been
     * deleted. In this case, the replica store is purged of the deleted
     * manifest, or manifests, if the id is (was) a container.
     *
     * @param ctx current DSpace Context
     * @param id Identifier of the object to be removed.
     * @return integer which represents Curator return status
     * @throws IOException
     */
    @Override
    public int perform(Context ctx, String id) throws IOException 
    {
        DSpaceObject dso = dereference(ctx, id);
        if (dso != null) {
            return perform(dso);
        }
        ReplicaManager repMan = ReplicaManager.instance();
        deleteManifest(repMan, ReplicaManager.safeId(id));
        setResult("Manifest for '" + id + "' has been removed");
        return Curator.CURATE_SUCCESS;
    }
    
    /**
     * Removes a DSpace Object's Manifest from the Replica ObjectStore.
     * If object has any associated child objects, their existing manifests
     * are also removed from the Replica ObjectStore.
     * <p>
     * NOTE: this method does NOT remove the DSpace Object itself (nor any 
     * children) from the DSpace repository. It only removes the associated
     * manifests from the Replica ObjectStore.
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param id the DSpace Object's identifier
     * @throws IOException 
     */
    private void deleteManifest(ReplicaManager repMan, String id) throws IOException 
    {
        File manFile = repMan.fetchObject(manifestGroupName, id);
        if (manFile != null) {
            BufferedReader reader = new BufferedReader(new FileReader(manFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (! line.startsWith("#")) {
                    String entry = line.substring(0, line.indexOf("|"));
                    if (entry.indexOf("-") > 0) {
                        // it's another manifest - fetch & delete it
                        deleteManifest(repMan, entry);
                    }
                }
            }
            reader.close();
            report("Removing manifest for: " + id);
            repMan.removeObject(manifestGroupName, id);
        }
    }
}
