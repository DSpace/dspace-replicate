/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.BAG_TYPE;
import static org.dspace.pack.PackerFactory.OBJECT_ID;
import static org.dspace.pack.PackerFactory.OBJECT_TYPE;
import static org.dspace.pack.PackerFactory.OBJFILE;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_AIP;
import static org.dspace.pack.bagit.BagItAipWriter.DEFAULT_MODIFIED_DATE;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.PackageException;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.pack.Packer;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;

/**
 * Packer for a DSpace {@link Site} object in to a BagIt bag
 *
 * @author mikejritter
 */
public class SitePacker implements Packer {

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();

    private final Context context;
    private final Site site;
    private final String archFmt;

    private List<String> members;

    public SitePacker(Context context, Site site, String archFmt) {
        this.context = context;
        this.site = site;
        this.archFmt = archFmt;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        final Map<String, List<String>> properties = new HashMap<>();
        // object.properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_AIP);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + "site");
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + site.getHandle());
        properties.put(OBJFILE, objectProperties);

        // human readable metadata (dspace.properties)
        final List<String> dspaceProperties = new ArrayList<>();
        dspaceProperties.add("Site-Handle" + PROPERTIES_DELIMITER + site.getHandle());
        dspaceProperties.add("DSpace-Version" + PROPERTIES_DELIMITER + Util.getSourceVersion());
        properties.put("dspace.properties", dspaceProperties);

        // add handles of all DSpaceObjects found in the site
        final List<String> handles = new ArrayList<>();
        final List<Community> allTopCommunities = communityService.findAllTop(context);
        for (Community community : allTopCommunities) {
            appendHandles(handles, community);
        }
        properties.put("members", handles);

        DSpaceRoles dSpaceRoles;
        try {
            dSpaceRoles = BagItRolesUtil.getDSpaceRoles(context, site);
        } catch (PackageException exception) {
            throw new IOException(exception);
        }

        return new BagItAipWriter(context, packDir, archFmt, properties)
            .withDSpaceRoles(dSpaceRoles)
            .withLastModifiedTime(DEFAULT_MODIFIED_DATE)
            .packageAip();
    }

    /**
     * Appends all handles of the {@code dso} and any child DSpaceObjects to the given {@code handles} list.
     *
     * This allows the SitePacker to be similar to the CatalogPacker in that it can restore a Site and all DSOs using
     * the relationships contained within the AIP.
     *
     * @param handles the List to append to
     * @param dso the DSO whose handle to append
     * @throws SQLException if there's an error retrieving any DSOs from the database
     */
    private void appendHandles(final List<String> handles, final DSpaceObject dso) throws SQLException {
        handles.add(dso.getHandle());
        if (dso.getType() == Constants.COMMUNITY) {
            final Community community = (Community) dso;
            for (Community subcommunity : community.getSubcommunities()) {
                appendHandles(handles, subcommunity);
            }
            for (Collection collection : community.getCollections()) {
                appendHandles(handles, collection);
            }

        } else if (dso.getType() == Constants.COLLECTION) {
            final Collection collection = (Collection) dso;
            final Iterator<Item> items = itemService.findAllByCollection(context, collection);
            while (items.hasNext()) {
                final Item item = items.next();
                handles.add(item.getHandle());
            }
        }
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || !archive.exists()) {
            throw new IOException("Missing archive for site: " + site.getHandle());
        }

        final BagItAipReader reader = new BagItAipReader(archive.toPath());
        reader.validateBag();

        try {
            // try and ingest the roles for the site
            final Optional<Path> roles = reader.findRoles();
            if (roles.isPresent()) {
                BagItRolesUtil.ingest(context, site, roles.get());
            }
        } catch (PackageException e) {
            throw new IOException(e);
        }

        // Read the members so we can try to restore all other DSOs in the Site
        this.members = reader.readFile("members");
        reader.clean();
    }

    /**
     * Get the {@code members} for this Site; only populated on unpack.
     *
     * @return the list of members for the site, each represented the objects handle
     */
    public Optional<List<String>> getMembers() {
        return Optional.fromNullable(members);
    }

    @Override
    public long size(String method) throws SQLException {
        // not supported
        return 0;
    }

    @Override
    public void setContentFilter(String filter) {
    }

    @Override
    public void setReferenceFilter(String filter) {
    }
}
