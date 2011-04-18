/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.pack;

import java.util.Arrays;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import org.dspace.curate.Utils;

// Warning - static import ahead!
import static javax.xml.stream.XMLStreamConstants.*;

/**
 * Bag represents a rudimentary bag conformant to LC Bagit spec - version 0.96.
 * A Bag is a directory with contents and a little structured metadata in it.
 * Although they don't have to be, these bags are 'wormy' - meaning that they
 * can be written only once (have no update semantics), and can be 'holey' -
 * meaning they can contain content by reference as well as inclusion. The
 * implementation provides 3 symmetrical interfaces for reading from & writing
 * to the bag: (1) A 'flat' reader and writer for line-oriented character
 * data, (2) an 'xml' reader and writer for XML documents, and (3) a 'raw'
 * stream-based reader and writer for uninterpreted binary data.
 * 
 * The bag also can serialize itself to a compressed archive file (supported
 * formats zip or tgz) or be deserialized from same or a stream, 
 * abiding by the serialization recommendations of the specification.
 * 
 * @author richardrodgers
 */
public class Bag {
    // coding constants
    private static final String ENCODING = "UTF-8";
    private static final String CS_ALGO = "MD5";
    private static final String BAGIT_VSN = "0.96";
    private static final String DFLT_FMT = "zip";
    // mandated file names
    private static final String MANIF_FILE = "manifest-" + CS_ALGO.toLowerCase() + ".txt";
    private static final String TAGMANIF_FILE = "tag" + MANIF_FILE;
    private static final String DECL_FILE = "bagit.txt";
    private static final String REF_FILE = "fetch.txt";

    private XMLInputFactory inFactory = XMLInputFactory.newInstance();
    private XMLOutputFactory outFactory = XMLOutputFactory.newInstance();

    private FlatWriter tagWriter = null;
    private FlatWriter manWriter = null;
    private FlatWriter refWriter = null;

    // directory root of bag
    private File baseDir = null;
    // have all content and tag files been written?
    private boolean filled = false;

    /**
     * Constructor - creates a new bag. There are 3 distinct modes
     * of creation, depending on passed base file:
     * (1) file does not have a 'data' subdirectory - create new unfilled bag.
     * (2) file exists, is a directory with a 'data' subdirectory - assumed
     *     to be an existing Bag structure. Create as filled.
     * (3) file exists and is not a directory - assume it to be a compressed
     *     archive of a bag. Explode the archive and create a filled bag in
     *     the same directory as the archive file.
     * 
     * @param baseFile
     * @throws IOException
     */
    public Bag(File baseFile) throws IOException
    {
        // is it an archive file? If so, inflate into bag
        String baseName = baseFile.getName();
        int sfxIdx = baseName.lastIndexOf(".");
        String suffix = (sfxIdx != -1) ? baseName.substring(sfxIdx + 1) : null;
        if (baseFile.exists() && ! baseFile.isDirectory()
            && suffix != null && suffix.equals(DFLT_FMT))
        {
            String dirName = baseName.substring(0, sfxIdx);
            baseDir = new File(baseFile.getParent(), dirName);
            File dFile = bagFile("data");
            dFile.mkdirs();
            inflate();
        }
        else
        {
            // pregenerate data directory if creating
            baseDir = baseFile;
            File dFile = bagFile("data");
            if (dFile.exists())
            {
                filled = true;
            }
            else
            {
                dFile.mkdirs();
                // prepare manifest writers
                tagWriter = new FlatWriter(bagFile(TAGMANIF_FILE), null, null);
                manWriter = new FlatWriter(bagFile(MANIF_FILE), null, tagWriter);
            }
        } 
    }

    public static String getVersion()
    {
        return BAGIT_VSN;
    }

    public String getName()
    {
        return baseDir.getName();
    }

    public boolean isFilled()
    {
        return filled;
    }

    public FlatReader flatReader(String name) throws IOException
    {
        File flatFile = dataFile(name);
        return flatFile.exists() ? new FlatReader(flatFile) : null;
    }

    public XmlReader xmlReader(String name) throws IOException
    {
        File xmlFile = dataFile(name);
        return xmlFile.exists() ? new XmlReader(xmlFile) : null;
    }

    public FlatWriter flatWriter(String name) throws IOException
    {
        if (filled)
        {
            throw new IllegalStateException("Cannot write to filled bag");
        }
        String brPath = "data/" + name;
        return new FlatWriter(dataFile(name), brPath, manWriter);
    }

    public XmlWriter xmlWriter(String name) throws IOException
    {
        if (filled)
        {
            throw new IllegalStateException("Cannot write to filled bag");
        }
        String brPath = "data/" + name;
        return new XmlWriter(dataFile(name), brPath, manWriter);
    }

    public InputStream dataStream(String name) throws IOException
    {
        File dFile = dataFile(name);
        return dFile.exists() ? new FileInputStream(dFile) : null;
    }

    public void addData(String relPath, long size, InputStream is) throws IOException
    {
        if (filled)
        {
            throw new IllegalStateException("Cannot add data to filled bag");
        }
        // wrap stream in digest stream
        DigestInputStream dis = null;
        try
        {
            dis = new DigestInputStream(is, MessageDigest.getInstance(CS_ALGO));
            FileOutputStream fos = new FileOutputStream(dataFile(relPath));
            // attempt to optimize copy in various ways - TODO
            Utils.copy(dis, fos);
            fos.close();
            is.close();
        }
        catch (NoSuchAlgorithmException nsaE)
        {
            throw new IOException("no algorithm: " + CS_ALGO);
        }
        // record checksum
        String brPath = "data/" + relPath;
        manWriter.writeProperty(Utils.toHex(dis.getMessageDigest().digest()), brPath);
    }

    public void addDataRef(String relPath, long size, String url) throws IOException
    {
        if (refWriter == null)
        {
            refWriter = new FlatWriter(bagFile(REF_FILE), null, tagWriter);
        }
        String brPath = "data/" + relPath;
        refWriter.writeLine(url + " " + size + " " + brPath);
    }
    
    public List<File> listDataFiles() throws IOException
    {
       return Arrays.asList(bagFile("data").listFiles());
    }

    public List<String> getDataRefs() throws IOException
    {
        List<String> refList = new ArrayList<String>();
        File refFile = bagFile(REF_FILE);
        if (refFile.exists())
        {
            FlatReader reader = new FlatReader(refFile);
            String line = null;
            while ((line = reader.readLine()) != null)
            {
                refList.add(line);
            }
        }
        return refList;
    }

    public void close() throws IOException
    {
        if (! filled)
        {
            // close the manifest file
            manWriter.close();
            // close ref file if present
            if (refWriter != null) {
                refWriter.close();
            }
            // write out bagit declaration file
            FlatWriter fwriter = new FlatWriter(bagFile(DECL_FILE), null, tagWriter);
            fwriter.writeLine("BagIt-Version: " + BAGIT_VSN);
            fwriter.writeLine("Tag-File-Character-Encoding: " + ENCODING);
            fwriter.close();
            // close tag manifest file of previous tag files
            tagWriter.close();
            filled = true;
        }
    }

    public void empty()
    {
        // just delete everything
        deleteDir(baseDir);
        baseDir.delete();
        filled = false;
    }

    private void deleteDir(File dirFile)
    {
       for (File file : dirFile.listFiles())
       {
           if (file.isDirectory())
           {
               deleteDir(file);
           }
           file.delete();
       }
    }
    
    public File deflate() throws IOException
    {
        // deflate this bag inplace (in current directory) using default archive format
        return deflate(baseDir.getParent(), DFLT_FMT);
    }
    
    public File deflate(String fmt) throws IOException
    {
        // deflate this bag inplace (in current directory) using given archive format
        return deflate(baseDir.getParent(), fmt);        
    }
    
    public File deflate(String destDir, String fmt) throws IOException
    {
        File defFile = new File(destDir, baseDir.getName() + "." + fmt);
        deflate(new FileOutputStream(defFile), fmt);
        return defFile;
    }
    
    public void deflate(OutputStream out, String fmt) throws IOException
    {
        if (! filled)
        {
            throw new IllegalStateException("Cannot deflate unfilled bag");
        }
        if ("zip".equals(fmt))
        {
            ZipOutputStream zout = new ZipOutputStream(
                                   new BufferedOutputStream(out));
            fillZip(baseDir, baseDir.getName(), zout);
            zout.close(); 
        }
        else if ("tgz".equals(fmt))
        {
            TarArchiveOutputStream tout = new TarArchiveOutputStream(
                                          new BufferedOutputStream(
                                          new GzipCompressorOutputStream(out)));
            fillArchive(baseDir, baseDir.getName(), tout);
            tout.close(); 
        }
    }
    
    public final void inflate() throws IOException
    {
        // assume current directory & default format
        inflate(baseDir.getParent() + File.separator + baseDir.getName() + "." + DFLT_FMT);
    }
    
    public void inflate(String archFile) throws IOException
    {
        String fmt = archFile.substring(archFile.lastIndexOf(".") + 1);
        InputStream in = new FileInputStream(new File(archFile));
        inflate(in, fmt);
    }
    
    public void inflate(InputStream in, String fmt) throws IOException
    {
        if (filled)
        {
            throw new IllegalStateException("Cannot inflate filled bag");
        }
        if ("zip".equals(fmt))
        {
            ZipInputStream zin = new ZipInputStream(in);
            ZipEntry entry = null;
            while((entry = zin.getNextEntry()) != null)
            {
                File outFile = new File(baseDir.getParent(), entry.getName());
                outFile.getParentFile().mkdirs();
                FileOutputStream fout = new FileOutputStream(outFile);
                Utils.copy(zin, fout);
                fout.close();
            }
            zin.close();
        }
        else if ("tgz".equals(fmt))
        {
            TarArchiveInputStream tin = new TarArchiveInputStream(
                                        new GzipCompressorInputStream(in));
            TarArchiveEntry entry = null;
            while((entry = tin.getNextTarEntry()) != null)
            {
                File outFile = new File(baseDir.getParent(), entry.getName());
                outFile.getParentFile().mkdirs();
                FileOutputStream fout = new FileOutputStream(outFile);
                Utils.copy(tin, fout);
                fout.close();
            }
            tin.close();                                 
        }
        filled = true;
    }
    
    private void fillArchive(File dirFile, String relBase, ArchiveOutputStream out) throws IOException
    {
        for (File file : dirFile.listFiles())
        {
            String relPath = relBase + File.separator + file.getName();
            if (file.isDirectory())
            {
                fillArchive(file, relPath, out);
            }
            else
            {
                TarArchiveEntry entry = new TarArchiveEntry(relPath);
                entry.setSize(file.length());
                entry.setModTime(0L);
                out.putArchiveEntry(entry);
                FileInputStream fin = new FileInputStream(file);
                Utils.copy(fin, out);
                out.closeArchiveEntry();
                fin.close();
            }
        }
    }

    private void fillZip(File dirFile, String relBase, ZipOutputStream zout) throws IOException
    {
        for (File file : dirFile.listFiles())
        {
            String relPath = relBase + File.separator + file.getName();
            if (file.isDirectory())
            {
                fillZip(file, relPath, zout);
            }
            else
            {
                ZipEntry entry = new ZipEntry(relPath);
                entry.setTime(0L);
                zout.putNextEntry(entry);
                FileInputStream fin = new FileInputStream(file);
                Utils.copy(fin, zout);
                zout.closeEntry();
                fin.close();
            }
        }
    }

    private File dataFile(String name)
    {
        // all user-defined files live in payload area - ie. under 'data'
        File dataFile = new File(bagFile("data"), name);
        // create needed dirs
        File parentFile = dataFile.getParentFile();
        if (! parentFile.isDirectory())
        {
            parentFile.mkdirs();
        }
        return dataFile;
    }

    private File bagFile(String name)
    {
        return new File(baseDir, name);
    }

    // Assortment of small helper classes for reading & writing bag files
    // Writers capture the checksums of written files, needed for bag manifests

    public class FlatReader
    {
        private BufferedReader reader = null;

        private FlatReader(File file) throws IOException
        {
            reader = new BufferedReader(new FileReader(file));
        }

        public String readLine() throws IOException
        {
            return reader.readLine();
        }

        public void close() throws IOException
        {
            reader.close();
        }
    }

    // wrapper for simple Stax-based XML reader
    public class XmlReader
    {
        private XMLStreamReader reader = null;

        private XmlReader(File file) throws IOException
        {
            try
            {
                reader = inFactory.createXMLStreamReader(new FileInputStream(file),
                                                         ENCODING);
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
        }

        public boolean findStanza(String name) throws IOException
        {
            try
            {
                while(reader.hasNext())
                {
                    if (reader.next() == START_ELEMENT &&
                        reader.getLocalName().equals(name))
                    {
                        return true;
                    }
                }
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
            return false;
        }

        public Value nextValue() throws IOException
        {
            Value value = null;
            try
            {
                while(reader.hasNext())
                {
                    switch (reader.next())
                    {
                        case START_ELEMENT:
                            value = new Value();
                            int numAttrs = reader.getAttributeCount();
                            for (int idx = 0; idx < numAttrs; idx++)
                            {
                                value.addAttr(reader.getAttributeLocalName(idx),
                                              reader.getAttributeValue(idx));
                            }
                            break;
                        case ATTRIBUTE:
                            break;
                        case CHARACTERS:
                            value.val = reader.getText();
                            break;
                        case END_ELEMENT:
                            return value;
                        default:
                            break;
                    }
                }
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
            return value;
        }

        public void close() throws IOException
        {
            try
            {
                reader.close();
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
        }
    }

    public class FlatWriter
    {
        private String brPath = null;
        private OutputStream out = null;
        private DigestOutputStream dout = null;
        private FlatWriter tailWriter = null;

        private FlatWriter(File file, String brPath, FlatWriter tailWriter) throws IOException
        {
            try
            {
                out = new FileOutputStream(file);
                dout = new DigestOutputStream(out,
                                           MessageDigest.getInstance(CS_ALGO));
                this.brPath = (brPath != null) ? brPath : file.getName();
                this.tailWriter = tailWriter;
            }
            catch (NoSuchAlgorithmException nsae)
            {
                throw new IOException("no such algorithm: " + CS_ALGO);
            }
        }

        public void writeProperty(String key, String value) throws IOException
        {
            writeLine(key + " " + value);
        }

        public void writeLine(String line) throws IOException
        {
            byte[] bytes = (line + "\n").getBytes(ENCODING);
            dout.write(bytes);
        }

        public void close() throws IOException
        {
            dout.flush();
            out.close();
            if (tailWriter != null)
            {
                tailWriter.writeProperty(
                       Utils.toHex(dout.getMessageDigest().digest()), brPath);
            }
        }
    }

    // Wrapper for simple Stax-based writer
    public class XmlWriter
    {
        private String brPath = null;
        private OutputStream out = null;
        private DigestOutputStream dout = null;
        private XMLStreamWriter writer = null;
        private FlatWriter tailWriter = null;

        private XmlWriter(File file, String brPath, FlatWriter tailWriter) throws IOException
        {
            try
            {
                out = new FileOutputStream(file);
                dout = new DigestOutputStream(out,
                                    MessageDigest.getInstance(CS_ALGO));
                writer = outFactory.createXMLStreamWriter(dout, ENCODING);
                writer.writeStartDocument(ENCODING, "1.0");
                this.brPath = (brPath != null) ? brPath : file.getName();
                this.tailWriter = tailWriter;
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
            catch (NoSuchAlgorithmException nsaE)
            {
                throw new IOException("no such algorithm: " + CS_ALGO);
            }
        }

        public void startStanza(String name) throws IOException
        {
            try
            {
                writer.writeStartElement(name);
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
        }

        public void endStanza() throws IOException
        {
            try
            {
                writer.writeEndElement();
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
        }

        public void writeValue(String name, String val) throws IOException
        {
            if (name != null && val != null)
            {
                try
                {
                    writer.writeStartElement("value");
                    writer.writeAttribute("name", name);
                    writer.writeCharacters(val);
                    writer.writeEndElement();
                }
                catch (XMLStreamException xsE)
                {
                    throw new IOException(xsE.getMessage(), xsE);
                }
            }
        }

        public void writeValue(Value value) throws IOException
        {
            try
            {
                writer.writeStartElement("value");
                for (String attrName : value.attrs.keySet())
                {
                    String attrVal = value.attrs.get(attrName);
                    if (attrVal != null)
                    {
                        writer.writeAttribute(attrName, attrVal);
                    }
                }
                writer.writeCharacters(value.val);
                writer.writeEndElement();
            }
            catch (XMLStreamException xsE)
            {
                throw new IOException(xsE.getMessage(), xsE);
            }
        }

        public void close() throws IOException
        {
            try
            {
                writer.writeEndDocument();
                writer.flush();
                writer.close();
                out.close();
                if (tailWriter != null)
                {
                    tailWriter.writeProperty(
                            Utils.toHex(dout.getMessageDigest().digest()), brPath);
                }
           }
           catch (XMLStreamException xsE)
           {
                throw new IOException(xsE.getMessage(), xsE);
           }
        }
    }

    public static class Value
    {
        public String name = null;
        public String val = null;
        public Map<String, String> attrs = null;

        public void addAttr(String name, String val)
        {
            if ("name".equals(name))
            {
                this.name = val;
            }
            else
            {
                if (attrs == null)
                {
                    attrs = new HashMap<String, String>();
                }
                attrs.put(name, val);
            }
        }
    }
}
