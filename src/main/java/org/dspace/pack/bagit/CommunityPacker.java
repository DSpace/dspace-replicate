/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.io.Charsets;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

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
        List<String> objectProperties = new ArrayList<>();
        objectProperties.add(PackerFactory.BAG_TYPE + "  " + BagItAipWriter.BAG_AIP);
        objectProperties.add(PackerFactory.OBJECT_TYPE + "  " + BagItAipWriter.OBJ_TYPE_COMMUNITY);
        objectProperties.add(PackerFactory.OBJECT_ID + "  " + community.getHandle());

        List<Community> parents = community.getParentCommunities();
        if (parents != null && !parents.isEmpty()) {
            objectProperties.add(PackerFactory.OWNER_ID + "  " + parents.get(0).getHandle());
        }
        Map<String, List<String>> properties = ImmutableMap.of(PackerFactory.OBJFILE, objectProperties);

        // collect the xml metadata
        final List<XmlElement> elements = new ArrayList<>();
        for (String field : fields) {
            final String metadata = communityService.getMetadata(community, field);
            final XmlElement element = new XmlElement(metadata, ImmutableMap.of(BagItAipWriter.XML_NAME_KEY, field));
            elements.add(element);
        }

        BagItAipWriter aipWriter = new BagItAipWriter(packDir, archFmt, logo, properties, elements,
                                                      Collections.<BagBitstream>emptyList());
        return aipWriter.packageAip();
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
