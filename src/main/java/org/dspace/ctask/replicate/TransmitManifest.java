/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Arrays;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Utils;

/**
 * TransmitManifest task produces a manifest file for the content files contained
 * in the passed DSpace object, and forwards it to the replication system for
 * transmission (upload). If the DSpace Object is a container,
 * the task produces a multi-level set of manifests representing the container.
 * The manifests produced conform to the CDL Checkm v0.7 manifest format spec.
 * 
 * @author richardrodgers
 */
@Distributive
public class TransmitManifest extends AbstractCurationTask {

    private static final String CKM_VSN = "0.7";

    private ReplicaManager repMan = ReplicaManager.instance();
    private static String template = null;
    
    static {
        template = ConfigurationManager.getProperty("replicate", "checkm.template");
    }

    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        try
        {
            File manFile = null;
            int type = dso.getType();
            if (Constants.ITEM == type)
            {
                manFile = itemManifest((Item)dso);
            }
            else if (Constants.COLLECTION == type)
            {
                // create manifests for each item - link in collection manifest
                manFile = collectionManifest((Collection)dso);
            }
            else if (Constants.COMMUNITY == type)
            {
                // create manifests on down
                manFile = communityManifest((Community)dso);
            }
            repMan.transferObject("manifests", manFile);
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }
        setResult("Created manifest for: " + dso.getHandle());
        return Curator.CURATE_SUCCESS;
    }

    private File communityManifest(Community comm) throws IOException, SQLException
    {
        File manFile = repMan.stage("manifests", comm.getHandle());
        Writer writer = manifestWriter(manFile);
        int count = 0;
        for (Community subComm : comm.getSubcommunities())
        {
            File scFile = communityManifest(subComm);
            writer.write(tokenized(scFile) + "\n");
            count++;
            repMan.transferObject("manifests", scFile); 
        }
        for (Collection coll: comm.getCollections())
        {
            File colFile = collectionManifest(coll);
            writer.write(tokenized(colFile) + "\n");
            count++;
            repMan.transferObject("manifests", colFile);
        }
        if (count == 0)
        {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        writer.close();
        return manFile;
    }

    private File collectionManifest(Collection coll) throws IOException, SQLException
    {
        File manFile = repMan.stage("manifests", coll.getHandle());
        Writer writer = manifestWriter(manFile);
        int count = 0;
        ItemIterator ii = coll.getItems();
        while (ii.hasNext())
        {
            File itemMan = itemManifest(ii.next());
            count++;
            writer.write(tokenized(itemMan) + "\n");
            repMan.transferObject("manifests", itemMan);
        }
        if (count == 0)
        {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        writer.close();
        return manFile;
    }

    private File itemManifest(Item item) throws IOException, SQLException
    {
        File manFile = repMan.stage("manifests", item.getHandle());
        Writer writer = manifestWriter(manFile);
        // look through all ORIGINAL bitstreams, comparing
        // stored to recalculated checksums - report disagreements
        Bundle bundle = item.getBundles("ORIGINAL")[0];
        for (Bitstream bs : bundle.getBitstreams())
        {
            int i = 0;
            StringBuilder sb = new StringBuilder();
            for (String token : Arrays.asList(template.split("\\|")))
            {
                if (! token.startsWith("x"))
                {
                    // tokens are positionally defined
                    switch (i)
                    {
                        case 0:
                            // what URL/name format?
                            sb.append(item.getHandle()).append("/").append(bs.getSequenceID());
                            break;
                        case 1:
                            // Checksum algorithm
                            sb.append(bs.getChecksumAlgorithm().toLowerCase());
                            break;
                        case 2:
                            // Checksum
                            sb.append(bs.getChecksum());
                            break;
                        case 3:
                            // length
                            sb.append(bs.getSize());
                            break;
                        case 4:
                            // modified - use item level data?
                            sb.append(item.getLastModified());
                            break;
                        case 5:
                             // target name - skip for now
                        default:
                             break;
                    }
                }
                sb.append("|");
                i++;
            }
            writer.write(sb.substring(0, sb.length() - 1) + "\n");
        }
        writer.close();
        return manFile;
    }

    private Writer manifestWriter(File file) throws IOException
    {
        FileWriter writer = new FileWriter(file);
        writer.write("#%checkm_" + CKM_VSN + "\n");
        // write out template as explanatory metadata
        writer.write("# " + template + "\n");
        return writer;
    }

    private String tokenized(File file) throws IOException
    {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (String token : Arrays.asList(template.split("\\|")))
        {
            if (! token.startsWith("x"))
            {
                // tokens are positionally defined
                switch (i) {
                    case 0:
                        // what URL/name format?
                        sb.append(file.getName());
                        break;
                    case 1:
                        // Checksum algorithm
                        sb.append("md5");
                        break;
                    case 2:
                        // Checksum
                        sb.append(Utils.checksum(file, "md5"));
                        break;
                    case 3:
                        // length
                        sb.append(file.length());
                        break;
                    case 4:
                        // modified - use item level data?
                        sb.append(file.lastModified());
                        break;
                    case 5:
                         // target name - skip for now
                    default:
                         break;
                }
            }
            sb.append("|");
            i++;
        }
        return sb.substring(0, sb.length() - 1);
    }
}
