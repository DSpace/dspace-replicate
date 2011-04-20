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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.ItemIterator;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Utils;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * CompareWithAIP task compares local repository values with the replica store
 * values. It can perform 2 types of comparison: first, an 'integrity' audit
 * which compares the checksums of the local and remote zipped AIPs; second, a 
 * 'count' or enumerative audit that verifies that all objects in a local 
 * container have corresponding replicas in the remote store.
 *
 * @author richardrodgers
 */
public class CompareWithAIP extends AbstractCurationTask
{
    private ReplicaManager repMan = ReplicaManager.instance();
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
    private int status = Curator.CURATE_UNSET;
    private String result = null;
    
    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");

    public int perform(DSpaceObject dso) throws IOException
    {
        Packer packer = PackerFactory.instance(dso);
        String id = dso.getHandle();
        status = Curator.CURATE_SUCCESS;
        result = "Checksums of local and remote agree";
        String objId = ReplicaManager.safeId(id) + "." + archFmt;
        try
        {
            // generate an archive and calculate it's checksum
            File packDir = repMan.stage(storeGroupName, id);
            File archive = packer.pack(packDir);
            String chkSum = Utils.checksum(archive, "MD5");
            // compare with replica
            String repChkSum = repMan.objectAttribute(storeGroupName, objId, "checksum");
            if (! chkSum.equals(repChkSum))
            {
                report("Local and remote checksums differ for: " + id);
                report("Local: " + chkSum + " replica: " + repChkSum);
                result = "Checksums of local and remote differ";
                status = Curator.CURATE_FAIL;
            }
            else
            {
                report("Local and remote checksums agree for: " + id);
            }
            // if a container, also perform an extent (count) audit - i.e.
            // does replica store have replicas for each object in container?
            if (Curator.isContainer(dso))
            {
                auditExtent(dso);
            }
            setResult(result);
            return status;
        }
        catch (AuthorizeException authE)
        {
            throw new IOException(authE.getMessage(), authE);
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }
    }

    private void auditExtent(DSpaceObject dso) throws IOException
    {
        int type = dso.getType();
        if (Constants.COLLECTION == type)
        {
            Collection coll = (Collection)dso;
            try
            {
                ItemIterator iter = coll.getItems();
                while (iter.hasNext())
                {
                    checkReplica(iter.next());
                }
            }
            catch (SQLException sqlE)
            {
                throw new IOException(sqlE.getMessage(), sqlE);
            }
        }
        else if (Constants.COMMUNITY == type)
        {
            Community comm = (Community)dso;
            try
            {
                for (Community subcomm : comm.getSubcommunities())
                {
                    checkReplica(subcomm);
                    auditExtent(subcomm);
                }
                for (Collection coll : comm.getCollections())
                {
                    checkReplica(coll);
                    auditExtent(coll);
                }
            }
            catch (SQLException sqlE)
            {
                throw new IOException(sqlE.getMessage(), sqlE);
            }
        }
    }

    private void checkReplica(DSpaceObject dso) throws IOException
    {
       if (! repMan.objectExists(storeGroupName, dso.getHandle()))
       {
           String msg = "Missing replica for: " + dso.getHandle();
           report(msg);
           result = msg;
           status = Curator.CURATE_FAIL;
       }
    }
}
