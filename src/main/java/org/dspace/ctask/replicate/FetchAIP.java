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

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * FetchAIP task will simply retrieve replica representations of the object
 * into the local staging area.
 *
 * @author richardrodgers
 * @see TransmitAIP
 */

public class FetchAIP extends AbstractCurationTask {
    private String archFmt;

    private String baseFolder;

    // Group where all AIPs are stored
    private String storeGroupName;

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        baseFolder = configurationService.getProperty("replicate.base.dir");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
    }

    /**
     * Perform the 'Fetch AIP' task
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        if (dso != null) {
            try {
                return perform(Curator.curationContext(), dso.getHandle());
            } catch (SQLException e) {
                throw new IOException(e);
            }
        } else {
            String result = "DSpace Object not found!";
            report(result);
            setResult(result);
            return Curator.CURATE_FAIL;
        }
    }


    /**
     * Perform the 'Fetch AIP' task
     * @param ctx DSpace Context
     * @param id ID of object whose AIP should be fetched
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(ctx, id, archFmt);
        File archive = repMan.fetchObject(ctx, storeGroupName, objId);

        boolean found = archive != null;
        String result = "AIP for object: " + id + " located : " + found + ".";

        if (found) {
            result += " AIP file downloaded to '"
                + baseFolder + "/" + storeGroupName + "/" + objId + "'";
        }

        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
