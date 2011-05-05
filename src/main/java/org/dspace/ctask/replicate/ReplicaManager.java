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

import org.apache.log4j.Logger;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.PluginManager;

import static org.dspace.ctask.replicate.Odometer.*;

/**
 * Singleton access point for communicating with replication access providers.
 * ReplicaManager adds a thin accounting or bookkeeping layer, recording
 * activity with the storage provider.
 *
 * @author richardrodgers
 */
public class ReplicaManager {

    private Logger log = Logger.getLogger(ReplicaManager.class);
    // singleton instance
    private static ReplicaManager instance = null;
    // the replica provider
    private ObjectStore objStore = null;
    // base directory for replication activities
    private final String repDir = ConfigurationManager.getProperty("replicate", "base.dir");
    // an odometer for recording activity
    private Odometer odometer = null;
    // lock for updating odometer
    private final Object odoLock = new Object();


    private ReplicaManager() throws IOException
    {
        objStore = (ObjectStore)PluginManager.getSinglePlugin("replicate", ObjectStore.class);
        if (objStore == null) {
            log.error("No ObjectStore configured in 'replicate.cfg'!");
            throw new IOException("No ObjectStore configured in 'replicate.cfg'!");
        }
        try
        {
            objStore.init();
        }
        catch (IOException ioE)
        {
            //log error & pass this exception on to the next handler.
            log.error("Replica store initialization error", ioE);
            throw ioE;
        }
        // create directory structures
        new File(repDir).mkdirs();
        // load our odometer - writeable copy
        try
        {
            odometer = new Odometer(repDir, false);
        }
        catch (IOException ioE)
        {
            //just log a warning
            log.warn("Unable to read odometer file in '"+ repDir + "'", ioE);
        }
    }

    public static synchronized ReplicaManager instance() throws IOException
    {
        if (instance == null)
        {
            instance = new ReplicaManager();
        }
        return instance;
    }
    
    public File stage(String group, String id)
    {
        // ensure path exists
        File stageDir = new File(repDir + File.separator + group);
        if (! stageDir.isDirectory())
        {
            stageDir.mkdirs();
        }
        return new File(stageDir, safeId(id)); 
    }
    
    public static String safeId(String id)
    {
        // canonical handle notation bedevils file system semantics
        return id.replaceAll("/", "-");
    }
    
    public static String canonicalId(String safeId)
    {
        return safeId.replaceAll("-", "/");
    }

    public Odometer getOdometer() throws IOException
    {
        // return a new read-only copy
        return new Odometer(repDir, true);
    }

    // Replica store-backed methods

    public File fetchObject(String group, String objId) throws IOException
    {
        //String repId = safeId(id) + "." + arFmt;
        File file = stage(group, objId);
        long size = objStore.fetchObject(group, objId, file);
        if (size > 0L)
        {
            synchronized (odoLock)
            {
                odometer.adjustProperty(DOWNLOADED, size);
                odometer.save();
            }
        }
        /*
        // does bag have holes to fill?
        for (String refStr : bag.getDataRefs()) {
            String[] parts = refStr.split(" ");
            String relPath = parts[2].substring("data/".length());
            long refSize = Long.valueOf(parts[1]);
            InputStream in = new URL(parts[0]).openStream();
            bag.addData(relPath, refSize, in);
        }
         * 
         */
        return file.exists() ? file : null;
    }
    
    public void transferObject(String group, File file) throws IOException {
        String psStr = objStore.objectAttribute(group, file.getName(), "sizebytes");
        long prevSize = psStr != null ? Long.valueOf(psStr) : 0L;
        long size = objStore.transferObject(group, file);
        if (size > 0L) {
            synchronized (odoLock) {
                odometer.adjustProperty(UPLOADED, size);
                // this may be an update - not a new object
                odometer.adjustProperty(SIZE, size - prevSize);
                if (prevSize == 0L) {
                    odometer.adjustProperty(COUNT, 1L);
                }
                odometer.save();
            }
        }       
    }
    
    public boolean objectExists(String group, String objId) throws IOException {
        return objStore.objectExists(group, objId);
    }

    public String objectAttribute(String group, String objId, String attrName) throws IOException {
        return objStore.objectAttribute(group, objId, attrName);
    }

    public void removeObject(String group, String objId) throws IOException {
        long size = objStore.removeObject(group, objId);
        if (size > 0L) {
            synchronized (odoLock) {
                odometer.adjustProperty(SIZE, -size);
                odometer.adjustProperty(COUNT, -1L);
                odometer.save();
            }
        }
    }
}
