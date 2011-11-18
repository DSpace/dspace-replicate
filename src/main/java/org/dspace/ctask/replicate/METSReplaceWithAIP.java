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
import java.sql.SQLException;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
import org.dspace.pack.mets.METSPacker;

import org.apache.log4j.Logger;
import org.dspace.ctask.replicate.ReplicaManager;

/**
 * METSReplaceWithAIP task will instate the METS AIP replica representation of the object in
 * place of the current (repository) one.
 * 
 * @author tdonohue
 * @see TransmitAIP
 */
@Distributive
@Mutative
public class METSReplaceWithAIP extends AbstractCurationTask 
{
    private Logger log = Logger.getLogger(METSReplaceWithAIP.class);
      
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    /**
     * Perform the 'Replace with AIP' task.
     * <P>
     * Actually overwrite any existing object data in the repository with
     * whatever information is contained in the AIP.
     * @param dso the DSpace object to replace
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(Context ctx, String id) throws IOException
    {
        String result = null;
        int status = Curator.CURATE_FAIL;
        
        ReplicaManager repMan = ReplicaManager.instance();
        
        //Look for object in Replica Store
        String objId = ReplicaManager.safeId(id) + "." + archFmt;
        File archive = repMan.fetchObject(storeGroupName, objId);
          
        if (archive != null) 
        {
            //replace object represented by this archive file (and all reference child objects)
            replaceObject(repMan, archive);
            result = "Object: " + objId + " (and all child objects) replaced from AIP";
            status = Curator.CURATE_SUCCESS;
        }
        else
        {
            result = "Failed to replace Object ID " + id + ". AIP could not be found in Replica Store.";
        }
             
        report(result);
        setResult(result);
        return status;
    }
    
    
    
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        int status = Curator.CURATE_FAIL;
        try
        {
            //Get Context from current curation thread
            Context ctx = Curator.curationContext();
            status = perform(ctx, dso.getHandle());
            //Note: context will be committed/closed by Curator
        }
        catch(SQLException sqle)
        {
            throw new IOException(sqle);
        }
        return status;
    }
    
    
    /**
     * Replaces a single DSpace Object along with all its child objects,
     * based on an archive file in the Replica Filestore
     * @param repMan ReplicaManager
     * @param archive File in replica archive
     * @throws IOException 
     */
    private void replaceObject(ReplicaManager repMan, File archive)
             throws IOException
    {
        //Initialize a new METS-based packer, without an associated object
        METSPacker packer = new METSPacker(archFmt);
       
        try
        {
            //unpack archival package & actually replace this single object
            packer.unpack(archive);

            //See if this package refered to child packages, 
            //if so, we want to also replace those child objects
            List<String> childPkgRefs = packer.getChildPackageRefs();
            if(childPkgRefs!=null && !childPkgRefs.isEmpty())
            {
                for(String childRef : childPkgRefs)
                {
                    File childArchive = repMan.fetchObject(storeGroupName, childRef);
                    
                    if(childArchive!=null)
                    {
                        //recurse to replace this child object (and all its children)
                        replaceObject(repMan, childArchive);
                    }
                    else
                    {
                        throw new IOException("Archive " + childRef + " was not found in Replica Store");
                    }
                }    
            }
        }
        catch(AuthorizeException authe)
        {
            throw new IOException(authe);
        }
        catch(SQLException sqle)
        {
            throw new IOException(sqle);
        }
    }        
    
    
}
