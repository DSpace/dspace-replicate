/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.metadata;

import org.dspace.pack.bagit.xml.Parent;

/**
 * BagIt specific pojo for DSO metadata.xml
 *
 * @author mikejritter
 */
public class Metadata extends Parent {

    @Override
    public String getLocalName() {
        return "metadata";
    }

}
