/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

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
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.handle.HandleManager;

/**
 * CompareWithManifest task compares local repository content against the
 * representation contained in the replica store manifest.
 * <p>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 *
 * @author richardrodgers
 * @see TransmitManifest
 */
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
        
        String objId = ReplicaManager.safeId(dso.getHandle());
        try
        {
            Context context = new Context();
            checkManifest(repMan, objId, context);
            context.complete();
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE);
        }
        return status;
    }
    
    // recursive manifest checking
    private void checkManifest(ReplicaManager repMan, String id, Context context) throws IOException, SQLException
    {
        File manFile = repMan.fetchObject(manifestGroupName, id);
        if (manFile != null)
        {
            Item item = null;
            Map<String, Bitstream> bsMap = new HashMap<String, Bitstream>();
            BufferedReader reader = new BufferedReader(new FileReader(manFile));
            String line = null;
            while ((line = reader.readLine()) != null)
            {
                if (! line.startsWith("#"))  // skip comments
                {
                    String entry = line.substring(0, line.indexOf("|"));
                    if (entry.indexOf("-") > 0)
                    {
                        // it's another manifest - fetch & check it
                        item = null;
                        bsMap.clear();
                        checkManifest(repMan, entry, context);
                    }
                    else
                    {
                        // it's a bitstream ref check it
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
                                report("No item found for manifest entry: " + handle);
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
                                report("Bitstream: " + seqId + " differs from manifest: " + entry);
                                status = Curator.CURATE_FAIL;
                            }
                        }
                        else
                        {
                            report("No bitstream: " + seqId + " found for manifest entry: " + entry);
                            status = Curator.CURATE_FAIL;
                        }
                    }
                }
            }
            reader.close();
            report("Checked manifest for: " + id);
        }
        else
        {
            setResult("No manifest found for: " + id);
            status = Curator.CURATE_FAIL; 
        }
    }
}
