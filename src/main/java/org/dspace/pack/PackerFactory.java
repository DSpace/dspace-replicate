/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.pack;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;

/**
 * PackerFactory mints packers for specified object types. Packer implementation
 * is based on a configurable property (packer.pkgtype). Currently, only 
 * LC METS-based ("mets") and Bagit-based ("bagit") package formats are supported.
 *
 * @author richardrodgers
 */
public class PackerFactory
{

    // basic bag property names - some optional
    public static final String OBJFILE = "object.properties";
    public static final String BAG_TYPE = "bagType";
    public static final String OBJECT_TYPE = "objectType";
    public static final String OBJECT_ID = "objectId";
    public static final String OWNER_ID = "ownerId";
    public static final String OTHER_IDS = "otherIds";
    public static final String CREATE_TS = "created";
    public static final String WITHDRAWN  = "withdrawn";
    
    // type of package to use - must be either 'mets' or 'bagit'
    private static String packType = 
            ConfigurationManager.getProperty("replicate", "packer.pkgtype");
    // type of archive format - supported types are 'zip' or 'tgz'
    private static String archFmt = 
            ConfigurationManager.getProperty("replicate", "packer.archfmt");
    // content filter - comma separated list of bundle names
    private static String cfgFilter = 
            ConfigurationManager.getProperty("replicate", "packer.cfilter");
    
    // cached instance of METSPacker - because a little expensive to create
    private static METSPacker metsPacker = null;

    public static Packer instance(DSpaceObject dso)
    {
        Packer packer = null;
        int type = dso.getType();
        if ("mets".equals(packType))
        {
            if (metsPacker == null)
            {
                metsPacker = new METSPacker(dso, archFmt);
            }
            else
            {
                metsPacker.setDSO(dso);
            }
            packer = metsPacker;
        }
        else if (Constants.ITEM == type)
        {
            packer = new ItemPacker((Item)dso, archFmt);
            if (cfgFilter != null)
            {
                packer.setContentFilter(cfgFilter);
            }
        }
        else if (Constants.COLLECTION == type)
        {
            packer = new CollectionPacker((Collection)dso, archFmt);
        }
        else if (Constants.COMMUNITY == type)
        {
            packer = new CommunityPacker((Community)dso, archFmt);
        }
        else 
        {
            throw new RuntimeException("No packer for object type");
        }
        return packer;
    }
}
