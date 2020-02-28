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
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.dspace.core.Utils;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.BagSerializer;
import org.duraspace.bagit.BagWriter;
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
    private final String bagProfile = "/profiles/beyondtherepository.json";

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
    public File pack(File packDir) throws IOException {
        final MessageDigest messageDigest;
        final URL url = this.getClass().getResource(bagProfile);
        final BagProfile profile = new BagProfile(url.openStream());

        final Path dataDir = packDir.toPath().resolve("data");
        final HashMap<File, String> checksums = new HashMap<>();

        // todo - on bag init add: tag files, bag metadata, track size written
        BagWriter bag = new BagWriter(packDir, Collections.singleton("md5"));
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // should never happen with known algs
            throw new IOException(e.getMessage(), e);
        }

        final Path objfile = dataDir.resolve(PackerFactory.OBJFILE);
        try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream objDigest = new DigestOutputStream(objOS, messageDigest)) {

            objDigest.write((BAG_TYPE + "  " + "MAN\n").getBytes());
            objDigest.write((OBJECT_TYPE + "  " + "deletion\n").getBytes());
            objDigest.write((OBJECT_ID + "  " + objectId + "\n").getBytes());

            if (ownerId != null) {
                objDigest.write((OWNER_ID + "  " + ownerId + "\n").getBytes());
            }

            objDigest.write((CREATE_TS + "  " + System.currentTimeMillis() + "\n").getBytes());
        }
        final String objDigest = Utils.toHex(messageDigest.digest());
        checksums.put(objfile.toFile(), objDigest);

        messageDigest.reset();

        if (members.size() > 0) {
            final Path membersFile = dataDir.resolve("members");
            try (final OutputStream os = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
                 final DigestOutputStream membersOs = new DigestOutputStream(os, messageDigest)) {
                for (String member : members) {
                    membersOs.write((member + "\n").getBytes());
                }
            }
            final String memberDigest = Utils.toHex(messageDigest.digest());
            checksums.put(membersFile.toFile(), memberDigest);
        }

        try {
            bag.registerChecksums("md5", checksums);
            bag.write();

            BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
            // todo: clean up bag remnants
            return serializer.serialize(packDir.toPath()).toFile();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage(), e);
        }
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
