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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "DSpaceRoles")
public class DSpaceRoles {

    private Set<AssociatedGroup> groups;
    private Set<Person> people;

    @XmlElement(name = "Group")
    @XmlElementWrapper(name = "Groups")
    public Set<AssociatedGroup> getGroups() {
        return groups;
    }

    public void addGroup(final AssociatedGroup group) {
        if (groups == null) {
            groups = new HashSet<>();
        }

        groups.add(group);
    }

    public void setGroups(final Set<AssociatedGroup> groups) {
        this.groups = groups;
    }

    @XmlElement(name = "Person")
    @XmlElementWrapper(name = "People")
    public Set<Person> getPeople() {
        return people;
    }

    public void addPerson(final Person person) {
        if (people == null) {
            people = new HashSet<>();
        }

        people.add(person);
    }

    public void setPeople(final Set<Person> people) {
        this.people = people;
    }
}
