package org.dspace.pack.bagit.xml.site;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "DSpaceRoles")
public class DSpaceRoles {

    private List<Group> groups;
    private List<Person> people;

    @XmlElement(name = "Group")
    @XmlElementWrapper(name = "Groups")
    public List<Group> getGroups() {
        return groups;
    }

    public void addGroup(final Group group) {
        if (groups == null) {
            groups = new ArrayList<>();
        }

        groups.add(group);
    }

    public void setGroups(final List<Group> groups) {
        this.groups = groups;
    }

    @XmlElement(name = "Person")
    @XmlElementWrapper(name = "People")
    public List<Person> getPeople() {
        return people;
    }

    public void addPerson(final Person person) {
        if (people == null) {
            people = new ArrayList<>();
        }

        people.add(person);
    }

    public void setPeople(final List<Person> people) {
        this.people = people;
    }
}
