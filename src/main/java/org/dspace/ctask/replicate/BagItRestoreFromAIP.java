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
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.log4j.Logger;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
import org.dspace.embargo.factory.EmbargoServiceFactory;
import org.dspace.embargo.service.EmbargoService;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.Bag;
import org.dspace.pack.bagit.CatalogPacker;

import static org.dspace.pack.PackerFactory.*;

/**
 * BagItRestoreFromAIP task performs essentially an 'undelete' on an object that
 * has been deleted from the repository, using the replica copy.
 * If the object is a container, it recovers all its children/members.
 *
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
@Mutative
public class BagItRestoreFromAIP extends AbstractCurationTask {

    private static Logger log = Logger.getLogger(BagItRestoreFromAIP.class);
    private String archFmt;

    // Group where all AIPs are stored
    private String storeGroupName;
    
    // Group where object deletion catalog/records are stored
    private String deleteGroupName;

    private EmbargoService embargoService = EmbargoServiceFactory.getInstance().getEmbargoService();
    private WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    private InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        deleteGroupName = configurationService.getProperty("replicate.group.delete.name");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
    }
    
    /**
     * Perform 'Recover From AIP' task on a particular object.
     * As you cannot recover an object that already exists, this method
     * always returns an exception. 
     * @param dso DSpace Object to recover
     * @return integer which represents Curator return status
     * @throws IOException if IO error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        throw new IllegalStateException("Cannot recover if object exists");
    }

    /**
     * Perform 'Recover From AIP' task by retrieving an object package
     * of a particular ID (name) and restoring it to the current DSpace
     * Context.
     * @param ctx current DSpace context
     * @param id identifier of object to restore
     * @return integer which represents Curator return status
     * @throws IOException if IO error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException 
    {
        ReplicaManager repMan = ReplicaManager.instance();
        // first we locate the deletion catalog for this object
        String catId = repMan.deletionCatalogId(id, archFmt);
        File catArchive = repMan.fetchObject(deleteGroupName, catId);
        int status = Curator.CURATE_FAIL;
        String result;
        // CANNOT continue if the deletion catalog cannot be located
        if (catArchive != null) {
            CatalogPacker cpack = new CatalogPacker(id);
            cpack.unpack(catArchive);
            // RLR TODO - remove filename collision next delete requires
            catArchive.delete();
            // recover root object itself, then any members
            recover(ctx, repMan, id);
            for (String mem : cpack.getMembers()) {
                recover(ctx, repMan, mem);
            }
            // remove the deletion catalog (as the object is now restored)
            repMan.removeObject(deleteGroupName, catId);
            result = "Successfully restored Object '" + id + "' (and any child objects) from AIP.";
            status = Curator.CURATE_SUCCESS;
        }
        else
        {
            result = "Failed to restore Object '" + id + "'. Deletion record could not be found in Replica Store. Are you sure this object was previously deleted?";
        }

        report(result);
        setResult(result);
        return status;
    }

    /**
     * Recover an object from an ObjectStore based on its identifier
     * @param ctx current DSpace Context
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param id Identifier of object in ObjectStore
     * @throws IOException if IO error
     */
    private void recover(Context ctx, ReplicaManager repMan, String id) throws IOException 
    {
        String objId = repMan.storageId(id, archFmt);
        File archive = repMan.fetchObject(storeGroupName, objId);
        if (archive != null) {
            Bag bag = new Bag(archive);
            InputStream bagIn = bag.dataStream(OBJFILE);
            Properties props = new Properties();
            props.load(bagIn);
            bagIn.close();
            String type = props.getProperty(OBJECT_TYPE);
            String ownerId = props.getProperty(OWNER_ID);
            if ("item".equals(type)) {
                recoverItem(ctx, archive, id, props);
            } else if ("collection".equals(type)) {
                recoverCollection(ctx, archive, id, ownerId);
            } else if ("community".equals(type)) {
                recoverCommunity(ctx, archive, id, ownerId);
            }
            // discard bag when done
            bag.empty();
        }
    }

    /**
     * Recover a DSpace Item from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param objId identifier of object we are restoring
     * @param props properties which control how item is restored
     * @throws IOException if IO error
     */
    private void recoverItem(Context ctx, File archive, String objId, Properties props) throws IOException 
    {
        try {
            String collId = props.getProperty(OWNER_ID);
            Collection coll = (Collection) handleService.resolveToObject(ctx, collId);
            WorkspaceItem wi = workspaceItemService.create(ctx, coll, false);
            Packer packer = PackerFactory.instance(wi.getItem());
            // stuff bag contents into item
            packer.unpack(archive);
            // Install item
            Item item = installItemService.restoreItem(ctx, wi, objId);
            String colls = props.getProperty(OTHER_IDS);
            if (colls != null) {
                // reset linked collections
                for (String link : colls.split(",")) {
                    Collection linkC = (Collection) handleService.resolveToObject(ctx, link);
                    collectionService.addItem(ctx, linkC, item);
                }
            }
            // now post-process: withdrawals, embargoes, etc
            if (props.getProperty(WITHDRAWN) != null) {
                itemService.withdraw(ctx, item);
            }
            embargoService.setEmbargo(ctx, item);
        } catch (AuthorizeException authE) {
            throw new IOException(authE);
        } catch (SQLException sqlE) {
            throw new IOException(sqlE);
        }
    }

    /**
     * Recover a DSpace Collection from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param collId identifier of collection we are restoring
     * @param commId identifier of parent community for this collection
     * @throws IOException if IO error
     */
    private void recoverCollection(Context ctx, File archive, String collId, String commId) throws IOException 
    {
        Collection coll = null;
        try {
            if (commId != null) {
                Community pcomm = (Community) handleService.resolveToObject(ctx, commId);
                coll = collectionService.create(ctx, pcomm, collId);
            } else {
                log.error("Collection '" + collId + "' lacks parent community");
            }
            // update with AIP data
            Packer packer = PackerFactory.instance(coll);
            packer.unpack(archive);
        } catch (AuthorizeException authE) {
            throw new IOException(authE);
        } catch (SQLException sqlE) {
            throw new IOException(sqlE);
        }
    }

    /**
     * Recover a DSpace Community from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param commId identifier of community we are restoring
     * @param parentId identifier of parent community (if any) for community
     * @throws IOException if IO error
     */
    private void recoverCommunity(Context ctx, File archive, String commId, String parentId) throws IOException 
    {
        // if not top-level, have parent create it
        Community comm = null;
        try {
            if (parentId != null) {
                Community pcomm = (Community) handleService.resolveToObject(ctx, parentId);
                comm = communityService.createSubcommunity(ctx, pcomm, commId);
            } else {
                comm = communityService.create(null, ctx, commId);
            }
            // update with AIP data
            Packer packer = PackerFactory.instance(comm);
            packer.unpack(archive);
        } catch (AuthorizeException authE) {
            throw new IOException(authE);
        } catch (SQLException sqlE) {
            throw new IOException(sqlE);
        }
    }

}
