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
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.bagit.BagItAipWriter.BAG_AIP;
import static org.dspace.pack.bagit.BagItAipWriter.OBJ_TYPE_COMMUNITY;
import static org.dspace.pack.bagit.BagItAipWriter.PROPERTIES_DELIMITER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.packager.PackageException;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.xml.metadata.Metadata;
import org.dspace.pack.bagit.xml.metadata.Value;
import org.dspace.pack.bagit.xml.policy.Policies;

/**
 * CommunityPacker Packs and unpacks Community AIPs in Bagit format.
 *
 * @author richardrodgers
 */
public class CommunityPacker implements Packer
{
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();

    // NB - these values must remain synchronized with DB schema -
    // they represent the persistent object state
    private static final String[] fields = {
        "name",
        "short_description",
        "introductory_text",
        "copyright_text",
        "side_bar_text"
    };

    private Community community = null;
    private String archFmt = null;

    public CommunityPacker(Community community, String archFmt)
    {
        this.community = community;
        this.archFmt = archFmt;
    }

    public Community getCommunity()
    {
        return community;
    }

    public void setCommunity(Community community)
    {
        this.community = community;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, SQLException, IOException {
        final Bitstream logo = community.getLogo();

        // object.properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_AIP);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + OBJ_TYPE_COMMUNITY);
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + community.getHandle());

        final List<Community> parents = community.getParentCommunities();
        if (parents != null && !parents.isEmpty()) {
            objectProperties.add(OWNER_ID + PROPERTIES_DELIMITER + parents.get(0).getHandle());
        }
        final Map<String, List<String>> properties = ImmutableMap.of(OBJFILE, objectProperties);

        // collect the metadata
        final Metadata metadata = new Metadata();
        for (String field : fields) {
            final String body = communityService.getMetadata(community, field);
            metadata.addValue(new Value(body, field));
        }

        // collect the policy
        final Policies policy = BagItPolicyUtil.getPolicy(Curator.curationContext(), community);

        return new BagItAipWriter(packDir, archFmt, properties)
            .withLogo(logo)
            .withPolicies(policy)
            .withMetadata(metadata)
            .packageAip();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || !archive.exists()) {
            throw new IOException("Missing archive for community: " + community.getHandle());
        }

        final Context context = Curator.curationContext();
        final BagItAipReader reader = new BagItAipReader(archive.toPath());
        reader.validateBag();

        try {
            final Policies policies = reader.readPolicy();
            BagItPolicyUtil.registerPolicies(community, policies);
        } catch (PackageException e) {
            throw new IOException(e.getMessage(), e);
        }

        final Metadata metadata= reader.readMetadata();
        for (Value value : metadata.getValues()) {
            final String name = value.getName();
            final String body = value.getBody();
            communityService.setMetadata(context, community, name, body);
        }

        final Optional<Path> logo = reader.findLogo();
        if (logo.isPresent()) {
            try (InputStream logoStream = Files.newInputStream(logo.get())) {
                communityService.setLogo(context, community, logoStream);
            }
        }

        communityService.update(context, community);

        reader.clean();
    }

    @Override
    public long size(String method) throws SQLException
    {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null)
        {
            size += logo.getSize();
        }
        // proceed to children, unless 'norecurse' set
        if (! "norecurse".equals(method))
        {
            for (Community comm : community.getSubcommunities())
            {
                size += PackerFactory.instance(comm).size(method);
            }
            for (Collection coll : community.getCollections())
            {
                size += PackerFactory.instance(coll).size(method);
            }
        }
        return size;
    }

    @Override
    public void setContentFilter(String filter)
    {
        // no-op
    }

    @Override
    public void setReferenceFilter(String filter)
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
