package org.dspace.pack.bagit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Site;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageParameters;
import org.dspace.content.packager.RoleIngester;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.site.AssociatedGroup;
import org.dspace.pack.bagit.xml.site.DSpaceRoles;
import org.dspace.pack.bagit.xml.site.Person;

/**
 * Create a {@link org.dspace.pack.bagit.xml.site.DSpaceRoles} for a BagIt bag
 *
 * Uses {@link org.dspace.content.packager.RoleDisseminator} as a reference for obtaining groups/epeople
 *
 * @author mikejritter
 */
public class BagItRolesUtil {

    private static final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private static final EPersonService ePersonService=  EPersonServiceFactory.getInstance().getEPersonService();

    public static DSpaceRoles getDSpaceRoles(final Site site) throws SQLException {
        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        List<Group> groups = groupService.findAll(Curator.curationContext(), null);
        for (Group group : groups) {
            dSpaceRoles.addGroup(new AssociatedGroup(group));
        }

        List<EPerson> ePeople = ePersonService.findAll(Curator.curationContext(), EPerson.EMAIL);
        for (EPerson ePerson : ePeople) {
            dSpaceRoles.addPerson(new Person(ePerson));
        }

        return dSpaceRoles;
    }

    public static DSpaceRoles getDSpaceRoles(final Community community) throws SQLException {
        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final List<Group> groups = new ArrayList<>();
        final Group administrators = community.getAdministrators();
        if (administrators != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(administrators));
        }

        final String groupIdentifier = "COMMUNITY\\_" + community.getID() + "\\_";
        final List<Group> matchingGroups = groupService.search(Curator.curationContext(), groupIdentifier);
        for (Group group : matchingGroups) {
            dSpaceRoles.addGroup(new AssociatedGroup(group));
        }

        return dSpaceRoles;
    }

    public static DSpaceRoles getDSpaceRoles(final Collection collection) throws SQLException {
        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final Group administrators = collection.getAdministrators();
        if (administrators != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(administrators));
        }

        final Group submitters = collection.getSubmitters();
        if (submitters != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(submitters));
        }

        final Group workflowStep1 = collection.getWorkflowStep1();
        if (workflowStep1 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(workflowStep1));
        }

        final Group workflowStep2 = collection.getWorkflowStep2();
        if (workflowStep2 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(workflowStep2));
        }

        final Group workflowStep3 = collection.getWorkflowStep3();
        if (workflowStep3 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(workflowStep3));
        }

        final String groupIdentifier = "COMMUNITY\\_" + collection.getID() + "\\_";
        final List<Group> matchingGroups = groupService.search(Curator.curationContext(), groupIdentifier);
        for (Group group : matchingGroups) {
            dSpaceRoles.addGroup(new AssociatedGroup(group));
        }

        return dSpaceRoles;
    }

    public static void ingest(DSpaceObject dso, Path xml) throws IOException, SQLException, PackageException, AuthorizeException {
        RoleIngester ingester = new RoleIngester();
        PackageParameters params = new PackageParameters();
        ingester.ingestStream(Curator.curationContext(), dso, params, Files.newInputStream(xml));
    }

}
