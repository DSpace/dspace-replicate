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
import org.dspace.content.DSpaceObject;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * TransmitAIP task creates an AIP suitable for replication, and forwards it
 * to the replication system for transmission (upload).
 * 
 * @author richardrodgers
 */
public class TransmitAIP extends AbstractCurationTask
{
    private ReplicaManager repMan = ReplicaManager.instance();

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        Packer packer = PackerFactory.instance(dso);
        try
        {
            // get location for staging our replica archive file
            File archive = packer.pack(repMan.stage("aips", dso.getHandle()));
            String msg = "Created AIP: '" + archive.getName() + 
                         "' size: " + archive.length();
            repMan.transferObject("aips", archive);
            setResult(msg);
            return Curator.CURATE_SUCCESS;
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
}
