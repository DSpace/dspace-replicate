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
import org.dspace.content.packager.PackageException;
import org.dspace.content.packager.PackageParameters;
import org.dspace.content.packager.RoleIngester;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.PasswordHash;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.roles.AssociatedGroup;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;
import org.dspace.pack.bagit.xml.roles.Password;
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
     * Retrieve all roles in a DSpace site. Gets all {@link Group}s and {@link EPerson}s.
     *
     * @return the DSpaceRoles
     * @throws SQLException if there are any errors querying the database
     * @throws PackageException if there are any errors translating group names
     */
    public static DSpaceRoles getDSpaceRoles() throws SQLException, PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService=  EPersonServiceFactory.getInstance().getEPersonService();

        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

        final List<Group> groups = groupService.findAll(Curator.curationContext(), null);
        for (Group group : groups) {
            dSpaceRoles.addGroup(new AssociatedGroup(group));
        }

        boolean includePasswords = false; // retrieve from service
        final List<EPerson> ePeople = ePersonService.findAll(Curator.curationContext(), EPerson.EMAIL);
        for (EPerson ePerson : ePeople) {
            final Person person = new Person(ePerson);
            if (includePasswords) {
                final PasswordHash passwordHash = ePersonService.getPasswordHash(ePerson);
                if (passwordHash != null) {
                    final Password password = new Password();
                    password.setHash(passwordHash.getHashString());
                    password.setSalt(passwordHash.getSaltString());
                    password.setAlgorithm(passwordHash.getAlgorithm());

                    person.setPassword(password);
                }
            }

            dSpaceRoles.addPerson(person);
        }

        return dSpaceRoles;
    }

    /**
     * Retrieve all roles for a {@link Community}. Only queries for {@link Group}s which belong to the given
     * {@link Community}.
     *
     * @param community the community to retrieve the roles for
     * @return the DSpaceRoles
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating group names
     */
    public static DSpaceRoles getDSpaceRoles(final Community community) throws SQLException, PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

        final DSpaceRoles dSpaceRoles = new DSpaceRoles();

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

    /**
     * Retrieve all roles for a {@link Collection}. Only queries for {@link Group}s which belong to the given
     * {@link Collection}.
     *
     * @param collection the collection to get the roles for
     * @return the DSpaceRoles
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating group names
     */
    public static DSpaceRoles getDSpaceRoles(final Collection collection) throws SQLException, PackageException {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

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

        final String groupIdentifier = "COLLECTION\\_" + collection.getID() + "\\_";
        final List<Group> matchingGroups = groupService.search(Curator.curationContext(), groupIdentifier);
        for (Group group : matchingGroups) {
            dSpaceRoles.addGroup(new AssociatedGroup(group));
        }

        return dSpaceRoles;
    }

    /**
     * Ingest an xml file and search for any DSpaceRoles. This is essentially a pass through to
     * {@link RoleIngester#ingestStream} as the DSpaceRoles schema is well defined.
     *
     * @param context the curation context
     * @param params the packaging parameters
     * @param dso the DSpaceObject to ingest roles on
     * @param xml the path to the xml files containing the DSpaceRoles
     * @throws IOException if there is an error reading the xml file
     * @throws SQLException if there is an error querying the database
     * @throws PackageException if there is an error translating a group name
     * @throws AuthorizeException if there is an authorization error
     */
    public static void ingest(final Context context, final PackageParameters params, final DSpaceObject dso,
                              final Path xml) throws IOException, SQLException, PackageException, AuthorizeException {
        final RoleIngester roleIngester = new RoleIngester();
        roleIngester.ingestStream(context, dso, params, Files.newInputStream(xml));
    }

}
