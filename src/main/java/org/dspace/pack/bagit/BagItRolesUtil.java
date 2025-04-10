/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Site;
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageParameters;
import org.dspace.content.packager.RoleIngester;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.roles.AssociatedGroup;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;
import org.dspace.pack.bagit.xml.roles.Person;

/**
 * Create a {@link org.dspace.pack.bagit.xml.roles.DSpaceRoles} for a BagIt bag
 *
 * Uses {@link org.dspace.content.packager.RoleDisseminator} as a reference for obtaining groups/epeople
 *
 * @author mikejritter
 */
public class BagItRolesUtil {

    /**
     * Private constructor for this utility class
     */
    private BagItRolesUtil() {}

    /**
     * Retrieve all roles in a DSpace site. Gets all {@link Group}s and {@link EPerson}s.
     *
     * @param context the context to use
     * @param site the Site to get all roles for
     * @return the DSpaceRoles
     * @throws SQLException if there are any errors querying the database
     * @throws PackageException if there are any errors translating group names
     */
    public static DSpaceRoles getDSpaceRoles(final Context context, final Site site) throws SQLException,
            PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();

        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final List<Group> groups = groupService.findAll(context, null);
        for (Group group : groups) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, site, group));
        }

        final List<EPerson> ePeople = ePersonService.findAll(context, EPerson.EMAIL);
        for (EPerson ePerson : ePeople) {
            dSpaceRoles.addPerson(new Person(ePerson));
        }

        return dSpaceRoles;
    }

    /**
     * Retrieve all roles for a {@link Community}. Only queries for {@link Group}s which belong to the given
     * {@link Community}.
     *
     * @param context the context to use
     * @param community the community to retrieve the roles for
     * @return the DSpaceRoles
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating group names
     */
    public static DSpaceRoles getDSpaceRoles(final Context context, final Community community) throws SQLException,
            PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final Group administrators = community.getAdministrators();
        if (administrators != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, community, administrators));
        }

        final String groupIdentifier = "COMMUNITY\\_" + community.getID() + "\\_";
        final List<Group> matchingGroups = groupService.search(context, groupIdentifier);
        for (Group group : matchingGroups) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, community, group));
        }

        return dSpaceRoles;
    }

    /**
     * Retrieve all roles for a {@link Collection}. Only queries for {@link Group}s which belong to the given
     * {@link Collection}.
     *
     * @param context the context to use
     * @param collection the collection to get the roles for
     * @return the DSpaceRoles
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating group names
     */
    public static DSpaceRoles getDSpaceRoles(final Context context, final Collection collection) throws SQLException,
            PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final Group administrators = collection.getAdministrators();
        if (administrators != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, administrators));
        }

        final Group submitters = collection.getSubmitters();
        if (submitters != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, submitters));
        }

        final Group workflowStep1 = collection.getWorkflowStep1(context);
        if (workflowStep1 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, workflowStep1));
        }

        final Group workflowStep2 = collection.getWorkflowStep2(context);
        if (workflowStep2 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, workflowStep2));
        }

        final Group workflowStep3 = collection.getWorkflowStep3(context);
        if (workflowStep3 != null) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, workflowStep3));
        }

        final String groupIdentifier = "COLLECTION\\_" + collection.getID() + "\\_";
        final List<Group> matchingGroups = groupService.search(context, groupIdentifier);
        for (Group group : matchingGroups) {
            dSpaceRoles.addGroup(new AssociatedGroup(context, collection, group));
        }

        return dSpaceRoles;
    }

    /**
     * Ingest an xml file and search for any DSpaceRoles. This is essentially a pass through to
     * {@link RoleIngester#ingestStream} as the DSpaceRoles schema is well defined.
     *
     * @param context the context to use
     * @param dso the DSpaceObject to ingest roles on
     * @param xml the path to the xml files containing the DSpaceRoles
     * @throws IOException if there is an error reading the xml file
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating a group name
     * @throws AuthorizeException if there is an authorization error
     */
    public static void ingest(final Context context, final DSpaceObject dso, final Path xml) throws IOException,
        SQLException, PackageException, AuthorizeException {
        final PackageParameters parameters = new PackageParameters();
        parameters.setKeepExistingModeEnabled(true);
        final RoleIngester roleIngester = new RoleIngester();

        try (InputStream inputStream = Files.newInputStream(xml)) {
            roleIngester.ingestStream(context, dso, parameters, inputStream);
        }
    }

}
