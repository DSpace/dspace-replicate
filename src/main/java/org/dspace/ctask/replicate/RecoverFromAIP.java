/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.log4j.Logger;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.InstallItem;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
import org.dspace.embargo.EmbargoManager;
import org.dspace.handle.HandleManager;
import org.dspace.pack.Bag;
import org.dspace.pack.CatalogPacker;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

import static org.dspace.pack.PackerFactory.*;

/**
 * RecoverFromAIP task performs essentially an 'undelete' on an object that
 * has been deleted from the repository, using the replica copy.
 * If the object is a container, it recovers all its children/members.
 *
 * @author richardrodgers
 */
@Distributive
@Mutative
public class RecoverFromAIP extends AbstractCurationTask {

    private static Logger log = Logger.getLogger(RecoverFromAIP.class);
    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    @Override
    public int perform(DSpaceObject dso) throws IOException {
        throw new IllegalStateException("Cannot recover if object exists");
    }

    @Override
    public int perform(Context ctx, String id) throws IOException {
        // first we locate the deletion catalog for this object
        String objId = ReplicaManager.safeId(id) + "." + archFmt;
        File catArchive = repMan.fetchObject("deletes", objId);
        int status = Curator.CURATE_FAIL;
        if (catArchive != null) {
            CatalogPacker cpack = new CatalogPacker(id);
            cpack.unpack(catArchive);
            // RLR TODO - remove filename collision next delete requires
            catArchive.delete();
            // recover root object itself, then any members
            recover(ctx, id);
            for (String mem : cpack.getMembers()) {
                recover(ctx, mem);
            }
            status = Curator.CURATE_SUCCESS;
        }
        return status;
    }

    private void recover(Context ctx, String id) throws IOException {
        String objId = ReplicaManager.safeId(id) + "." + archFmt;
        File archive = repMan.fetchObject("aips", objId);
        if (archive != null) {
            Bag bag = new Bag(archive);
            Properties props = new Properties();
            props.load(bag.dataStream(OBJFILE));
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

    private void recoverItem(Context ctx, File archive, String objId, Properties props) throws IOException {
        try {
            String collId = props.getProperty(OWNER_ID);
            Collection coll = (Collection)HandleManager.resolveToObject(ctx, collId);
            WorkspaceItem wi = WorkspaceItem.create(ctx, coll, false);
            Packer packer = PackerFactory.instance(wi.getItem());
            // stuff bag contents into item
            packer.unpack(archive);
            // Install item
            Item item = InstallItem.restoreItem(ctx, wi, objId);
            String colls = props.getProperty(OTHER_IDS);
            if (colls != null) {
                // reset linked collections
                for (String link : colls.split(",")) {
                    Collection linkC = (Collection)HandleManager.resolveToObject(ctx, link);
                    linkC.addItem(item);
                }
            }
            // now post-process: withdrawals, embargoes, etc
            if (props.getProperty(WITHDRAWN) != null) {
                item.withdraw();
            }
            EmbargoManager.setEmbargo(ctx, item, null);
        } catch (AuthorizeException authE) {
            throw new IOException(authE.getMessage());
        } catch (SQLException sqlE) {
            throw new IOException(sqlE.getMessage());
        }
    }

    private void recoverCollection(Context ctx, File archive, String collId, String commId) throws IOException {
        Collection coll = null;
        try {
            if (commId != null) {
                Community pcomm = (Community)HandleManager.resolveToObject(ctx, commId);
                coll = pcomm.createCollection(collId);
            } else {
                log.error("Collection '" + collId + "' lacks parent community");
            }
            // update with AIP data
            Packer packer = PackerFactory.instance(coll);
            packer.unpack(archive);
        } catch (AuthorizeException authE) {
            throw new IOException(authE.getMessage());
        } catch (SQLException sqlE) {
            throw new IOException(sqlE.getMessage());
        }
    }

    private void recoverCommunity(Context ctx, File archive, String commId, String parentId) throws IOException {
        // if not top-level, have parent create it
        Community comm = null;
        try {
            if (parentId != null) {
                Community pcomm = (Community)HandleManager.resolveToObject(ctx, parentId);
                comm = pcomm.createSubcommunity(commId);
            } else {
                comm = Community.create(null, ctx, commId);
            }
            // update with AIP data
            Packer packer = PackerFactory.instance(comm);
            packer.unpack(archive);
        } catch (AuthorizeException authE) {
            throw new IOException(authE.getMessage());
        } catch (SQLException sqlE) {
            throw new IOException(sqlE.getMessage());
        }
    }

}
