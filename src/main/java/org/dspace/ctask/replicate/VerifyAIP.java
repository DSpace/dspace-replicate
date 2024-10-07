/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;

/**
 * VerifyAIP task will simply test for the presence of a replica representation
 * of the object in the remote store. It succeeds if found, otherwise fails.
 * <P>
 * This task is "suspendable" when invoked from the UI.  This means that if
 * you run a verification from the UI, this task will return an immediate failure
 * once a single object fails the verification. However, when run from the Command-Line
 * this task will run to completion (i.e. even if an object fails it will continue
 * processing to completion).
 *
 * @author richardrodgers
 * @see TransmitAIP
 */
@Suspendable(invoked = Curator.Invoked.INTERACTIVE)
public class VerifyAIP extends AbstractCurationTask {
    private String archFmt;

    // Group where all AIPs are stored
    private String storeGroupName;

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
    }

    /**
     * Performs the "Verify AIP" task.
     * <p>
     * Simply tests for presence of AIP in replica ObjectStore.
     * @param dso the DSpace Object to verify
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
     * Performs the "Verify AIP" task.
     * <p>
     * Simply tests for presence of AIP in replica ObjectStore.
     * @param ctx DSpace Context
     * @param id ID of object to verify
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();

        String objId = repMan.storageId(ctx, id, archFmt);
        boolean found = repMan.objectExists(storeGroupName, objId);

        String result = "AIP for object: " + id + " found: " + found;
        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
