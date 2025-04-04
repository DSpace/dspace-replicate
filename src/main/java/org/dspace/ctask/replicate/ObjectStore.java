/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.File;
import java.io.IOException;

/**
 * An ObjectStore provides access to a managed store containing objects
 * that possess a set of attributes. ObjectStores allow storage within named
 * containers, here called groups.
 * 
 * @author richardrodgers
 */
public interface ObjectStore {

    /**
     * Initializes the storage service for use.
     * @throws IOException if I/O error
     */
    void init() throws IOException;

    /**
     * Returns whether an object with the passed id exists in the store.
     * 
     * @param group Group
     * @param id ID
     * @return true if a representation of the object named by ID exists
     * @throws IOException if I/O error
     */
    boolean objectExists(String group, String id) throws IOException;

    /**
     * Obtains attributes of the representation of the object with passed ID.
     * 
     * @param group Group
     * @param id ID
     * @param attrName - name of the attribute
     * @return value of the attribute if it exists, else null
     * @throws IOException if I/O error
     */
    String objectAttribute(String group, String id, String attrName) throws IOException;

    /**
     * Fetches a copy of the object with passed ID, and places it in passed file.
     * 
     * @param group Group
     * @param id ID
     * @param file File
     * @return number of bytes the replica used
     * @throws IOException if I/O error
     */
    long fetchObject(String group, String id, File file) throws IOException;

    /**
     * Transfers a copy of this file to the object store
     * 
     * @param group Group
     * @param file the file to transfer to store
     * @return number of bytes transferred to store or 0 if transfer failed.
     * @throws IOException if I/O error
     */
    long transferObject(String group, File file) throws IOException;

    /**
     * Removes the passed object from the store.
     * 
     * @param group Group
     * @param id the id of the object to remove
     * @return number of bytes the object was using, or 0 if
     *         object did not exist.
     * @throws IOException if I/O error
     */
    long removeObject(String group, String id) throws IOException;

    /**
     * Moves the passed object from one storage group to another.
     * 
     * @param srcgroup source group
     * @param destGroup destination group
     * @param id the id of the object to move between groups
     * @return number of bytes moved or 0 if move failed.
     * @throws IOException if I/O error
     */
    long moveObject(String srcgroup, String destGroup, String id) throws IOException;
}
