package org.dspace.pack.bagit;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Site;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.site.DSpaceRoles;
import org.dspace.pack.bagit.xml.site.Member;
import org.dspace.pack.bagit.xml.site.Person;

/**
 * Create a {@link org.dspace.pack.bagit.xml.site.DSpaceRoles} for a BagIt bag
 *
 * Uses {@link org.dspace.content.packager.RoleDisseminator} as a reference for obtaining groups/epeople
 *
 * @author mikejritter
 */
public class BagItRolesUtil {

    private final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private final EPersonService ePersonService=  EPersonServiceFactory.getInstance().getEPersonService();

    public void getRoles(final DSpaceObject dso) {
        DSpaceRoles dSpaceRoles = new DSpaceRoles();
        List<Group> groups;
        List<EPerson> ePeople;
        if (dso.getType() == Constants.SITE) {

        } else if (dso.getType() == Constants.COMMUNITY) {

        } else if (dso.getType() == Constants.COLLECTION) {

        }


    }

    public void getGroups(final Site site) throws SQLException {
        List<Group> groups = groupService.findAll(Curator.curationContext(), null);
        // GroupService groupService;
    }

    public void getGroups(final Community community) throws SQLException {
        List<Group> groups = new ArrayList<>();
        Group administrators = community.getAdministrators();
        groups.add(administrators);

        List<Group> matchingGroups = groupService.search(Curator.curationContext(), "COMMUNITY\\_" + community.getID() + "\\_");
        for (Group group : matchingGroups) {
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }

    }

    public void getGroups(final Collection collection) throws SQLException {
        List<Group> groups = new ArrayList<>();

        Group administrators = collection.getAdministrators();
        if (administrators != null) {
            groups.add(administrators);
        }

        Group submitters = collection.getSubmitters();
        if (submitters != null) {
            groups.add(submitters);
        }

        Group workflowStep1 = collection.getWorkflowStep1();
        if (workflowStep1 != null) {
            groups.add(workflowStep1);
        }

        Group workflowStep2 = collection.getWorkflowStep2();
        if (workflowStep2 != null) {
            groups.add(workflowStep2);
        }

        Group workflowStep3 = collection.getWorkflowStep3();
        if (workflowStep3 != null) {
            groups.add(workflowStep3);
        }

        List<Group> matchingGroups = groupService.search(Curator.curationContext(), "COMMUNITY\\_" + collection.getID() + "\\_");
        for (Group group : matchingGroups) {
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }
    }

    private org.dspace.pack.bagit.xml.site.Group groupToPojo(Group group) {
        org.dspace.pack.bagit.xml.site.Group pojo = new org.dspace.pack.bagit.xml.site.Group();
        for (EPerson ePerson : group.getMembers()) {
            Member member = new Member();
            member.setId(String.valueOf(ePerson.getID()));
            member.setName(ePerson.getName());
            pojo.addMember(member);
        }

        for (Group memberGroup : group.getMemberGroups()) {
            Member mb = new Member();
            mb.setId(String.valueOf(memberGroup.getID()));
            mb.setName(memberGroup.getName());
            pojo.addMemberGroup(mb);
        }

        return null;
    }

    private Member ePersonToMember(EPerson ep) {
        return null;
    }

    private Member groupToMember(Group group) {
        return null;
    }

    private Person ePersonToPerson(EPerson ePerson) {
        Person person = new Person();

        person.setNetId(ePerson.getNetid())
              .setId(String.valueOf(ePerson.getID()))
              .setEmail(ePerson.getEmail())
              .setLastName(ePerson.getLastName())
              .setFirstName(ePerson.getFirstName())
              .setLanguage(ePerson.getLanguage());

        if (ePerson.canLogIn()) {
            person.canLogin();
        }
        if (ePerson.getSelfRegistered()) {
            person.selfRegistered();
        }

        return person;
    }

}
