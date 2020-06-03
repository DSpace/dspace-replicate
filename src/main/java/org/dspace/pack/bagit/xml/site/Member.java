package org.dspace.pack.bagit.xml.site;

import javax.xml.bind.annotation.XmlAttribute;

import org.dspace.content.DSpaceObject;

public class Member {

    private String id;
    private String name;

    protected Member() {
    }

    public Member(final DSpaceObject object) {
        this.id = object.getID().toString();
        this.name = object.getName();
    }

    @XmlAttribute(name = "ID")
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    @XmlAttribute(name = "Name")
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
