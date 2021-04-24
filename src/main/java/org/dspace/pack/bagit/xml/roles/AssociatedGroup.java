/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit.xml.roles;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageUtils;
import org.dspace.content.packager.RoleDisseminator;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * The Group tag for roles.xml
 *
 * Named as to not conflict with {@link Group} but still be clear for what it is
 *
 * @author mikejritter
 */
public class AssociatedGroup {

    private String id;
    private String name;
    private String type;
    private List<Member> members;
    private List<Member> memberGroups;

    /**
     * Default constructor for jaxb
     */
    protected AssociatedGroup() {
    }

    /**
     * Constructor to create an {@link AssociatedGroup} from a {@link Group}
     *
     * @param context the context to use
     * @param dso the related DSpaceObject
     * @param group the group to use
     * @throws SQLException if there is an error translating a Group's name
     * @throws PackageException if there is an error translating a Group's name
     */
    public AssociatedGroup(final Context context, final DSpaceObject dso, final Group group) throws SQLException,
            PackageException {
        this.id = group.getID().toString();

        if (Group.ADMIN.equalsIgnoreCase(group.getName()) || Group.ANONYMOUS.equalsIgnoreCase(group.getName())) {
            this.name = group.getName();
        } else {
            this.name = PackageUtils.translateGroupNameForExport(context, group.getName());
        }

        this.type = findGroupType(context, dso, group);

        for (EPerson member : group.getMembers()) {
            addMember(new Member(member));
        }
        for (Group memberGroup : group.getMemberGroups()) {
            addMemberGroup(new Member(context, memberGroup));
        }
    }

    /**
     * Get the group type to use for a given DSpaceObject and group
     * based on org.dspace.content.packager.RoleDisseminator#getGroupType(DSpaceObject, Group)
     *
     * @param context the context to use
     * @param dso the related DSpaceObject
     * @param group the group associated to the DSpaceObject
     * @return the group type string or null
     */
    private String findGroupType(final Context context, final DSpaceObject dso, final Group group) throws SQLException {
        if (dso == null || group == null) {
            return null;
        }

        if (dso.getType() == Constants.COMMUNITY) {
            final Community community = (Community) dso;

            if (group.equals(community.getAdministrators())) {
                return RoleDisseminator.GROUP_TYPE_ADMIN;
            }
        } else if (dso.getType() == Constants.COLLECTION) {
            final Collection collection = (Collection) dso;

            if (group.equals(collection.getAdministrators())) {
                return RoleDisseminator.GROUP_TYPE_ADMIN;
            } else if (group.equals(collection.getSubmitters())) {
                return RoleDisseminator.GROUP_TYPE_SUBMIT;
            } else if (group.equals(collection.getWorkflowStep1(context))) {
                return RoleDisseminator.GROUP_TYPE_WORKFLOW_STEP_1;
            } else if (group.equals(collection.getWorkflowStep2(context))) {
                return RoleDisseminator.GROUP_TYPE_WORKFLOW_STEP_2;
            } else if (group.equals(collection.getWorkflowStep3(context))) {
                return RoleDisseminator.GROUP_TYPE_WORKFLOW_STEP_3;
            }
        }

        return null;
    }

    /**
     * @return the id of the group, the string value of {@link Group#getID()}
     */
    @XmlAttribute(name = "ID")
    public String getId() {
        return id;
    }

    /**
     * @return the name of the group, {@link Group#getName()}
     */
    @XmlAttribute(name = "Name")
    public String getName() {
        return name;
    }

    /**
     * @see #findGroupType(Context, DSpaceObject, Group)
     * @return the group type
     */
    @XmlAttribute(name = "Type")
    public String getType() {
        return type;
    }

    /**
     * The {@link Member}s in the group. When converting to XML a parent group Members is created and each list item is
     * in a single Member tag.
     *
     * @return the members associated with the group {@link Group#getMembers()}
     */
    @XmlElement(name = "Member")
    @XmlElementWrapper(name = "Members")
    public List<Member> getMembers() {
        return members;
    }

    /**
     * Add a single member to the Members
     *
     * @param member the {@link Member} to add
     */
    public void addMember(final Member member) {
        if (members == null) {
            this.members = new ArrayList<>();
        }

        members.add(member);
    }

    /**
     * The groups associated with this {@link AssociatedGroup}. When converting to XML a parent group MemberGroups is
     * created and each list item is in a single MemberGroup tag.
     *
     * @return the members associated with the group {@link Group#getMemberGroups()}
     */
    @XmlElement(name = "MemberGroup")
    @XmlElementWrapper(name = "MemberGroups")
    public List<Member> getMemberGroups() {
        return memberGroups;
    }

    /**
     * Add a single {@link Member} to the {@link AssociatedGroup#memberGroups}
     *
     * @param member the {@link Member} to add
     */
    public void addMemberGroup(final Member member) {
        if (memberGroups == null) {
            memberGroups = new ArrayList<>();
        }
        memberGroups.add(member);
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
