package org.dspace.pack.bagit.xml;

/**
 * Pojo for policy xml
 *
 * @author mikejritter
 */
public class Policy extends Parent {
    private static final String LOCAL_NAME = "policies";

    @Override
    public String getLocalName() {
        return LOCAL_NAME;
    }

}
