/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;

/**
 * Delete task will simply delete DSOs as would be done in a
 * an administrative UI - thus it is largely a testing convenience task.
 * 
 * @author richardrodgers
 */
@Mutative
public class Delete extends AbstractCurationTask {

    /**
     * Removes passed object from the repository.
     * 
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        
        try {
            if (dso instanceof Collection) {
                Collection coll = (Collection)dso;
                Community parent = coll.getCommunities()[0];
                parent.removeCollection(coll);
            } else if (dso instanceof Community) {
                Community comm = (Community)dso;
                Community parent = comm.getParentCommunity();
                if (parent != null) {
                    parent.removeSubcommunity(comm);
                } else {
                    comm.delete();
                }
            } else if (dso instanceof Item) {
                Item item = (Item)dso;
                Collection owner = item.getOwningCollection();
                owner.removeItem(item);
            }
        } catch (AuthorizeException authE) {
            // consider this a task failure
            return Curator.CURATE_FAIL;
        } catch (SQLException sqlE) {
            throw new IOException(sqlE);
        }
        return Curator.CURATE_SUCCESS;
    }
}
