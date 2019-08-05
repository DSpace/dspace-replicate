/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.sql.SQLException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.dspace.core.factory.CoreServiceFactory;
import org.dspace.core.service.PluginService;
import org.dspace.curate.Curator;
import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

import static org.dspace.ctask.replicate.Odometer.*;

/**
 * Singleton access point for communicating with replication access providers.
 * ReplicaManager adds a thin accounting or bookkeeping layer, recording
 * activity with the storage provider.
 *
 * @author richardrodgers
 */
public class ReplicaManager {

    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
    private PluginService pluginService = CoreServiceFactory.getInstance().getPluginService();
    private HandleService handleService = HandleServiceFactory.getInstance().getHandleService();

    private Logger log = Logger.getLogger(ReplicaManager.class);
    // singleton instance
    private static ReplicaManager instance = null;
    // the replica provider
    private ObjectStore objStore = null;
    // base directory for replication activities
    private final String repDir = configurationService.getProperty("replicate.base.dir");
    // an odometer for recording activity
    private Odometer odometer = null;
    // lock for updating odometer
    private final Object odoLock = new Object();
    // Primary store group name
    private final String storeGroupName = configurationService.getProperty("replicate.group.aip.name");
    // Delete store group name
    private final String deleteGroupName = configurationService.getProperty("replicate.group.delete.name");
    // Separating character between Type prefix and object identifier, used when packages are named with a Type prefix
    private final String typePrefixSeparator = "@";
    // Special Type prefix for Deletion catalog records
    private final String deletionCatalogPrefix = "DELETION-RECORD";
    // AIP Package compression format (e.g. zip or tgz)
    private final String archFmt = configurationService.getProperty("replicate.packer.archfmt");


    private ReplicaManager() throws IOException
    {
        objStore = (ObjectStore) pluginService.getSinglePlugin(ObjectStore.class);
        if (objStore == null) {
            log.error("No ObjectStore configured in 'replicate.cfg'!");
            throw new IOException("No ObjectStore configured in 'replicate.cfg'!");
        }
        
        objStore.init();
        
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
        return new File(stageDir, storageId(id, null));
    }
    
    
    /**
     * Determine the Identifier of an object once it is placed 
     * in storage. This method ensures any special characters are 
     * escaped. It also ensures all objects are named in a similar
     * manner once they are in a given store (so that they can similarly
     * be retrieved from storage using this same 'storageId').
     * 
     * @param objId - original object id (canonical ID)
     * @param fileExtension - file extension, if any (may be null)
     * @return reformatted storage ID for this object (including file extension)
     */
    public String storageId(String objId, String fileExtension)
    {
        // canonical handle notation bedevils file system semantics
        String storageId = objId.replaceAll("/", "-");
        
        // add appropriate file extension, if needed
        if(fileExtension!=null && !storageId.endsWith("." + fileExtension))
            storageId = storageId + "." + fileExtension;

        // If 'packer.typeprefix' setting is 'true', 
        // then prefix the storageID with the DSpace Type (if it doesn't already have a prefix)
        if(configurationService.getBooleanProperty("replicate.packer.typeprefix", true) &&
           !storageId.contains(typePrefixSeparator))
        {    
            String typePrefix = null;
        
            try
            {    
                //Get object associated with this handle
                DSpaceObject dso = handleService.resolveToObject(Curator.curationContext(), objId);

                //typePrefix format = 'TYPE@'
                if(dso!=null)
                    typePrefix = Constants.typeText[dso.getType()] + typePrefixSeparator;
            }
            catch(SQLException sqle)
            {
                //do nothing, just ignore -- we'll handle this in a moment
            }
            
            // If we were unable to determine a type prefix, then this must mean the object
            // no longer exists in DSpace!  Let's see if we can find it in storage!
            if(typePrefix==null)
            {
                try
                {
                    //Currently we need to try and lookup the object in storage
                    //Hopefully, there will be an easier way to do this in the future
                    
                    //see if this object exists in main storage group
                    typePrefix = findTypePrefix(storeGroupName, storageId);
                    if(typePrefix==null && deleteGroupName!=null) //if not found, check deletion group as well
                        typePrefix = findTypePrefix(deleteGroupName, storageId);
                }
                catch(IOException io)
                {
                    //do nothing, just ignore
                }
            }    
            
            //if we found a typePrefix, prepend it on storageId
            if(typePrefix!=null)
                storageId = typePrefix + storageId;
        }
        
       
        // Return final storage ID
        return storageId;
    }
    
    /**
     * Convert a Storage ID back into a Canonical Identifier
     * (opposite of 'storageId()' method).
     * @param storageId the given object's storage ID
     * @return the objects canonical identifier
     */
    public String canonicalId(String storageId)
    {
        //If this 'storageId' includes a TYPE prefix (see 'storageId()' method),
        // then remove it, before returning the reformatted ID.
        if(storageId.contains(typePrefixSeparator))
            storageId = storageId.substring(storageId.indexOf(typePrefixSeparator)+1);
        
        //If this 'storageId' includes a file extension suffix, also remove it.
        if(storageId.contains("."))
            storageId = storageId.substring(0, storageId.indexOf("."));
        
        //Finally revert all dashes back to slashes (to create the original canonical ID)
        return storageId.replaceAll("-", "/");
    }

    /**
     * Determine the ID of an object's deletion catalog in storage.
     * This method ensures any special characters are
     * escaped. It also ensures all objects are named in a similar
     * manner once they are in a given store (so that they can similarly
     * be retrieved from storage using this same 'storageId').
     *
     * @param objId - original object id (canonical ID)
     * @param fileExtension - file extension, if any (may be null)
     * @return reformatted storage ID for this object (including file extension)
     */
    public String deletionCatalogId(String objId, String fileExtension)
    {
        // canonical handle notation bedevils file system semantics
        String storageId = objId.replaceAll("/", "-");

        // add appropriate file extension, if needed
        if(fileExtension!=null && !storageId.endsWith("." + fileExtension))
            storageId = storageId + "." + fileExtension;

        if(configurationService.getBooleanProperty("replicate.packer.typeprefix", true) &&
           !storageId.contains(typePrefixSeparator))
        {
            //Prepend the "deletion catalog" type prefix on the name
            return deletionCatalogPrefix + typePrefixSeparator + storageId;
        }
        else
        {
            // Otherwise, just return the cleaned up ID
            return storageId;
        }
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
        long size = 0L;
        File file = stage(group, objId);
        size = objStore.fetchObject(group, objId, file);

        if (size > 0L)
        {
            synchronized (odoLock)
            {
                odometer.adjustProperty(DOWNLOADED, size);
                odometer.save();
            }
        }
       
        return file.exists() ? file : null;
    }
    
    public long transferObject(String group, File file) throws IOException {
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
        return size;
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
    
    public boolean moveObject(String srcGroup, String destGroup, String objId) throws IOException {
        long size = objStore.moveObject(srcGroup, destGroup, objId);
        
        // NOTE: no need to adjust the odometer. In this case we haven't 
        // actually uploaded or downloaded any content. 
        if (size > 0L)
            return true;
        else
            return false;
    }
    
    /**
     * This method is only called if we cannot determine an object's type prefix
     * via DSpace (i.e. the object no longer exists in DSpace). In this case,
     * we'll perform some basic searching of the given object store group to see
     * if we can find an object with this ID that has a type prefix.
     * 
     * @param group store group name to search
     * @param baseId base object id we are looking for (without type prefix)
     * @return Type prefix if a matching object is located successfully. Null otherwise.
     */
    private String findTypePrefix(String group, String baseId) throws IOException
    {
        boolean exists = false;
        
        // This next part may look a bit like a hack, but it's actually safer than
        // it seems. Essentially, we are going to try to "guess" what the Type Prefix
        // may be, and see if we can find an object with that name in our object Store.
        // The reason this is still "safe" is that the "objId" should be unique with or without
        // the Type prefix. Even if it wasn't unique, DSpace HandleManager has checks in place 
        // to ensure we can never restore an object of a different Type to a Handle that was 
        // used previously (e.g. cannot restore an Item with a handle that was previously used by a Collection)
        
        // NOTE: If DSpace ever provided a way to lookup Object type for an unbound handle, then
        // we may no longer need to guess which type this object may have been.
        // ALTERNATIVELY: If DuraCloud & other stores provide a way to search by file properties, we could change
        // our store plugins to always save the object handle as a property & retrieve files via that property.

        //Most objects are Items, so lets see if this object can be found with an Item Type prefix
        String typePrefix = Constants.typeText[Constants.ITEM] + typePrefixSeparator;
        exists = objStore.objectExists(group, typePrefix + baseId);

        if(!exists)
        {
            //Ok, our second guess will be that this used to be a Collection
            typePrefix = Constants.typeText[Constants.COLLECTION] + typePrefixSeparator;
            exists = objStore.objectExists(group, typePrefix + baseId);
        }

        if(!exists)
        {
            //Final guess: maybe this used to be a Community?
            typePrefix = Constants.typeText[Constants.COMMUNITY] + typePrefixSeparator;
            exists = objStore.objectExists(group, typePrefix + baseId);
        }    
        
        // That's it. We're done guessing. If we still couldn't find this object, 
        // it obviously doesn't exist in our object Store.
        if(exists)
            return typePrefix;
        else
            return null;
    }
}
