/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.pack;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.packager.AbstractPackageIngester;
import org.dspace.content.packager.PackageDisseminator;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageIngester;
import org.dspace.content.packager.PackageParameters;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.core.PluginManager;
import org.dspace.curate.Curator;

import org.apache.log4j.Logger;

/**
 * METSPacker packs and unpacks Item AIPs in METS compressed archives
 *
 * @author richardrodgers
 */
public class METSPacker implements Packer
{
    private Logger log = Logger.getLogger(METSPacker.class);
    
    private DSpaceObject dso = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<String>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<RefFilter>();
    
    private List<String> childPackageRefs = new ArrayList<String>();
    
    private PackageDisseminator dip = null;
    private PackageIngester sip = null;

    public METSPacker(String archFmt)
    {
        this.dso = null;
        this.archFmt = archFmt;
    }
    
    public METSPacker(DSpaceObject dso, String archFmt)
    {
        this.dso = dso;
        this.archFmt = archFmt;
    }

    public DSpaceObject getDSO()
    {
        return dso;
    }

    public void setDSO(DSpaceObject dso)
    {
        this.dso = dso;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException
    {
        //retrieve specified package disseminator
        if (dip == null)
        {
            dip = (PackageDisseminator)PluginManager.
                  getNamedPlugin(PackageDisseminator.class, "AIP");
        }
        if (dip == null)
        {
            throw new IOException("Cannot obtain AIP disseminator. No dissemination plugin named 'AIP' is configured.");
        }
        
        //Retrieve a Context object, authenticated as the current Task performer.
        Context context = Curator.authenticatedContext();
        //Initialize packaging params
        PackageParameters pkgParams = new PackageParameters();
        File archive = new File(packDir.getParentFile(), packDir.getName() + "." + archFmt);
        //disseminate the requested object
        try
        {
            dip.disseminate(context, dso, pkgParams, archive);
        }
        catch (PackageException pkgE)
        {
            //abort context & undo any changes
            context.abort();
            throw new IOException(pkgE.getMessage(), pkgE);
        }
        catch (CrosswalkException xwkE)
        {
            //abort context & undo any changes
            context.abort();
            throw new IOException(xwkE.getMessage(), xwkE);
        }
        //complete & close context
        context.complete();
        
        return archive;
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null || ! archive.exists()) {
            throw new IOException("Missing archive for object: " + dso.getHandle());
        }
        if (sip == null)
        {
            sip = (PackageIngester) PluginManager
                    .getNamedPlugin(PackageIngester.class, "AIP");
        }
        if (sip == null)
        {
            throw new IOException("Cannot obtain AIP ingester. No ingestion plugin named 'AIP' is configured.");
        }

        //Retrieve a Context object, authenticated as the current Task performer.
        Context context = Curator.authenticatedContext();
        PackageParameters pkgParams = new PackageParameters();
        
        //--- Default settings/parameters for PackageIngester --
        // @TODO - May want to make these configurable somehow in replicate.cfg or similar
        // For more info, see: https://wiki.duraspace.org/display/DSDOC/AIP+Backup+and+Restore
        //Always run in Replace mode (always replace existing objects & restore ones that are missing)
        pkgParams.setReplaceModeEnabled(true);
        //Always run in Recursive mode (also replace/restore all child objects)
        pkgParams.setRecursiveModeEnabled(true);
        //Always create Metadata Fields referenced in an AIP, which are found to be missing in DSpace
        pkgParams.setProperty("createMetadataFields", "true");
        //Always skip over an object if it's Parent Object is "missing". These errors will still be logged as warnings.
        //(This setting is recommended for 'recursive' mode, as sometimes ingester will try to restore a child object
        // before its parent. But, once parent is restored, the child object will then be restored immediately after)
        pkgParams.setProperty("skipIfParentMissing", "true");
        
        try
        {
            if(sip instanceof AbstractPackageIngester)
            {
                DSpaceObject replacedDso = sip.replace(context, dso, archive, pkgParams);
               
                //We can only recursively replace non-Items
                //(NOTE: Items have no children, as Bitstreams/Bundles are created from Item packages)
                if(replacedDso!=null && replacedDso.getType()!=Constants.ITEM)
                {
                    //Check if we found child package references when replacing this latest DSpaceObject
                    this.childPackageRefs = ((AbstractPackageIngester) sip).getPackageReferences(replacedDso);
                }//end if not an Item
            }
            else
            {
                // We will always run a replaceAll() in order to perform a recursive replace/recovery. 
                // If the object doesn't exist, this will automatically call sip.ingest() to recover it. 
                // If the object does exist, it will try to replace it with contents of AIP.
                sip.replaceAll(context, dso, archive, pkgParams);
            }
            
        }
        catch (PackageException pkgE)
        {
            //abort context & undo any changes
            if(context!=null)
                context.abort();
            throw new IOException(pkgE.getMessage(), pkgE);
        }
        catch (CrosswalkException xwkE)
        {
            //abort context & undo any changes
            if(context!=null)
                context.abort();
            throw new IOException(xwkE.getMessage(), xwkE);
        }
        //complete & close context
        context.complete();
    }

    @Override
    public long size(String method) throws SQLException
    {
        int type = dso.getType();
        if (Constants.SITE == type)
        {
            return siteSize();
        }
        else if (Constants.COMMUNITY == type)
        {
            return communitySize((Community)dso);
        }
        else if (Constants.COLLECTION == type)
        {
            return collectionSize((Collection)dso);
        }
        else
        {
            return itemSize((Item)dso);
        }     
    }
    
    /**
     * Determine total estimated size of all AIPs 
     * (Site AIP, Community AIPs, Collection AIPs, Item AIPs)
     * <P>
     * Estimated size is currently just based on size of content files.
     * 
     * @return estimated storage size
     * @throws SQLException 
     */
    private long siteSize() throws SQLException
    {
        long size = 0L;
        
        //Retrieve a Context object, authenticated as the current Task performer.
        Context ctx = Curator.authenticatedContext();
        
        //This Site AIP itself is very small, so as a "guess" we'll just total
        // up the size of all Community, Collection & Item AIPs
        //Then, perform this task for all Top-Level Communities in the Site
        // (this will recursively perform task for all objects in DSpace)
        for (Community subcomm : Community.findAllTop(ctx))
        {
            size += communitySize(subcomm);
        }

        //complete & close context.
        ctx.complete();
        
        return size;
    }
    
    /**
     * Determine total estimated size of Community AIP and all child AIPs
     * (Sub-Community AIPs, Collection AIPs, Item AIPs)
     * <P>
     * Estimated size is currently just based on size of content files.
     * 
     * @param community DSpace Community
     * @return estimated storage size
     * @throws SQLException 
     */
    private long communitySize(Community community) throws SQLException
    {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        for (Community comm : community.getSubcommunities())
        {
            size += communitySize(comm);
        }
        for (Collection coll : community.getCollections())
        {
            size += collectionSize(coll);
        }
        return size;
    }
    
    /**
     * Determine total estimated size of Collection AIP and all child AIPs
     * (Item AIPs)
     * <P>
     * Estimated size is currently just based on size of content files.
     * 
     * @param collection DSpace Collection
     * @return estimated storage size
     * @throws SQLException 
     */
    private long collectionSize(Collection collection) throws SQLException
    {
        long size = 0L;
        // start with logo size, if present
        Bitstream logo = collection.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        ItemIterator itemIter = collection.getItems();
        while (itemIter.hasNext())
        {
            size += itemSize(itemIter.next());
        }
        return size; 
    }
    
   /**
     * Determine total estimated size of Item AIP
     * <P>
     * Estimated size is currently just based on size of content files.
     * 
     * @param item DSpace Item
     * @return estimated storage size
     * @throws SQLException 
     */
    private long itemSize(Item item) throws SQLException
    {
        long size = 0L;
        // just total bitstream sizes, respecting filters
        for (Bundle bundle : item.getBundles())
        {
            if (accept(bundle.getName()))
            {
                for (Bitstream bs : bundle.getBitstreams())
                {
                    size += bs.getSize();
                }
            }
        }
        return size;
    }
    
    @Override
    public void setContentFilter(String filter)
    {
        // filters currently just lists of bundle names
        // if first list element is "+", the list
        // is inclusive, otherwise all bundles *except*
        // the listed ones are included
        filterBundles = Arrays.asList(filter.split(","));
        if ("+".equals(filterBundles.get(0)))
        {
            exclude = false;
            filterBundles.remove(0);
        }
    }

    private boolean accept(String name)
    {
        boolean onList = filterBundles.contains(name);
        return exclude ? ! onList : onList;
    }

    @Override
    public void setReferenceFilter(String filterSet)
    {
        // parse ref filter list
        for (String filter : filterSet.split(","))
        {
            refFilters.add(new RefFilter(filter));
        }
    }

    private String byReference(Bundle bundle, Bitstream bs)
    {
        for (RefFilter filter : refFilters) {
            if (filter.bundle.equals(bundle.getName()) &&
                filter.size == bs.getSize())
            {
                return filter.url;
            }
        }
        return null;
    }

    private class RefFilter
    {
        public String bundle;
        public long size;
        public String url;

        public RefFilter(String filter)
        {
            String[] parts = filter.split(" ");
            bundle = parts[0];
            size = Long.valueOf(parts[1]);
            url = parts[2];
        }
    }
    
    public List<String> getChildPackageRefs()
    {
        return childPackageRefs;
    }
}
