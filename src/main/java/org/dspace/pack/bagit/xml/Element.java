/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml;

import java.util.List;
import java.util.Map;

/**
 * Interface to define common ops which we need in order to serialize pojos as xml
 *
 * @author mikejritter
 */
public interface Element {

    /**
     * The content to write as the body of the xml tag
     *
     * @return the body
     */
    String getBody();

    /**
     * The local name to use for the xml tag
     *
     * @return the local name
     */
    String getLocalName();

    /**
     * Boolean flag indicating if this tag has any child elements
     *
     * @return true if {@link #getChildren()} is empty, false otherwise
     */
    Boolean hasChildren();

    /**
     * Get all child elements of this {@link Element}
     *
     * @return the child elements
     */
    List<Element> getChildren();

    /**
     * Get the attributes to apply to an xml tag
     *
     * @return the xml attributes
     */
    Map<String, String> getAttributes();

}
