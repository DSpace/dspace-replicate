package org.dspace.pack.bagit.xml.roles;

import java.sql.SQLException;
import javax.xml.bind.annotation.XmlAttribute;

import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageUtils;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

public class Member {

    private String id;
    private String name;

    protected Member() {
    }

    public Member(final EPerson ePerson) {
        this.id = ePerson.getID().toString();
        this.name = ePerson.getName();
    }

    public Member(final Group group) throws SQLException, PackageException {
        this.id = group.getID().toString();
        if (Group.ADMIN.equalsIgnoreCase(group.getName()) || Group.ANONYMOUS.equalsIgnoreCase(group.getName())) {
            this.name = group.getName();
        } else {
            this.name = PackageUtils.translateGroupNameForExport(Curator.curationContext(), group.getName());
        }
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
