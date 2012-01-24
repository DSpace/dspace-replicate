/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Item;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;
import org.dspace.handle.HandleManager;

/**
 * CompareWithManifest task compares local repository content against the
 * representation contained in the replica store manifest.
 * <P>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * <P>
 * This task is "suspendable" when invoked from the UI.  This means that if
 * you run an Audit from the UI, this task will return an immediate failure
 * once a single object fails the audit. However, when run from the Command-Line
 * this task will run to completion (i.e. even if an object fails it will continue
 * processing to completion).
 *
 * @author richardrodgers
 * @see TransmitManifest
 */
@Suspendable(invoked=Curator.Invoked.INTERACTIVE)
public class CompareWithManifest extends AbstractCurationTask
{
    private int status = Curator.CURATE_SUCCESS;
    
    // Group where all Manifests will be stored
    private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");

    /**
     * Perform 'Compare with Manifest' task
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        ReplicaManager repMan = ReplicaManager.instance();
        
        try
        {
            Context context = new Context();
            checkManifest(repMan, dso.getHandle(), context);
            context.complete();
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE);
        }
        return status;
    }
    
    /**
     * This method recursively checks Manifests.
     * <P>
     * In a sense, all this is checking is that Bitstreams (files) have not changed within 
     * the current DSpace object.  So, if the current object is a Site, Community or Collection,
     * its manifest is not validated (as those manifests just point at other sub-manifests). Rather,
     * this method recursively loads manifests until it locates all Item-level manifests. Then it
     * validates that all bitstream information (and checksums) are unchanged.
     * 
     * @param repMan Replication Manager
     * @param id Identifier of current DSpace object
     * @param context current DSpace context
     * @throws IOException
     * @throws SQLException 
     */
    private void checkManifest(ReplicaManager repMan, String id, Context context) throws IOException, SQLException
    {
        String filename = repMan.storageId(id, TransmitManifest.MANIFEST_EXTENSION);
        
        File manFile = repMan.fetchObject(manifestGroupName, filename);
        if (manFile != null)
        {
            String result = "Manifest and repository content agree";
            Item item = null;
            Map<String, Bitstream> bsMap = new HashMap<String, Bitstream>();
            BufferedReader reader = new BufferedReader(new FileReader(manFile));
            String line = null;
            while ((line = reader.readLine()) != null)
            {
                if (! line.startsWith("#"))  // skip comments
                {
                    String entry = line.substring(0, line.indexOf("|"));
                    // if there's a dash in the first entry, then it just
                    // refers to a sub manifest
                    if (entry.indexOf("-") > 0)
                    {
                        // it's another manifest - fetch & check it
                        item = null;
                        bsMap.clear();
                        checkManifest(repMan, entry, context);
                    }
                    else
                    {
                        // first entry is a bitstream reference. So, check it
                        int cut = entry.lastIndexOf("/");
                        if (item == null)
                        {
                            // look up object first & map bitstreams by seqID
                            String handle = entry.substring(0, cut);
                            DSpaceObject dso = HandleManager.resolveToObject(context, handle);
                            if (dso != null && dso instanceof Item)
                            {
                                item = (Item)dso;
                                for (Bundle bundle : item.getBundles())
                                {
                                    for (Bitstream bs : bundle.getBitstreams())
                                    {
                                        bsMap.put(Integer.toString(bs.getSequenceID()), bs);
                                    }
                                }
                            }
                            else
                            {
                                result = "No item found for manifest entry: " + handle;
                                status = Curator.CURATE_FAIL;
                            }
                        }
                        String seqId = entry.substring(cut + 1);
                        Bitstream bs = bsMap.get(seqId);
                        if (bs != null)
                        {
                            String[] parts = line.split("\\|");
                            // compare checksums
                            if (! bs.getChecksum().equals(parts[2]))
                            {
                                result = "Bitstream: " + seqId + " differs from manifest: " + entry;
                                status = Curator.CURATE_FAIL;
                            }
                        }
                        else
                        {
                            result = "No bitstream: " + seqId + " found for manifest entry: " + entry;
                            status = Curator.CURATE_FAIL;
                        }
                    }
                }
            }
            reader.close();
            report(result);
            setResult(result);
        }
        else
        {
            report("No manifest found for: " + id);
            setResult("No manifest found for: " + id);
            status = Curator.CURATE_FAIL; 
        }
    }
}
