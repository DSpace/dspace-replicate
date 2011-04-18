/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate.impl;

import java.io.File;
import java.io.IOException;

import org.dspace.curate.Utils;

/**
 * MountableObjectStore uses a mountable file system to manage replicas or other
 * content. As such, it is not intended to provide the level of assurance that
 * a remote, externally managed service can provide. It's primary use is in
 * testing and validating the replication service, not as a production replica
 * store. Note in particular that certain filesystem limits on number of files
 * in a directory may limit its use. It stores replicas as archive files.
 * Also note that MountableObjectStore differs only from LocalObjectStore in
 * that all objects are copied, rather than moved (renamed). This will result
 * in slower performance, but may be required when more complex storage
 * architectures (e.g. an NFS-mounted store) are used.
 * 
 * @author richardrodgers
 */
public class MountableObjectStore extends LocalObjectStore
{
    // need a no-arg constructor for PluginManager
    public MountableObjectStore()
    {
    }

    @Override
    public long transferObject(String group, File file) throws IOException
    {
        // local transfer is a simple matter of copying the file,
        // we don't bother checking if replica is really new, since
        // local deletes/copies are cheap
        File archFile = new File(storeDir + File.separator + group, file.getName());
        if (archFile.exists())
        {
            archFile.delete();
        }
        Utils.copy(file, archFile);
        return file.length();
    }
}
