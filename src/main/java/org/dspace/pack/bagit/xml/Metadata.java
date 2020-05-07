package org.dspace.pack.bagit.xml;

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
