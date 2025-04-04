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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * MoveToTrashSingleAIP task moves a single AIP from one group (folder) in external
 * storage to another group (folder). Currently it always moves content
 * from the 'group.aip.name' store to the 'group.delete.name' store, essentially
 * moving the content into a "trash" folder.
 * <P>
 * This task is primarily used by the ReplicateConsumer to move the AIP for a
 * deleted DSpace Object off to a "trash" folder / temporary location. This
 * allows the AIP to remain in external storage for a period, just in case the
 * deleted object needs to be restored to DSpace.
 * <P>
 * This task only moves a single AIP at at time (it inhibits iteration when
 * invoked on a container object).
 *
 * @author tdonohue
 */
@Distributive
public class MoveToTrashSingleAIP extends AbstractCurationTask {
    // Source and destination group where AIP will be moved to
    private String srcGroupName;
    private String destGroupName;

    private String archFmt;

    private static Logger log = LogManager.getLogger();

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        srcGroupName = configurationService.getProperty("replicate.group.aip.name");
        destGroupName = configurationService.getProperty("replicate.group.delete.name");
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
    }

    /**
     * Perform 'Move To Trash Single AIP' task
     * <p>
     * Actually generates the AIP and transmits it to the replica ObjectStore
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
            String result = "DSpace Object not specified!";
            report(result);
            setResult(result);
            return Curator.CURATE_FAIL;
        }
    }

    /**
     * Perform 'Move AIP' task
     * <p>
     * Moves an existing AIP from the 'group.aip.name' store to the 'group.delete.name' store
     * @param ctx DSpace Context
     * @param id ID of object whose AIP should be moved
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(ctx, id, archFmt);

        boolean success = repMan.moveObject(srcGroupName, destGroupName, objId);

        String result = "AIP for object: " + id + " could NOT be moved from: " + srcGroupName + " to : "
            + destGroupName + ".";

        if (success) {
            result = "AIP for object: " + id + " moved from: " + srcGroupName + " to : " + destGroupName + ".";
        }

        report(result);
        setResult(result);

        return success ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
