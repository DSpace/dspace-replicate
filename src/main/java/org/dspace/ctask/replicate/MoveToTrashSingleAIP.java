/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;
import org.apache.log4j.Logger;

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * MoveToTrashSingleAIP task moves a single AIP from one group (folder) in external
 * storage to another group (folder). Currently it always moves content
 * from the 'group.aip.name' store to the 'group.delete.name' store, essentially
 * moving the content into a "trash" folder.
 * <P>
 * This task is primarily used by the ReplicateConsumer to move the AIP for a 
 * deleted DSpace Object off to a "trash" folder / temporary location. This 
 * allows the AIP to remain in external storage for a period, just in case the
 * deleted object needs to be restored to DSpace.
 * <P>
 * This task only moves a single AIP at at time (it inhibits iteration when 
 * invoked on a container object).
 * 
 * @author tdonohue
 */
@Distributive
public class MoveToTrashSingleAIP extends AbstractCurationTask
{
    // Source and destination group where AIP will be moved to
    private final String srcGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    private final String destGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");
    
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
    
    private static Logger log = Logger.getLogger(MoveToTrashSingleAIP.class);
    
    /**
     * Perform 'Move To Trash Single AIP' task
     * <p>
     * Actually generates the AIP and transmits it to the replica ObjectStore
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        if(dso!=null)
        {
            //NOTE: we can get away with passing in a 'null' Context because
            // the context isn't actually used to fetch the AIP
            // (see below 'perform(ctx,id)' method)
            return perform(null, dso.getHandle());
        }
        else
        {
            String result = "DSpace Object not specified!";
            report(result);
            setResult(result);
            return Curator.CURATE_FAIL;
        }
    }
    
    /**
     * Perform 'Move AIP' task 
     * <p>
     * Moves an existing AIP from the 'group.aip.name' store to the 'group.delete.name' store
     * @param ctx DSpace Context (this param is ignored for this task)
     * @param id ID of object whose AIP should be moved
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(Context ctx, String id) throws IOException
    {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(id, archFmt);
        
        boolean success = repMan.moveObject(srcGroupName, destGroupName, objId);
        
        String result = "AIP for object: " + id + " could NOT be moved from: " + srcGroupName + " to : " + destGroupName + ".";
        if(success)
            result = "AIP for object: " + id + " moved from: " + srcGroupName + " to : " + destGroupName + ".";
        report(result);
        setResult(result);
        
        return success ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
