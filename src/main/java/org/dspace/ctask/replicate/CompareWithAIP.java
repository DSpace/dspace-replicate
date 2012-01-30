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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;
import org.dspace.curate.Utils;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * CompareWithAIP task compares local repository objects with the replica store
 * objects. This task just performs an 'integrity' audit which compares the 
 * checksums of the local and remote zipped AIPs.
 * <P>
 * This task is "suspendable" when invoked from the UI.  This means that if
 * you run an Audit from the UI, this task will return an immediate failure
 * once a single object fails the audit. However, when run from the Command-Line
 * this task will run to completion (i.e. even if an object fails it will continue
 * processing to completion).
 * 
 * @author richardrodgers
 */
@Suspendable(invoked=Curator.Invoked.INTERACTIVE)
public class CompareWithAIP extends AbstractCurationTask
{
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
   
    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");

    /**
     * Perform 'Compare with AIP' task for a *single* object.
     * <P>
     * The Curator itself will take care of running this in a distributive (recursive)
     * manner, as appropriate.
     * 
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        ReplicaManager repMan = ReplicaManager.instance();
        Packer packer = PackerFactory.instance(dso);
        String id = dso.getHandle();
        int status = Curator.CURATE_UNSET;
        String result = null;
        String storeId = repMan.storageId(id, archFmt);
        try
        {
            // generate an archive and calculate its checksum
            File packDir = repMan.stage(storeGroupName, id);
            File archive = packer.pack(packDir);
            String chkSum = Utils.checksum(archive, "MD5");
            
            // verify replica exists
            if (! repMan.objectExists(storeGroupName, storeId))
            {
                String msg = "Missing remote replica for: " + id;
                report(msg);
                result = msg;
                status = Curator.CURATE_FAIL;
            }
            else // replica exists, now we can do a comparison
            {    
                // compare local checksum with replica checksum
                String repChkSum = repMan.objectAttribute(storeGroupName, storeId, "checksum");
                if (! chkSum.equals(repChkSum))
                {
                    report("Local and remote checksums differ for: " + id);
                    report("Local: " + chkSum + " replica: " + repChkSum);
                    result = "Checksums of local and remote differ for: " + id;
                    status = Curator.CURATE_FAIL;
                }
                else
                {
                    report("Local and remote checksums agree for: " + id);
                    result = "Checksums of local and remote agree";
                    status = Curator.CURATE_SUCCESS;
                }
            }
            setResult(result);
            return status;
        }
        catch (AuthorizeException authE)
        {
            throw new IOException(authE);
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE);
        }
    }

}
