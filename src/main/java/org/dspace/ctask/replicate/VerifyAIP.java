/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * VerifyAIP task will simply test for the presence of a replica representation
 * of the object in the remote store. It succeeds if found, otherwise fails.
 * 
 * @author richardrodgers
 */

public class VerifyAIP extends AbstractCurationTask
{
    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        String objId = ReplicaManager.safeId(dso.getHandle()) + "." + archFmt;
        boolean found = repMan.objectExists("aips", objId);
        String result = "AIP for object: " + dso.getHandle() + " found: " + found;
        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
