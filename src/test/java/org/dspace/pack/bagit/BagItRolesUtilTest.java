package org.dspace.pack.bagit;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.content.packager.PackageParameters;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.roles.AssociatedGroup;
import org.dspace.pack.bagit.xml.roles.DSpaceRoles;
import org.dspace.pack.bagit.xml.roles.Member;
import org.dspace.pack.bagit.xml.roles.Person;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link BagItRolesUtil}
 * - site wide roles
 * - community roles
 * - collection roles
 *
 * @author mikejriter
 */
public class BagItRolesUtilTest extends BagItPackerTest {

    private static final String NAME_FIELD = "name";
    private static final String EPERSON_LANGUAGE = "en_US";
    private static final String EPERSON_LAST_NAME = "last-name";
    private static final String EPERSON_FIRST_NAME = "first-name";
    private static final String EPERSON_EMAIL = "person@localhost";
    private static final String EPERSON_NETID = "netid";

    private GroupService groupService;
    private EPersonService ePersonService;

    @Before
    public void setup() throws SQLException {
        super.setup();

        groupService = EPersonServiceFactory.getInstance().getGroupService();
        ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    }

    @Test
    public void testGetDSpaceRolesForSite() throws Exception {

        final Group group = initDSO(Group.class);
        final Field name = Group.class.getDeclaredField(NAME_FIELD);
        name.setAccessible(true);
        name.set(group, Group.ADMIN);

        final EPerson ePerson = initDSO(EPerson.class);
        ePerson.setEmail(EPERSON_EMAIL);
        ePerson.setNetid(EPERSON_NETID);
        ePerson.setCanLogIn(true);
        ePerson.setSelfRegistered(true);

        // queries in BagItRolesUtil
        when(groupService.findAll(any(Context.class), isNull(List.class))).thenReturn(ImmutableList.of(group));
        when(ePersonService.findAll(any(Context.class), eq(EPerson.EMAIL))).thenReturn(ImmutableList.of(ePerson));

        // metadata queries for the ePerson
        final String schema = "eperson";
        final String firstName = "firstname";
        final String lastName = "lastname";
        final String language = "language";
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq(schema), eq(firstName), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_FIRST_NAME);
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq(schema), eq(lastName), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_LAST_NAME);
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq(schema), eq(language), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_LANGUAGE);

        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles();

        // verifications
        verify(groupService, times(1)).findAll(any(Context.class), isNull(List.class));
        verify(ePersonService, times(1)).findAll(any(Context.class), eq(EPerson.EMAIL));
        verify(ePersonService, times(1)).getMetadataFirstValue(eq(ePerson), eq(schema), eq(firstName),
                                                               isNull(String.class), eq(Item.ANY));
        verify(ePersonService, times(1)).getMetadataFirstValue(eq(ePerson), eq(schema), eq(lastName),
                                                               isNull(String.class), eq(Item.ANY));
        verify(ePersonService, times(1)).getMetadataFirstValue(eq(ePerson), eq(schema), eq(language),
                                                               isNull(String.class), eq(Item.ANY));

        // mocks were good, check the role mappings are correct
        assertThat(dSpaceRoles).isNotNull();
        final Set<Person> people = dSpaceRoles.getPeople();
        final Set<AssociatedGroup> groups = dSpaceRoles.getGroups();
        assertThat(people).isNotNull().hasSize(1);
        assertThat(groups).isNotNull().hasSize(1);

        for (Person person : people) {
            assertThat(person.getPassword()).isNull();
            assertThat(person.getRequiredCertificate()).isNull();
            assertThat(person.getCanLogin()).isNotNull();
            assertThat(person.getSelfRegistered()).isNotNull();
            assertThat(person.getId()).isEqualTo(valueOf(ePerson.getID()));
            assertThat(person.getNetId()).isEqualTo(ePerson.getNetid());
            assertThat(person.getEmail()).isEqualTo(ePerson.getEmail());
            assertThat(person.getFirstName()).isEqualTo(EPERSON_FIRST_NAME);
            assertThat(person.getLastName()).isEqualTo(EPERSON_LAST_NAME);
            assertThat(person.getLanguage()).isEqualTo(EPERSON_LANGUAGE);
        }

        for (AssociatedGroup associatedGroup : groups) {
            assertThat(associatedGroup.getMembers()).isNull();
            assertThat(associatedGroup.getMemberGroups()).isNull();

            assertThat(associatedGroup.getId()).isEqualTo(valueOf(group.getID()));
            assertThat(associatedGroup.getName()).isEqualTo(group.getName());
        }
    }

    @Test
    public void testGetDSpaceRolesForCommunity() throws Exception {
        // mock the community so that we can easily use community.getAdministrators to return our group
        final UUID uuid = UUID.randomUUID();
        final Community community = mock(Community.class);

        final Group adminGroup = initDSO(Group.class);
        final Group groupMember = initDSO(Group.class);

        // set the name for both groups
        final Field name = Group.class.getDeclaredField(NAME_FIELD);
        name.setAccessible(true);
        name.set(adminGroup, Group.ADMIN);
        name.set(groupMember, Group.ANONYMOUS);

        adminGroup.getMemberGroups().add(groupMember);

        when(community.getID()).thenReturn(uuid);
        when(community.getAdministrators()).thenReturn(adminGroup);

        // run the function and validate the result
        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles(community);

        // verify mocks
        final String query = "COMMUNITY\\_" + uuid + "\\_";
        verify(groupService, times(1)).search(any(Context.class), eq(query));
        verifyZeroInteractions(ePersonService);

        assertThat(dSpaceRoles).isNotNull();

        // communities have no people :(
        assertThat(dSpaceRoles.getPeople()).isNull();

        // verify that the groups are set up correctly:
        // 1 group with 1 memberGroup
        final Set<AssociatedGroup> associatedGroups = dSpaceRoles.getGroups();
        assertThat(associatedGroups).isNotNull().hasSize(1);

        for (AssociatedGroup associatedGroup : associatedGroups) {
            // a bit awkward but works
            if (associatedGroup.getId().equalsIgnoreCase(valueOf(adminGroup.getID()))) {
                // members were never set, so they should be null
                assertThat(associatedGroup.getMembers()).isNull();

                // validate the memberGroup
                assertThat(associatedGroup.getMemberGroups())
                    .isNotNull()
                    .hasSize(1);

                final Member member = associatedGroup.getMemberGroups().get(0);
                assertThat(member).isNotNull();
                assertThat(member.getName()).isEqualTo(groupMember.getName());
                assertThat(member.getId()).isEqualTo(valueOf(groupMember.getID()));
            } else {
                fail("Unexpected group");
            }
        }
    }

    @Test
    public void testGetDSpaceRolesForCollection() throws Exception {
        // mock the collection so that we can easily get groups from the collection getters
        final UUID uuid = UUID.randomUUID();
        final Collection collection = mock(Collection.class);

        final Group administrators = initDSO(Group.class);
        final EPerson ePerson = initDSO(EPerson.class);

        // set the name for both groups
        final Field name = Group.class.getDeclaredField(NAME_FIELD);
        name.setAccessible(true);
        name.set(administrators, Group.ADMIN);
        ePerson.setEmail("bagitroles");

        administrators.getMembers().add(ePerson);

        when(collection.getID()).thenReturn(uuid);
        when(collection.getAdministrators()).thenReturn(administrators);

        // run the function and validate the result
        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles(collection);

        // verify mocks
        final String query = "COLLECTION\\_" + uuid + "\\_";
        verify(groupService, times(1)).search(any(Context.class), eq(query));
        verifyZeroInteractions(ePersonService);

        assertThat(dSpaceRoles).isNotNull();

        // collections also have no people :(
        assertThat(dSpaceRoles.getPeople()).isNull();

        // verify that the groups are set up correctly:
        // 1 group with 1 memberGroup
        final Set<AssociatedGroup> associatedGroups = dSpaceRoles.getGroups();
        assertThat(associatedGroups).isNotNull().hasSize(1);

        for (AssociatedGroup associatedGroup : associatedGroups) {
            // a bit awkward but works
            if (associatedGroup.getId().equalsIgnoreCase(valueOf(administrators.getID()))) {
                // memberGroups were never set, so they should be null
                assertThat(associatedGroup.getMemberGroups()).isNull();

                // members were set, so validate
                assertThat(associatedGroup.getMembers())
                    .isNotNull()
                    .hasSize(1);

                final Member member = associatedGroup.getMembers().get(0);
                assertThat(member).isNotNull();
                assertThat(member.getName()).isEqualTo(ePerson.getName());
                assertThat(member.getId()).isEqualTo(valueOf(ePerson.getID()));
            } else {
                fail("Unexpected group");
            }
        }
    }

    @Test
    public void ingest() throws Exception {
        // get the roles.xml file
        final String location = "existing-bagit-aip/data/roles.xml";
        final URL resource = BagItRolesUtilTest.class.getClassLoader().getResource(location);
        assertThat(resource).isNotNull();
        Path xml = Paths.get(resource.toURI());

        // set up some interactions we expect to see in the RoleIngester
        // - an EPerson attached to the Context sharing the email from our roles.xml (in order to skip ops)
        // - a Group with name ADMINISTRATOR which exists (with keepExistingMode=true, this allows us to skip more ops)
        final Site site = mock(Site.class);
        final Group group = mock(Group.class);
        final EPerson ePerson = mock(EPerson.class);

        when(ePerson.getEmail()).thenReturn(EPERSON_EMAIL);
        when(ePerson.getNetid()).thenReturn(EPERSON_NETID);
        when(groupService.findByName(any(Context.class), eq(Group.ADMIN))).thenReturn(group);
        when(groupService.findByName(any(Context.class), eq(Group.ANONYMOUS))).thenReturn(group);

        // attach the EPerson to the context and create the PackageParameters then we're set to run
        final Context context = Curator.curationContext();
        context.setCurrentUser(ePerson);

        final PackageParameters parameters = new PackageParameters();
        parameters.setKeepExistingModeEnabled(true);

        BagItRolesUtil.ingest(context, parameters, site, xml);

        verify(ePerson, times(1)).getEmail();
        verify(ePerson, times(1)).getNetid();
        // the main group (admins) is retrieved twice by the RoleIngester
        verify(groupService, times(2)).findByName(any(Context.class), eq(Group.ADMIN));
        verify(groupService, times(1)).findByName(any(Context.class), eq(Group.ANONYMOUS));
        verify(groupService, times(1)).addMember(any(Context.class), eq(group), eq(group));
        verify(groupService, times(1)).update(any(Context.class), eq(group));
        verifyZeroInteractions(ePersonService);
        verifyNoMoreInteractions(groupService);
    }
}