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
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.packager.PackageParameters;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
import org.dspace.pack.mets.METSPacker;

/**
 * METSRestoreFromAIP task will instate the METS AIP replica representation of the object in
 * place of the current (repository) one.
 * 
 * @author tdonohue
 * @see TransmitAIP
 */
@Distributive
@Mutative
public class METSRestoreFromAIP extends AbstractPackagerTask 
{
    private Logger log = LogManager.getLogger();
      
    private String archFmt;

    // Group where all AIPs are stored
    private String storeGroupName;

    // Group where object deletion catalog/records are stored
    private String deleteGroupName;
    
    // Name of module configuration file specific to METS based AIPs
    private final String metsModuleConfig = "replicate-mets";

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        archFmt = configurationService.getProperty("replicate.packer.archfmt");
        storeGroupName = configurationService.getProperty("replicate.group.aip.name");
        deleteGroupName = configurationService.getProperty("replicate.group.delete.name");
    }
    
    
    /**
     * Perform the Restore/Replace task.
     * <P>
     * Actually restore/replace an object in the repository with
     * whatever information is contained in the AIP.
     * @param ctx current DSpace Context
     * @param id the ID of DSpace object to restore/replace
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(Context ctx, String id) throws IOException
    {
        String result = null;
        int status = Curator.CURATE_FAIL;
        
        ReplicaManager repMan = ReplicaManager.instance();
        
        //Look for object in Replica Store
        String objId = repMan.storageId(ctx, id, archFmt);
        File archive = repMan.fetchObject(ctx, storeGroupName, objId);
          
        if (archive != null) 
        {
            //Load packaging options from replicate-mets.cfg configuration file
            PackageParameters pkgParams = this.loadPackagerParameters(metsModuleConfig);
            
            //log that this task is starting (as this may be a large task)
            log.info(getStartMsg(id, pkgParams));
            
            //restore/replace object represented by this archive file
            //(based on packaging params, this may also restore/replace all child objects too)
            restoreObject(ctx, repMan, archive, pkgParams);

            //Check if a deletion catalog exists for this object
            String catId = repMan.deletionCatalogId(id, archFmt);
            File catArchive = repMan.fetchObject(ctx, deleteGroupName, catId);
            if (catArchive != null) {
                // remove the deletion catalog (as the object is now restored)
                repMan.removeObject(deleteGroupName, catId);
                // remove from local cache as well
                catArchive.delete();
            }
            
            result = getSuccessMsg(id, pkgParams);
            status = Curator.CURATE_SUCCESS;
        }
        else
        {
            result = "Failed to update Object '" + id + "'. AIP could not be found in Replica Store.";
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
     * Restores/Replaces a DSpace Object (along with possibly its child objects),
     * based on an archive file in the Replica Filestore and the given 
     * PackageParameters.
     *
     * @param context the context to use
     * @param repMan ReplicaManager
     * @param archive File in replica archive
     * @param pkgParams PackageParameters (may specify restore/replace mode, recursion, etc.)
     * @throws IOException if I/O error
     */
    private void restoreObject(Context context, ReplicaManager repMan, File archive, PackageParameters pkgParams)
             throws IOException
    {
        //Initialize a new METS-based packer, without an associated object
        METSPacker packer = new METSPacker(context, archFmt);
       
        try
        {
            //unpack archival package & actually run the restore/replace,
            // based on the current PackageParameters
            // This only restores/replaces a single object.
            packer.unpack(archive, pkgParams);

            // Remove the locally cached archive file - it is no longer needed.
            if(archive.exists())
                archive.delete();

            //check if recursiveMode is enabled (restore/replace multiple objects)
            if(pkgParams.recursiveModeEnabled())
            {
                //See if this package refered to child packages, 
                //if so, we want to also replace those child objects
                List<String> childPkgRefs = packer.getChildPackageRefs();
                if(childPkgRefs!=null && !childPkgRefs.isEmpty())
                {
                    for(String childRef : childPkgRefs)
                    {
                        File childArchive = repMan.fetchObject(context, storeGroupName, childRef);

                        if(childArchive!=null)
                        {
                            //recurse to restore/replace this child object (and all its children)
                            restoreObject(context, repMan, childArchive, pkgParams);
                        }
                        else
                        {
                            throw new IOException("Archive " + childRef + " was not found in Replica Store");
                        }
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
    
    
    /**
     * Return a human-friendly 'start processing' message based on the 
     * actions performed (determined via PackageParameters).
     * 
     * @param objId Object ID
     * @param pkgParams PackageParameters (used to determine actions)
     * @return human-friendly start message
     */
    private String getStartMsg(String objId, PackageParameters pkgParams)
    {
        String resultMsg = "Beginning ";
        
        //add action
        if(pkgParams.replaceModeEnabled())
            resultMsg += "replacement of ";
        else if (pkgParams.keepExistingModeEnabled())
            resultMsg += "restoration (keep-existing mode) of ";
        else
            resultMsg += "restoration of ";
        
        //add object info
        resultMsg += "Object '" + objId +"' ";
        
        //is it recursive?
        if(pkgParams.recursiveModeEnabled())
            resultMsg += "(and all child objects) ";
        
        //complete message;
        resultMsg += "from AIP.";
        return resultMsg;
        
    }
    
    /**
     * Return a human-friendly success message based on the 
     * actions performed (determined via PackageParameters).
     * 
     * @param objId Object ID
     * @param pkgParams PackageParameters (used to determine actions)
     * @return human-friendly result message
     */
    private String getSuccessMsg(String objId, PackageParameters pkgParams)
    {
        String resultMsg = "Successfully ";
        
        //add action
        if(pkgParams.replaceModeEnabled())
            resultMsg += "replaced ";
        else if (pkgParams.keepExistingModeEnabled())
            resultMsg += "restored (keep-existing mode) ";
        else
            resultMsg += "restored ";
        
        //add object info
        resultMsg += "Object '" + objId +"' ";
        
        //is it recursive?
        if(pkgParams.recursiveModeEnabled())
            resultMsg += "(and all child objects) ";
        
        //complete message;
        resultMsg += "from AIP.";
        return resultMsg;
    }
    
}
