package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.BAG_TYPE;
import static org.dspace.pack.PackerFactory.CREATE_TS;
import static org.dspace.pack.PackerFactory.OBJECT_ID;
import static org.dspace.pack.PackerFactory.OBJECT_TYPE;
import static org.dspace.pack.PackerFactory.OBJFILE;
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_AIP;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_MAN;
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_DELETION;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.app.util.Util;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Community;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageUtils;
import org.dspace.content.service.CommunityService;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;

/**
 *
 * @author mikejritter
 */
public class SitePacker implements Packer {

    private final Site site;
    private final String archFmt;

    private final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();

    public SitePacker(Site site, String archFmt) {
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

        // collect the DSpaceRoles
        DSpaceRoles dSpaceRoles;
        try {
            dSpaceRoles = BagItRolesUtil.getDSpaceRoles(site);
        } catch (PackageException exception) {
            throw new IOException(exception);
        }

        // human readable metadata
        final List<String> dspaceProperties = new ArrayList<>();
        dspaceProperties.add("Site-Handle" + PROPERTIES_DELIMITER + site.getHandle());
        dspaceProperties.add("DSpace-Version" + PROPERTIES_DELIMITER + Util.getSourceVersion());
        properties.put("dspace.properties", dspaceProperties);

        // add all top level communities
        final List<String> communities = new ArrayList<>();
        final List<Community> allTopCommunities = communityService.findAllTop(Curator.curationContext());
        for (Community community : allTopCommunities) {
            final String packageName = PackageUtils.getPackageName(community, archFmt);
            communities.add(community.getHandle() + PROPERTIES_DELIMITER + packageName);
        }
        properties.put("communities", communities);

        return new BagItAipWriter(packDir, archFmt, properties)
            .withDSpaceRoles(dSpaceRoles)
            .packageAip();
    }

    @Override
    public void unpack(File archFile) throws AuthorizeException, IOException, SQLException {

    }

    @Override
    public long size(String method) throws SQLException {
        return 0;
    }

    @Override
    public void setContentFilter(String filter) {

    }

    @Override
    public void setReferenceFilter(String filter) {

    }
}
