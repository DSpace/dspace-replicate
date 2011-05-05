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
import org.dspace.content.packager.PackageDisseminator;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageIngester;
import org.dspace.content.packager.PackageParameters;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.core.PluginManager;

/**
 * METSPacker packs and unpacks Item AIPs in METS compressed archives
 *
 * @author richardrodgers
 */
public class METSPacker implements Packer
{
    private DSpaceObject dso = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<String>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<RefFilter>();
    
    private PackageDisseminator dip = null;
    private PackageIngester sip = null;

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
        
        Context context = new Context();
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
            throw new IOException(pkgE.getMessage(), pkgE);
        }
        catch (CrosswalkException xwkE)
        {
            throw new IOException(xwkE.getMessage(), xwkE);
        }
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

        Context context = new Context();
        DSpaceObject parent = null;
        PackageParameters pkgParams = new PackageParameters();
        try
        {
            dso = sip.ingest(context, parent, archive, pkgParams, null);
        }
        catch (PackageException pkgE)
        {
            throw new IOException(pkgE.getMessage(), pkgE);
        }
        catch (CrosswalkException xwkE)
        {
            throw new IOException(xwkE.getMessage(), xwkE);
        }
    }

    @Override
    public long size(String method) throws SQLException
    {
        int type = dso.getType();
        if (Constants.COMMUNITY == type)
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
}
