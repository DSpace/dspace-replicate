/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * VerifyManifest task will simply test for the presence of a manifest
 * of the object in the remote store. It succeeds if found, otherwise fails.
 * <p>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 * @see TransmitManifest
 */

public class VerifyManifest extends AbstractCurationTask {

    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
    
    // Group where all Manifests are stored
    private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");

    /**
     * Perform the 'Verify Manifest' task
     * @param dso the DSpace Object to be verified
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = ReplicaManager.safeId(dso.getHandle());
        boolean found = repMan.objectExists(manifestGroupName, objId);
        String result = "Manifest for object: " + dso.getHandle() + " found: " + found;
        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
