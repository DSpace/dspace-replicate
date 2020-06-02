package org.dspace.pack.bagit.xml.site;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class Group {

    private Long id;
    private String name;
    private String type;
    private List<Member> members;
    private List<Member> memberGroups;

    @XmlAttribute(name = "ID")
    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
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
}
