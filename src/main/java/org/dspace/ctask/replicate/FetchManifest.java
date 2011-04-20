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

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * FetchManifest task will simply retrieve a manifest representation of the object
 * into the local staging area
 * 
 * @author richardrodgers
 */

public class FetchManifest extends AbstractCurationTask
{
    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    // Group where all Manifests are stored
    private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");
    
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        String objId = ReplicaManager.safeId(dso.getHandle());
        File archive = repMan.fetchObject(manifestGroupName, objId);
        boolean found = archive != null;
        setResult("Manifest for object: " + dso.getHandle() + " found: " + found);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
