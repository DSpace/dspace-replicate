/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.BAG_TYPE;
import static org.dspace.pack.PackerFactory.CREATE_TS;
import static org.dspace.pack.PackerFactory.OBJECT_ID;
import static org.dspace.pack.PackerFactory.OBJECT_TYPE;
import static org.dspace.pack.PackerFactory.OBJFILE;
import static org.dspace.pack.PackerFactory.OWNER_ID;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.dspace.authorize.AuthorizeException;
import org.dspace.pack.Packer;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * CatalogPacker packs and unpacks Object catalogs in Bagit format. These
 * catalogs are typically used as deletion 'receipts' - i.e. records of what
 * was deleted.
 *
 * @author richardrodgers
 */
public class CatalogPacker implements Packer
{
    private ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

    private String objectId = null;
    private String ownerId = null;
    private List<String> members = null;
    // Package compression format (e.g. zip or tgz) - Catalog packer uses same as AIPs
    private String archFmt = configurationService.getProperty("replicate.packer.archfmt");

    public CatalogPacker(String objectId)
    {
        this.objectId = objectId;
    }
    
    public CatalogPacker(String objectId, String ownerId, List<String> members)
    {
        this.objectId = objectId;
        this.ownerId = ownerId;
        this.members = members;
    }

    public String getOwnerId()
    {
        return ownerId;
    }

    public List<String> getMembers()
    {
        return members;
    }

    @Override
    public File pack(File packDir) throws IOException, SQLException, AuthorizeException {
        final Map<String, List<String>> properties = new HashMap<>();
        // object.properties
        final List<String> objectProperties = new ArrayList<>();
        objectProperties.add(BAG_TYPE + "  " +  BagItAipWriter.BAG_MAN);
        objectProperties.add(OBJECT_TYPE + "  " +  BagItAipWriter.OBJ_TYPE_DELETION);
        objectProperties.add(OBJECT_ID + "  " +  objectId);
        objectProperties.add(CREATE_TS + "  " +  String.valueOf(System.currentTimeMillis()));
        if (ownerId != null) {
            objectProperties.add(OWNER_ID + "  " +  ownerId);
        }
        properties.put(OBJFILE, objectProperties);

        // members...properties
        if (members.size() > 0) {
            // todo: I don't think properties makes sense for this...
            properties.put("members", members);
        }

        BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, null, properties,
                                                      Collections.<XmlElement>emptyList(),
                                                      Collections.<BagBitstream>emptyList());
        return aipWriter.packageAip();
    }

    @Override
    public void unpack(File archive) throws IOException
    {
        if (archive == null)
        {
            throw new IOException("Missing archive for catalog: " + objectId);
        }
        Bag bag = new Bag(archive);
        // just populate the member list
        InputStream bagIn = bag.dataStream(OBJFILE);
        Properties props = new Properties();
        props.load(bagIn);
        bagIn.close();
        ownerId = props.getProperty(OWNER_ID);
        members = new ArrayList<String>();
        Bag.FlatReader reader = bag.flatReader("members");
        if (reader != null)
        {
            String member = null;
            while ((member = reader.readLine()) != null)
            {
                members.add(member);
            }
            reader.close();
        }
        // clean up bag
        bag.empty();
    }



    @Override
    public long size(String method)
    {
        // not currently implemented
        return 0L;
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
