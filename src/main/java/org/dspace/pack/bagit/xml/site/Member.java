package org.dspace.pack.bagit.xml.site;

import javax.xml.bind.annotation.XmlAttribute;

public class Member {

    private Long id;
    private String name;

    @XmlAttribute(name = "ID")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @XmlAttribute(name = "Name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
