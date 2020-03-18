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
import static org.dspace.pack.bagit.BagItAipWriter.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.duraspace.bagit.BagDeserializer;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.SerializationSupport;

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
        objectProperties.add(BAG_TYPE + PROPERTIES_DELIMITER + BAG_MAN);
        objectProperties.add(OBJECT_TYPE + PROPERTIES_DELIMITER + OBJ_TYPE_DELETION);
        objectProperties.add(OBJECT_ID + PROPERTIES_DELIMITER + objectId);
        objectProperties.add(CREATE_TS + PROPERTIES_DELIMITER + System.currentTimeMillis());
        if (ownerId != null) {
            objectProperties.add(OWNER_ID + PROPERTIES_DELIMITER + ownerId);
        }
        properties.put(OBJFILE, objectProperties);

        // members file
        if (members.size() > 0) {
            properties.put("members", members);
        }

        final BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, null, properties,
                                                            Collections.<XmlElement>emptyList(),
                                                            Collections.<BagBitstream>emptyList());
        return aipWriter.packageAip();
    }

    @Override
    public void unpack(File archive) throws IOException {
        if (archive == null) {
            throw new IOException("Missing archive for catalog: " + objectId);
        }

        final Path bagPath;
        if (archive.isFile()) {
            final BagProfile profile = new BagProfile(BagProfile.BuiltIn.BEYOND_THE_REPOSITORY);
            final BagDeserializer deserializer = SerializationSupport.deserializerFor(archive.toPath(), profile);
            bagPath = deserializer.deserialize(archive.toPath());
        } else {
            bagPath = archive.toPath();
        }

        // just populate the member list
        InputStream bagIn = Files.newInputStream(bagPath.resolve("data").resolve(OBJFILE));
        Properties props = new Properties();
        props.load(bagIn);
        bagIn.close();
        ownerId = props.getProperty(OWNER_ID);

        try {
            members = Files.readAllLines(bagPath.resolve("data").resolve("members"), Charset.defaultCharset());
        } catch (FileNotFoundException ignored) {
            members = new ArrayList<>();
        }

        // clean up bag
        delete(bagPath.toFile());
    }

    /**
     * Delete a directory and it's files/subdirectories
     *
     * @param directory the directory to delete
     */
    private void delete(final File directory) {
        // protect against being sent a file instead of a directory
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    delete(file);
                } else {
                    file.delete();
                }
            }
        }

        directory.delete();
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
