package org.dspace.pack.bagit.xml.site;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * The Group tag for roles.xml
 *
 * Named as to not conflict with {@link Group} but still be clear for what it is
 *
 */
public class AssociatedGroup {

    private String id;
    private String name;
    private String type;
    private List<Member> members;
    private List<Member> memberGroups;

    protected AssociatedGroup() {
    }

    public AssociatedGroup(Group group) {
        this.id = group.getID().toString();
        this.name = group.getName();
        this.type = String.valueOf(group.getType());

        for (EPerson member : group.getMembers()) {
            addMember(new Member(member));
        }
        for (Group memberGroup : group.getMemberGroups()) {
            addMemberGroup(new Member(memberGroup));
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

    @XmlAttribute(name = "Type")
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    @XmlElement(name = "Member")
    @XmlElementWrapper(name = "Members")
    public List<Member> getMembers() {
        return members;
    }

    public void addMember(final Member member) {
        if (members == null) {
            this.members = new ArrayList<>();
        }

        members.add(member);
    }

    public void setMembers(final List<Member> members) {
        this.members = members;
    }
    @XmlElement(name = "MemberGroup")
    @XmlElementWrapper(name = "MemberGroups")
    public List<Member> getMemberGroups() {
        return memberGroups;
    }

    public void addMemberGroup(final Member member) {
        if (memberGroups == null) {
            memberGroups = new ArrayList<>();
        }
        memberGroups.add(member);
    }

    public void setMemberGroups(final List<Member> memberGroups) {
        this.memberGroups = memberGroups;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AssociatedGroup that = (AssociatedGroup) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
