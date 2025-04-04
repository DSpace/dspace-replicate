/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.mets;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.AbstractPackageIngester;
import org.dspace.content.packager.PackageDisseminator;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageIngester;
import org.dspace.content.packager.PackageParameters;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.factory.CoreServiceFactory;
import org.dspace.core.service.PluginService;
import org.dspace.pack.Packer;
import org.dspace.workflow.WorkflowException;

/**
 * METSPacker packs and unpacks Item AIPs in METS compressed archives
 *
 * @author richardrodgers
 */
public class METSPacker implements Packer {
    private PluginService pluginService = CoreServiceFactory.getInstance().getPluginService();
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private Logger log = LogManager.getLogger();

    /** The context to use */
    private Context context;
    /** Related DSpace Object **/
    private DSpaceObject dso = null;
    /** Exported archive format (e.g. zip) **/
    private String archFmt = null;
    /** Specified content filter string for exported AIP (e.g. 'packer.cfilter' in replication.cfg) **/
    private String contentFilter = null;
    /** Bundles which will be filtered in exported AIP **/
    private List<String> filterBundles = new ArrayList<String>();
    /** Whether bundles in 'filterBundles' will be included or excluded from exported AIP **/
    private boolean exclude = true;

    /** List of all referenced child packages **/
    private List<String> childPackageRefs = new ArrayList<String>();

    /** DSpace PackageDisseminator & PackageIngester to utilize for AIP pack() & unpack() **/
    private PackageDisseminator dip = null;
    private PackageIngester sip = null;

    public METSPacker(Context context, String archFmt) {
        this.context = context;
        this.dso = null;
        this.archFmt = archFmt;
    }

    public METSPacker(Context context, DSpaceObject dso, String archFmt) {
        this.context = context;
        this.dso = dso;
        this.archFmt = archFmt;
    }

    /**
     * Set application context
     * @param context
     */
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Get DSpaceObject we are working with
     * @return dso DSpaceObject
     */
    public DSpaceObject getDSO() {
        return dso;
    }

    /**
     * Set DSpaceObject we are working with
     * @param dso DSpaceObject
     */
    public void setDSO(DSpaceObject dso) {
        this.dso = dso;
    }

    /**
     * Create a METS AIP Package, using the configured AIP PackageDisseminator (in dspace.cfg)
     *
     * @param packDir the directory where this package should be created
     * @return the created package file
     * @throws AuthorizeException if authorize error
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        // retrieve specified package disseminator
        if (dip == null) {
            dip = (PackageDisseminator) pluginService.
                  getNamedPlugin(PackageDisseminator.class, "AIP");
        }

        if (dip == null) {
            throw new IOException("Cannot obtain AIP disseminator. No dissemination plugin named 'AIP' is configured.");
        }

        // Initialize packaging params
        PackageParameters pkgParams = new PackageParameters();

        // If a content (Bundle) filter is specified
        if (this.contentFilter != null && !this.contentFilter.isEmpty()) {
            // Pass that on to the 'filterBundles' package parameter (which uses the same syntax)
            pkgParams.addProperty("filterBundles", contentFilter);
        }

        File archive = new File(packDir.getParentFile(), packDir.getName() + "." + archFmt);

        // disseminate the requested object
        try {
            dip.disseminate(context, dso, pkgParams, archive);
        } catch (PackageException pkgE) {
            throw new IOException(pkgE.getMessage(), pkgE);
        } catch (CrosswalkException xwkE) {
            throw new IOException(xwkE.getMessage(), xwkE);
        }

        return archive;
    }

    /**
     * Restore/Replace a DSpaceObject based on the contents of a METS AIP Package,
     * using the configured AIP PackageIngester (in dspace.cfg)
     *
     * @param archive the METS AIP package
     * @throws AuthorizeException if authorize error
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        // unpack with default PackageParameter settings
        unpack(archive, null);
    }

    /**
     * Restore/Replace a *single* DSpaceObject based on the contents of a METS AIP Package,
     * and the given PackageParameters. This uses the configured AIP PackageIngester
     * (in dspace.cfg).
     * <p>
     * Please note that it is up to the calling Curation Task
     * to perform any recursive restores/replaces of child objects.
     * If recursiveMode is enabled, this method will attempt to save all referenced
     * child packages for access via getChildPackageRefs().
     *
     * @param archive the METS AIP package
     * @param pkgParams the PackageParameters (if null, defaults to Recursive Replace settings)
     * @throws AuthorizeException if authorize error
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    public void unpack(File archive, PackageParameters pkgParams) throws AuthorizeException, IOException, SQLException {
        if (archive == null || ! archive.exists()) {
            throw new IOException("Missing archive for object: " + dso.getHandle());
        }

        if (sip == null) {
            sip = (PackageIngester) pluginService
                    .getNamedPlugin(PackageIngester.class, "AIP");
        }

        if (sip == null) {
            throw new IOException("Cannot obtain AIP ingester. No ingestion plugin named 'AIP' is configured.");
        }

        if (pkgParams == null || pkgParams.isEmpty()) {
            pkgParams = new PackageParameters();

            //--- Default settings/parameters for PackageIngester --
            // These default settings should work for a wide variety of replace/restore actions.
            // For more info, see: https://wiki.duraspace.org/display/DSDOC18/AIP+Backup+and+Restore
            // Always run in Replace mode (always replace existing objects & restore ones that are missing)
            pkgParams.setReplaceModeEnabled(true);

            // Always run in Recursive mode (also replace/restore all child objects)
            pkgParams.setRecursiveModeEnabled(true);

            // Always create Metadata Fields referenced in an AIP, which are found to be missing in DSpace
            pkgParams.setProperty("createMetadataFields", "true");

            // Always skip over an object if it's Parent Object is "missing". These errors will still be
            // logged as warnings. (This setting is recommended for 'recursive' mode, as sometimes ingester
            // will try to restore a child object before its parent. But, once parent is restored, the child
            // object will then be restored immediately after)
            pkgParams.setProperty("skipIfParentMissing", "true");
        }

        DSpaceObject updatedDso = null;
        try {
            // Because we may be unpacking AIPs from an external storage location (e.g. DuraCloud or mapped drive)
            // we will only ever replace/restore a SINGLE OBJECT at a time.
            // It is up to the Replication Task to ensure each item is downloaded as needed and its
            // children are also replaced/restored recursively as needed.
            if (pkgParams.replaceModeEnabled()) {
                // run object replace
                updatedDso = sip.replace(context, dso, archive, pkgParams);
            } else {
                // else run a 'restore' (special form of ingest) with a null parent object
                // (parent obj will be determined from package manifest)
                updatedDso = sip.ingest(context, null, archive, pkgParams, null);
            }
        } catch (IllegalStateException isE) {
            // NOTE: we should only encounter an IllegalStateException, when
            // attempting to ingest an object that already exists in the system.
            // (i.e. this is a "handle already in use" exception)

            // if we are skipping over (i.e. keeping) existing objects
            if (pkgParams.keepExistingModeEnabled()) {
                // just log a warning
                log.warn("Skipping over object which already exists.");
            } else {
                // Pass this exception on -- which essentially causes a full rollback
                // of all changes (this is the default)
                throw isE;
            }
        } catch (PackageException pkgE) {
            throw new IOException(pkgE.getMessage(), pkgE);
        } catch (CrosswalkException xwkE) {
            throw new IOException(xwkE.getMessage(), xwkE);
        } catch (WorkflowException wfE) {
            throw new IOException(wfE.getMessage(), wfE);
        }

        // If we are using a class that extends AbstractPackageIngester,
        // then we can save the child AIP references for later processing.
        // (When recursion is enabled, this step ensures the calling curation task
        // knows which child AIPs to next restore/replace)
        if (sip instanceof AbstractPackageIngester) {
            // Only non-Items reference other child AIPs
            // (NOTE: Items have no children, as Bitstreams/Bundles are contained in Item packages)
            if (updatedDso != null && updatedDso.getType() != Constants.ITEM) {
                // Check if we found child package references when unpacking this latest package into a DSpaceObject
                this.childPackageRefs = ((AbstractPackageIngester) sip).getPackageReferences(updatedDso);
            }
        }

        // NOTE: Context is handled by Curator -- it will commit or close when needed.
    }

    @Override
    public long size(String method) throws SQLException {
        int type = dso.getType();
        if (Constants.SITE == type) {
            return siteSize();
        } else if (Constants.COMMUNITY == type) {
            return communitySize((Community)dso);
        } else if (Constants.COLLECTION == type) {
            return collectionSize((Collection)dso);
        } else {
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
     * @throws SQLException if database error
     */
    private long siteSize() throws SQLException {
        long size = 0L;

        // This Site AIP itself is very small, so as a "guess" we'll just total
        // up the size of all Community, Collection & Item AIPs
        // Then, perform this task for all Top-Level Communities in the Site
        // (this will recursively perform task for all objects in DSpace)
        for (Community subcomm : communityService.findAllTop(context)) {
            size += communitySize(subcomm);
        }

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
     * @throws SQLException if database error
     */
    private long communitySize(Community community) throws SQLException {
        long size = 0L;

        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null) {
            size += logo.getSizeBytes();
        }

        // Calculate sub-communities
        for (Community comm : community.getSubcommunities()) {
            size += communitySize(comm);
        }

        // Calculate collections
        for (Collection coll : community.getCollections()) {
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
     * @throws SQLException  if database error
     */
    private long collectionSize(Collection collection) throws SQLException {
        long size = 0L;

        // start with logo size, if present
        Bitstream logo = collection.getLogo();
        if (logo != null) {
            size += logo.getSizeBytes();
        }

        // Calculate items
        Iterator<Item> itemIter = itemService.findByCollection(context, collection);
        while (itemIter.hasNext()) {
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
     * @throws SQLException  if database error
     */
    private long itemSize(Item item) throws SQLException {
        long size = 0L;

        // just total bitstream sizes, respecting filters
        for (Bundle bundle : item.getBundles()) {
            if (includedBundle(bundle.getName())) {
                for (Bitstream bs : bundle.getBitstreams()) {
                    size += bs.getSizeBytes();
                }
            }
        }

        return size;
    }

    @Override
    public void setContentFilter(String filter) {
        // If our filter list of bundles begins with a '+', then this list
        // specifies all the bundles to *include*. Otherwise all
        // bundles *except* the listed ones are included
        if (filter.startsWith("+")) {
            exclude = false;
            // remove the preceding '+' from our bundle list
            filter = filter.substring(1);
        }

        contentFilter = filter;
        filterBundles = Arrays.asList(filter.split(","));
    }

    private boolean includedBundle(String name) {
        boolean onList = filterBundles.contains(name);
        return exclude ? ! onList : onList;
    }

    @Override
    public void setReferenceFilter(String filterSet) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Return list of referenced child packages (AIPs) which were
     * located during the unpacking of an AIP (see unpack()).
     * <P>
     * This may be used by tasks so that they can recursively unpack()
     * additional referenced child packages as needed.
     *
     * @return List of references to child AIPs
     */
    public List<String> getChildPackageRefs() {
        return childPackageRefs;
    }
}
