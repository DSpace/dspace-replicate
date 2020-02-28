/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.pack.PackerFactory.OTHER_IDS;
import static org.dspace.pack.PackerFactory.OWNER_ID;
import static org.dspace.pack.PackerFactory.WITHDRAWN;

import java.io.File;
import java.io.FileFilter;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.Charsets;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Utils;
import org.dspace.curate.Curator;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.duraspace.bagit.BagProfile;
import org.duraspace.bagit.BagSerializer;
import org.duraspace.bagit.BagWriter;
import org.duraspace.bagit.SerializationSupport;

/**
 * ItemPacker packs and unpacks Item AIPs in BagIt bag compressed archives
 *
 * @author richardrodgers
 */
public class ItemPacker implements Packer
{
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();

    private Item item = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<>();
    private final String bagProfile = "/profiles/beyondtherepository.json";

    public ItemPacker(Item item, String archFmt)
    {
        this.item = item;
        this.archFmt = archFmt;
    }

    public Item getItem()
    {
        return item;
    }

    public void setItem(Item item)
    {
        this.item = item;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException
    {
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

        // set base object properties
        // todo: capture digest... in a better way
        final Path objfile = dataDir.resolve(PackerFactory.OBJFILE);
        try (final OutputStream objOS = Files.newOutputStream(objfile, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream objDigest = new DigestOutputStream(objOS, messageDigest)) {

            objDigest.write((PackerFactory.BAG_TYPE + "  " + "AIP\n").getBytes());
            objDigest.write((PackerFactory.OBJECT_TYPE + "  " + "item\n").getBytes());
            objDigest.write((PackerFactory.OBJECT_ID + "  " + item.getHandle() + "\n").getBytes());

            StringBuilder linked = new StringBuilder();
            for (Collection coll : item.getCollections()) {
                if (itemService.isOwningCollection(item, coll)) {
                    objDigest.write((OWNER_ID + "  " + coll.getHandle() + "\n").getBytes());
                } else {
                    linked.append(coll.getHandle()).append(",");
                }
            }
            if (linked.length() > 0) {
                // todo: why substring?? is this not just printing the entire string...
                objDigest.write((OTHER_IDS + "  " + linked.substring(0, linked.length() - 1) + "\n").getBytes());
            }
            if (item.isWithdrawn()) {
                objDigest.write((WITHDRAWN + "  true\n").getBytes());
            }
        }

        final String objFileDigest = Utils.toHex(messageDigest.digest());
        checksums.put(objfile.toFile(), objFileDigest);

        final Path userXml = dataDir.resolve("metadata.xml");
        final String xmlDigest = writeItemMetadata(userXml, messageDigest);
        checksums.put(userXml.toFile(), xmlDigest);

        // proceed to bundles, in sub-directories, filtering
        for (Bundle bundle : item.getBundles()) {
            if (accept(bundle.getName())) {
                // only bundle metadata is the primary bitstream - remember it
                // and place in bitstream metadata if defined
                for (Bitstream bs : bundle.getBitstreams()) {
                    // write metadata to xml file
                    final String seqId = String.valueOf(bs.getSequenceID());
                    final String relPath = bundle.getName() + "/";
                    final Path bitstreamXml = dataDir.resolve(relPath + seqId + "-metadata.xml");

                    // field access is hard-coded in Bitstream class, ugh!
                    HashMap<String, String> valueMap = new HashMap<>();
                    valueMap.put("name", bs.getName());
                    valueMap.put("source", bs.getSource());
                    valueMap.put("description", bs.getDescription());
                    valueMap.put("sequence_id", seqId);

                    if (bs.equals(bundle.getPrimaryBitstream())) {
                        valueMap.put("bundle_primary", "true");
                    }
                    final String bsXmlDigest = writeXmlMetadata(valueMap, bitstreamXml, messageDigest);
                    checksums.put(bitstreamXml.toFile(), bsXmlDigest);

                    // write the bitstream itself, unless reference filter applies
                    String fetchUrl = byReference(bundle, bs);
                    if (fetchUrl != null) {
                        // todo: this is a fetch.txt... need to handle writing it
                        // add reference to bag
                        // bag.addDataRef(relPath + seqId, bs.getSize(), url);
                    } else {
                        // add bytes to bag
                        messageDigest.reset();
                        final Path dataFile = dataDir.resolve(relPath + seqId);
                        final InputStream is = bitstreamService.retrieve(Curator.curationContext(), bs);

                        try (OutputStream fout = Files.newOutputStream(dataFile);
                             DigestOutputStream dout = new DigestOutputStream(fout, messageDigest)) {
                            Utils.copy(is, dout);
                        }

                        final String fileChecksum = Utils.toHex(messageDigest.digest());
                        checksums.put(dataFile.toFile(), fileChecksum);
                    }
                }
            }
        }

        bag.registerChecksums("md5", checksums);
        try {
            bag.write();
            BagSerializer serializer = SerializationSupport.serializerFor(archFmt, profile);
            return serializer.serialize(packDir.toPath()).toFile();
        } catch (NoSuchAlgorithmException e) {
            // should never happen...
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException
    {
        if (archive == null || ! archive.exists())
        {
            throw new IOException("Missing archive for item: " + item.getHandle());
        }
        Bag bag = new Bag(archive);
        // add the metadata first
        Bag.XmlReader reader = bag.xmlReader("metadata.xml");
        if (reader != null && reader.findStanza("metadata"))
        {
            Bag.Value value = null;
            while((value = reader.nextValue()) != null)
            {
                itemService.addMetadata(Curator.curationContext(),
                                 item,
                                 value.attrs.get("schema"),
                                 value.attrs.get("element"),
                                 value.attrs.get("qualifier"),
                                 value.attrs.get("language"),
                                 value.val);
            }
            reader.close();
        }
        // proceed to bundle data & metadata
        for (File bfile : bag.listDataFiles())
        {
            // only bundles are directories
            if (! bfile.isDirectory())
            {
                continue;
            }
            Bundle bundle = bundleService.create(Curator.curationContext(), item, bfile.getName());
            for (File file : bfile.listFiles(new FileFilter() {
                            public boolean accept(File file) {
                                return ! file.getName().endsWith(".xml");
                            }
            })) {
                String relPath = bundle.getName() + File.separator + file.getName();
                InputStream in = bag.dataStream(relPath);
                if (in != null)
                {
                    Bitstream bs = bitstreamService.create(Curator.curationContext(), bundle, in);
                    // now set bitstream metadata
                    reader = bag.xmlReader(relPath + "-metadata.xml");
                    if (reader != null && reader.findStanza("metadata"))
                    {
                        Bag.Value value = null;
                        // field access is hard-coded in Bitstream class
                        while((value = reader.nextValue()) != null)
                        {
                            String name = value.name;
                            if ("name".equals(name))
                            {
                                bs.setName(Curator.curationContext(), value.val);
                            }
                            else if ("source".equals(name))
                            {
                                bs.setSource(Curator.curationContext(), value.val);
                            }
                            else if ("description".equals(name))
                            {
                                bs.setDescription(Curator.curationContext(), value.val);
                            }
                            else if ("sequence_id".equals(name))
                            {
                                bs.setSequenceID(Integer.valueOf(value.val));
                            }
                            else if ("bundle_primary".equals(name))
                            {
                                // special case - bundle metadata in bitstream
                                bundle.setPrimaryBitstreamID(bs);
                            }
                        }
                        reader.close();
                    }
                    else
                    {
                        String missing = relPath + "-metadata.xml";
                        throw new IOException("Cannot locate bitstream metadata file: " + missing);
                    }
                    bitstreamService.update(Curator.curationContext(), bs);
                }
                in.close();
            }
        }
        // clean up bag
        bag.empty();
    }

    @Override
    public long size(String method) throws SQLException
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
        //If our filter list of bundles begins with a '+', then this list
        // specifies all the bundles to *include*. Otherwise all 
        // bundles *except* the listed ones are included
        if(filter.startsWith("+"))
        {
            exclude = false;
            //remove the preceding '+' from our bundle list
            filter = filter.substring(1);
        }
        
        filterBundles = Arrays.asList(filter.split(","));
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
        for (RefFilter filter : refFilters)
        {
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

    private void writeXml(final XMLStreamWriter writer, final Map<String, String> attributes,
                          final String body) throws XMLStreamException {
        writer.writeStartElement("value");
        for (String attrName : attributes.keySet()) {
            String attrVal = attributes.get(attrName);
            if (attrVal != null) {
                writer.writeAttribute(attrName, attrVal);
            }
        }
        writer.writeCharacters(body);
        writer.writeEndElement();
    }

    private String writeItemMetadata(final Path manifestXml, final MessageDigest messageDigest) throws IOException {
        messageDigest.reset();
        Files.createDirectories(manifestXml.getParent());
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        try (final OutputStream xmlOut = Files.newOutputStream(manifestXml, StandardOpenOption.CREATE_NEW);
             final DigestOutputStream xmlDigestOut = new DigestOutputStream(xmlOut, messageDigest)) {
            final XMLStreamWriter xmlWriter = xmlOutputFactory.createXMLStreamWriter(xmlDigestOut,
                                                                                     Charsets.UTF_8.toString());
            xmlWriter.writeStartDocument(Charsets.UTF_8.toString(), "1.0");
            xmlWriter.writeStartElement("metadata");

            // Map<String, Map<String, String>>
            // first user metadata
            List<MetadataValue> metadata = itemService.getMetadata(item, Item.ANY, Item.ANY, Item.ANY, Item.ANY);
            for (MetadataValue metadatum : metadata) {
                final HashMap<String, String> values = new HashMap<>();
                values.put("schema", metadatum.getMetadataField().getMetadataSchema().getName());
                values.put("element", metadatum.getMetadataField().getElement());
                values.put("qualifier", metadatum.getMetadataField().getQualifier());
                values.put("language", metadatum.getLanguage());
                writeXml(xmlWriter, values, metadatum.getValue());
            }

            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IOException(e.getMessage(), e);
        }

        return Utils.toHex(messageDigest.digest());
    }

    private String writeXmlMetadata(final Map<String, String> metadata, final Path manifestXml,
                                    final MessageDigest messageDigest) throws IOException {
        messageDigest.reset();
        Files.createDirectories(manifestXml.getParent());
        final XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
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

        return Utils.toHex(messageDigest.digest());
    }

}
