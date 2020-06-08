/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import java.sql.SQLException;
import javax.xml.bind.annotation.XmlAttribute;

import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageUtils;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * The Member tag of the DSpaceRoles schema. Can be used with either {@link Group}s or {@link Member}s.
 *
 * @author mikejritter
 */
public class Member {

    private String id;
    private String name;

    /**
     * Default constructor for JAXB
     */
    protected Member() {
    }

    /**
     * Create a member for an {@link EPerson}
     *
     * @param ePerson the {@link EPerson} to use for setting the id and name
     */
    public Member(final EPerson ePerson) {
        this.id = ePerson.getID().toString();
        this.name = ePerson.getName();
    }

    /**
     * Create a Member for a {@link Group}
     *
     * @param group the {@link Group} to use for setting the id and name
     * @throws SQLException if an error occurs translating the {@link Group#getName()} for export
     * @throws PackageException if an error occurs translating the {@link Group#getName()} for export
     */
    public Member(final Group group) throws SQLException, PackageException {
        this.id = group.getID().toString();
        if (Group.ADMIN.equalsIgnoreCase(group.getName()) || Group.ANONYMOUS.equalsIgnoreCase(group.getName())) {
            this.name = group.getName();
        } else {
            this.name = PackageUtils.translateGroupNameForExport(Curator.curationContext(), group.getName());
        }
    }

    /**
     * @return the ID of the Member
     */
    @XmlAttribute(name = "ID")
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(final String id) {
        this.id = id;
    }

    /**
     * @return the name of the Member
     */
    @XmlAttribute(name = "Name")
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(final String name) {
        this.name = name;
    }
}
