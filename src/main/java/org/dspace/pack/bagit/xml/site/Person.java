package org.dspace.pack.bagit.xml.site;

import java.util.Collections;
import java.util.Map;

import org.dspace.pack.bagit.xml.Parent;

public class Person extends Parent {

    public static String EMAIL = "Email";
    public static String NET_ID = "Netid";
    public static String FIRST_NAME = "FirstName";
    public static String LAST_NAME = "LastName";
    public static String LANGUAGE = "Language";
    public static String CAN_LOGIN = "CanLogin";
    public static String SELF_REGISTERED = "SelfRegistered";

    private final Long id;

    public Person(final Long id) {
        this.id = id;
    }

    @Override
    public String getLocalName() {
        return null;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Map<String, String> getAttributes() {
        return Collections.singletonMap("ID", String.valueOf(id));
    }
}
