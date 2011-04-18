/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.pack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.dspace.pack.PackerFactory.*;

/**
 * CatalogPacker packs and unpacks Object catalogs in Bagit format. These
 * catalogs are typically used as deletion 'receipts' - i.e. records of what
 * was deleted.
 *
 * @author richardrodgers
 */
public class CatalogPacker implements Packer
{
    private String objectId = null;
    private String ownerId = null;
    private List<String> members = null;

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
    public File pack(File packDir) throws IOException
    {
        Bag bag = new Bag(packDir);
        // set base object properties
        Bag.FlatWriter fwriter = bag.flatWriter(OBJFILE);
        fwriter.writeProperty(BAG_TYPE, "MAN");
        fwriter.writeProperty(OBJECT_TYPE, "deletion");
        fwriter.writeProperty(OBJECT_ID, objectId);
        if (ownerId != null)
        {
            fwriter.writeProperty(OWNER_ID, ownerId);
        }
        fwriter.writeProperty(CREATE_TS,
                              String.valueOf(System.currentTimeMillis()));
        fwriter.close();
        // just serialize member list if non-empty
        if (members.size() > 0)
        {
            fwriter = bag.flatWriter("members");
            for (String member : members)
            {
                fwriter.writeLine(member);
            }
            fwriter.close();
        }
        bag.close();
        File archive = bag.deflate();
        // clean up undeflated bag
        bag.empty();
        return archive;
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
        Properties props = new Properties();
        props.load(bag.dataStream(OBJFILE));
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
