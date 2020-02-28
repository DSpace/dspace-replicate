/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static java.util.stream.Collectors.toMap;

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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.Charsets;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.duraspace.bagit.BagItDigest;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.BagSerializer;
import org.duraspace.bagit.BagWriter;
import org.duraspace.bagit.SerializationSupport;

/**
 * CommunityPacker Packs and unpacks Community AIPs in Bagit format.
 *
 * @author richardrodgers
 */
public class CommunityPacker implements Packer
{
    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

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
    private final String bagProfile = "/profiles/beyondtherepository.json";

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
        final BagItDigest digest = BagItDigest.MD5;
        final MessageDigest messageDigest = digest.messageDigest();

        final URL url = this.getClass().getResource(bagProfile);
        final BagProfile profile = new BagProfile(url.openStream());

        final Path dataDir = packDir.toPath().resolve("data");
        // todo - on bag init add: tag files, bag metadata, track size written
        final BagWriter bag = new BagWriter(packDir, Collections.singleton(digest.bagitName()));

        final Map<File, String> checksums = new HashMap<>();

        final Path objfile = dataDir.resolve(PackerFactory.OBJFILE);
        try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream objDigest = new DigestOutputStream(objOS, messageDigest)) {

            objDigest.write((PackerFactory.BAG_TYPE + "  " + "AIP\n").getBytes());
            objDigest.write((PackerFactory.OBJECT_TYPE + "  " + "community\n").getBytes());
            objDigest.write((PackerFactory.OBJECT_ID + "  " + community.getHandle() + "\n").getBytes());
            List<Community> parents = community.getParentCommunities();
            if (parents != null && !parents.isEmpty()) {
                objDigest.write((PackerFactory.OWNER_ID + "  " + parents.get(0).getHandle() + "\n").getBytes());
            }
        }
        final String objFileDigest = Utils.toHex(messageDigest.digest());
        checksums.put(objfile.toFile(), objFileDigest);

        // then metadata
        final Path manifestXml = dataDir.resolve("metadata.xml");
        final Map<String, String> metadata =
            Arrays.stream(fields)
                  .collect(toMap(Function.identity(), key -> communityService.getMetadata(community, key)));

        final String xmlDigest = writeXmlMetadata(metadata, manifestXml, messageDigest);
        checksums.put(manifestXml.toFile(), xmlDigest);

        // also add logo if it exists
        Bitstream logo = community.getLogo();
        if (logo != null) {
            final InputStream logoIS = bitstreamService.retrieve(Curator.curationContext(), logo);
            final Path logoPath = dataDir.resolve("logo");
            try (OutputStream os = Files.newOutputStream(logoPath);
                 DigestOutputStream dos = new DigestOutputStream(os, messageDigest)) {
                messageDigest.reset();
                Utils.copy(logoIS, dos);
                checksums.put(logoPath.toFile(), Utils.toHex(messageDigest.digest()));
            }
        }

        bag.registerChecksums(digest.bagitName(), checksums);
        bag.write();
        BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
        return serializer.serialize(packDir.toPath()).toFile();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null)
        {
            throw new IOException("Missing archive for community: " + community.getHandle());
        }
        Bag bag = new Bag(archive);
        // add the metadata
        Bag.XmlReader reader = bag.xmlReader("metadata.xml");
        if (reader != null && reader.findStanza("metadata")) {
            Bag.Value value = null;
            while((value = reader.nextValue()) != null)
            {
                communityService.setMetadata(Curator.curationContext(), community, value.name, value.val);
            }
            reader.close();
        }
        // also install logo or set to null
        communityService.setLogo(Curator.curationContext(), community, bag.dataStream("logo"));
        // now write data back to DB
        communityService.update(Curator.curationContext(), community);
        // clean up bag
        bag.empty();
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

    /**
     * Write the metadata.xml file
     *
     * This is being copied a few times while code is being reorganized. No need to attempt DRY before we know how
     * things will look
     *
     * @param metadata The map of metadata key/value pairs to write
     * @param manifestXml the Path of the metadata.xml file to write
     * @param messageDigest the MessageDigest for tracking the digest of the written stream
     * @return the checksum of the manifest.xml
     * @throws IOException if there's any exception
     */
    public static String writeXmlMetadata(final Map<String, String> metadata, final Path manifestXml,
                                          final MessageDigest messageDigest) throws IOException {
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();

        messageDigest.reset();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(xmlOut, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                final String key = entry.getKey();
                final String value = entry.getValue();
                if (key != null && value != null) {
                    xmlWriter.writeStartElement("value");
                    xmlWriter.writeAttribute("name", key);
                    xmlWriter.writeCharacters(value);
                    xmlWriter.writeEndElement();
                }
            }
            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        final String digest = Utils.toHex(messageDigest.digest());
        messageDigest.reset();

        return digest;
    }

}
