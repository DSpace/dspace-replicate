/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Utils;

/**
 * TransmitManifest task produces a manifest file for the content files contained
 * in the passed DSpace object, and forwards it to the replication system for
 * transmission (upload). If the DSpace Object is a container,
 * the task produces a multi-level set of manifests representing the container.
 * <p>
 * The manifests produced conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 */
@Distributive
public class TransmitManifest extends AbstractCurationTask {

    // Version of CDL Checkm spec that this manifest conforms to
    private static final String CKM_VSN = "0.7";

    // Format extension for manifest files
    protected static final String MANIFEST_EXTENSION = "txt";

    private String template = null;

    // Group where all Manifests will be stored
    private String manifestGroupName;

    private static Logger log = LogManager.getLogger();

    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    @Override
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        template = configurationService.getProperty("replicate.checkm.template");
        manifestGroupName = configurationService.getProperty("replicate.group.manifest.name");
    }

    /**
     * Perform 'Transmit Manifest' task
     * <p>
     * Actually generates manifest and transmits to Replica ObjectStore
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException if I/O error
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();
        try {
            Context context = Curator.curationContext();
            File manFile = null;
            int type = dso.getType();
            if (Constants.ITEM == type) {
                manFile = itemManifest(context, repMan, (Item)dso);
            } else if (Constants.COLLECTION == type) {
                // create manifests for each item - link in collection manifest
                manFile = collectionManifest(context, repMan, (Collection)dso);
            } else if (Constants.COMMUNITY == type) {
                // create manifests for Community on down
                manFile = communityManifest(context, repMan, (Community)dso);
            } else if (Constants.SITE == type) {
                // create manifests for all objects in DSpace
                manFile = siteManifest(context, repMan, (Site)dso);
            }

            repMan.transferObject(manifestGroupName, manFile);
        } catch (SQLException sqlE) {
            throw new IOException(sqlE);
        }
        setResult("Created manifest for: " + dso.getHandle());
        return Curator.CURATE_SUCCESS;
    }


    /**
     * Generate a manifest for the DSpace Site. Also
     * generate & transfer to replica ObjectStore the manifests for all
     * objects in DSpace, starting with the top-level Communities.
     *
     * Param context the context to use
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param site the DSpace Site object
     * @return reference to manifest file generated for Community
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    private File siteManifest(Context context, ReplicaManager repMan, Site site) throws IOException, SQLException {
        // Manifests stored as text files
        String filename = repMan.storageId(context, site.getHandle(), MANIFEST_EXTENSION);

        log.debug("Creating manifest for: " + site.getHandle());

        //Create site manifest
        File manFile = repMan.stage(context, manifestGroupName, filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;

        List<Community> topCommunities = communityService.findAllTop(context);
        // Create top-level community manifests & transfer each
        for (Community comm : topCommunities) {
            File scFile = communityManifest(context, repMan, comm);
            writer.write(tokenized(scFile) + "\n");
            count++;
            repMan.transferObject(manifestGroupName, scFile);
        }

        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }

        writer.close();
        report("Created manifest for: " + site.getHandle());
        return manFile;
    }

    /**
     * Generate a manifest for the specified DSpace Community. Also
     * generate & transfer to replica ObjectStore the manifests for any child
     * objects (sub-communities, collections).
     *
     * @param context the context to use
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param comm the DSpace Community
     * @return reference to manifest file generated for Community
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    private File communityManifest(Context context, ReplicaManager repMan, Community comm) throws IOException,
            SQLException {
        // Manifests stored as text files
        String filename = repMan.storageId(context, comm.getHandle(), MANIFEST_EXTENSION);

        log.debug("Creating manifest for: " + comm.getHandle());

        // Create community manifest
        File manFile = repMan.stage(context, manifestGroupName, filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;
        // Create sub-community manifests & transfer each
        for (Community subComm : comm.getSubcommunities()) {
            File scFile = communityManifest(context, repMan, subComm);
            writer.write(tokenized(scFile) + "\n");
            count++;
            repMan.transferObject(manifestGroupName, scFile);
        }

        // Create collection manifests & transfer each
        for (Collection coll: comm.getCollections()) {
            File colFile = collectionManifest(context, repMan, coll);
            writer.write(tokenized(colFile) + "\n");
            count++;
            repMan.transferObject(manifestGroupName, colFile);
        }

        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }

        writer.close();
        report("Created manifest for: " + comm.getHandle());
        return manFile;
    }

    /**
     * Generate a manifest for the specified DSpace Collection. Also
     * generate & transfer to replica ObjectStore the manifests for any child
     * objects (items).
     *
     * @param context the context to use
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param coll the DSpace Collection
     * @return reference to manifest file generated for Collection
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    private File collectionManifest(Context context, ReplicaManager repMan, Collection coll) throws IOException,
            SQLException {
         // Manifests stored as text files
        String filename = repMan.storageId(context, coll.getHandle(), MANIFEST_EXTENSION);

        log.debug("Creating manifest for: " + coll.getHandle());

        //Create Collection manifest
        File manFile = repMan.stage(context, manifestGroupName, filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;

        // Create all Item manifests & transfer each
        Iterator<Item> ii = itemService.findByCollection(context, coll);
        while (ii.hasNext()) {
            File itemMan = itemManifest(context, repMan, ii.next());
            count++;
            writer.write(tokenized(itemMan) + "\n");
            repMan.transferObject(manifestGroupName, itemMan);
        }

        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }

        writer.close();
        report("Created manifest for: " + coll.getHandle());
        return manFile;
    }

    /**
     * Generate a manifest for the specified DSpace Item.
     *
     * @param context the context to use
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param item the DSpace Item
     * @return reference to manifest file generated for Item
     * @throws IOException if I/O error
     * @throws SQLException if database error
     */
    private File itemManifest(Context context, ReplicaManager repMan, Item item) throws IOException, SQLException {
        String filename = repMan.storageId(context, item.getHandle(), MANIFEST_EXTENSION);

        log.debug("Creating manifest for: " + item.getHandle());

        //Create Item manifest
        File manFile = repMan.stage(context, manifestGroupName, filename);
        Writer writer = manifestWriter(manFile);

        // look through all ORIGINAL bitstreams, and add
        // information about each (e.g. checksum) to manifest
        int count = 0;
        List<Bundle> bundles = itemService.getBundles(item, "ORIGINAL");
        if (bundles != null && !bundles.isEmpty()) {
            // there should be only one ORIGINAL bundle
            Bundle bundle = bundles.get(0);
            for (Bitstream bs : bundle.getBitstreams()) {
                int i = 0;
                StringBuilder sb = new StringBuilder();
                for (String token : Arrays.asList(template.split("\\|"))) {
                    if (!token.startsWith("x")) {
                        // tokens are positionally defined
                        switch (i) {
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
                                sb.append(bs.getSizeBytes());
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
                count++;
                writer.write(sb.substring(0, sb.length() - 1) + "\n");
            } // end for each bitstream
        } // end if ORIGINAL bundle

        // If no bitstreams found, then this is an empty manifest
        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }

        writer.close();
        report("Created manifest for: " + item.getHandle());
        return manFile;
    }

    /**
     * Initialize a Writer for a Manifest file. Also, writes header to manifest file.
     * @param file file where manifest will be stored
     * @return reference to Writer
     * @throws IOException if I/O error
     */
    private Writer manifestWriter(File file) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write("#%checkm_" + CKM_VSN + "\n");
        // write out template as explanatory metadata
        writer.write("# " + template + "\n");
        return writer;
    }

    private String tokenized(File file) throws IOException {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (String token : Arrays.asList(template.split("\\|"))) {
            if (!token.startsWith("x")) {
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
