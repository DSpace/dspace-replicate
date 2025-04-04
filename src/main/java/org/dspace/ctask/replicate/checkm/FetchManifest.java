/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * FetchManifest task will simply retrieve a manifest representation of the object
 * into the local staging area
 * <p>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 * @see TransmitManifest
 */

public class FetchManifest extends AbstractCurationTask {
    private String archFmt;

    // Group where all Manifests are stored
    private String manifestGroupName;

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        manifestGroupName = configurationService.getProperty("replicate.group.manifest.name");
    }

    /**
     * Perform 'Fetch Manifest' task
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        try {
            Context context = Curator.curationContext();
            ReplicaManager repMan = ReplicaManager.instance();
            String objId = repMan.storageId(context, dso.getHandle(), TransmitManifest.MANIFEST_EXTENSION);
            File archive = repMan.fetchObject(context, manifestGroupName, objId);
            boolean found = archive != null;
            setResult("Manifest for object: " + dso.getHandle() + " found: " + found);
            return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
