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
 * FetchAIP task will simply retrieve replica representations of the object
 * into the local staging area
 * 
 * @author richardrodgers
 */

public class FetchAIP extends AbstractCurationTask
{
    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        String objId = ReplicaManager.safeId(dso.getHandle()) + "." + archFmt;
        File archive = repMan.fetchObject(storeGroupName, objId);
        boolean found = archive != null;
        setResult("AIP for object: " + dso.getHandle() + " found: " + found);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
