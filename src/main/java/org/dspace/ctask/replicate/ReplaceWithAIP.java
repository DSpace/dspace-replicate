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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * ReplaceWithAIP task will instate the replica representation of the object in
 * place of the current (repository) one.
 * 
 * @author richardrodgers
 */
@Mutative
public class ReplaceWithAIP extends AbstractCurationTask {

    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    @Override
    public int perform(DSpaceObject dso) throws IOException {
        // overwrite with AIP data
        Packer packer = PackerFactory.instance(dso);
        try {
            int status = Curator.CURATE_FAIL;
            String objId = ReplicaManager.safeId(dso.getHandle()) + "." + archFmt;
            File archive = repMan.fetchObject("aips", objId);
            if (archive != null) {
                // clear object where necessary
                if (dso.getType() == Constants.ITEM) {
                    Item item = (Item)dso;
                    item.clearMetadata(Item.ANY, Item.ANY, Item.ANY, Item.ANY);
                    for (Bundle bundle : item.getBundles()) {
                        item.removeBundle(bundle);
                    }   
                }
                packer.unpack(archive);
                // now update the dso
                int type = dso.getType();
                if (type == Constants.ITEM) {
                    ((Item)dso).update();
                } else if (type == Constants.COLLECTION) {
                    ((Collection)dso).update();
                } else if (type == Constants.COMMUNITY) {
                    ((Community)dso).update();
                }
                status = Curator.CURATE_SUCCESS;
                report("Object: " + dso.getHandle() + "replaced from AIP");
            }
            return status;
        } catch (AuthorizeException authE) {
            throw new IOException(authE.getMessage());
        } catch (SQLException sqlE) {
            throw new IOException(sqlE.getMessage());
        }
    }
}
