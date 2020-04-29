package org.dspace.pack.bagit.xml;

/**
 * BagIt specific pojo for DSO metadata.xml
 *
 * @author mikejritter
 */
public class Metadata extends Parent {

    private final String LOCAL_NAME = "metadata";

    @Override
    public String getLocalName() {
        return LOCAL_NAME;
    }

}
