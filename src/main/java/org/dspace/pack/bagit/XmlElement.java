/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.util.Map;

/**
 * Encapsulate information for metadata values which are written to xml. Allows for a body to be stored along with any
 * number of attributes for said body.
 *
 * @author mikejritter
 * @since 2020-03-12
 */
public class XmlElement {

    private final String body;
    private final Map<String, String> attributes;

    /**
     * Default constructor. Takes a string value for the body and a map of attributes.
     *
     * @param body the body of the xml element
     * @param attributes the attributes for the xml element
     */
    public XmlElement(String body, Map<String, String> attributes) {
        this.body = body;
        this.attributes = attributes;
    }

    /**
     * @return the body
     */
    public String getBody() {
        return body;
    }

    /**
     * @return the attributes
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }
}
