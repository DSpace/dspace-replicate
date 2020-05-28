package org.dspace.pack.bagit.xml.site;

import org.dspace.pack.bagit.xml.Parent;

public class DSpaceRoles extends Parent {

    public static String ID_KEY = "ID";
    public static String NAME_KEY = "Name";

    @Override
    public String getLocalName() {
        return "DSpaceRoles";
    }
}
