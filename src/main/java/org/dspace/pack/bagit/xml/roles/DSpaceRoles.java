/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import java.util.HashSet;
import java.util.Set;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;

/**
 * Top level object for the DSpaceRoles xml schema mapping
 *
 * @author mikejritter
 */
@XmlRootElement(name = "DSpaceRoles")
public class DSpaceRoles {

    private Set<AssociatedGroup> groups;
    private Set<Person> people;

    /**
     * @return the {@link AssociatedGroup}s which exist
     */
    @XmlElement(name = "Group")
    @XmlElementWrapper(name = "Groups")
    public Set<AssociatedGroup> getGroups() {
        return groups;
    }

    /**
     * Add a single {@link AssociatedGroup}
     *
     * @param group the {@link AssociatedGroup} to add
     */
    public void addGroup(final AssociatedGroup group) {
        if (groups == null) {
            groups = new HashSet<>();
        }

        groups.add(group);
    }

    /**
     * @return the {@link Person}s which exist
     */
    @XmlElement(name = "Person")
    @XmlElementWrapper(name = "People")
    public Set<Person> getPeople() {
        return people;
    }

    /**
     * Add a single {@link Person}
     *
     * @param person the {@link Person} to add
     */
    public void addPerson(final Person person) {
        if (people == null) {
            people = new HashSet<>();
        }

        people.add(person);
    }

}
