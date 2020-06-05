package org.dspace.pack.bagit;

import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.site.AssociatedGroup;
import org.dspace.pack.bagit.xml.site.DSpaceRoles;
import org.dspace.pack.bagit.xml.site.Member;
import org.dspace.pack.bagit.xml.site.Person;
import org.junit.Test;

/**
 * Tests for {@link BagItRolesUtil}
 * - site wide roles
 * - community roles
 * - collection roles
 *
 * todo: verify mocks
 * todo: ingest
 *
 * @author mikejriter
 */
public class BagItRolesUtilTest extends BagItPackerTest {

    public static final String NAME_FIELD = "name";
    public static final String EPERSON_LANGUAGE = "en_US";
    public static final String EPERSON_LAST_NAME = "last-name";
    public static final String EPERSON_FIRST_NAME = "first-name";
    public static final String EPERSON_EMAIL = "person@localhost";
    public static final String EPERSON_NETID = "netid";

    @Test
    public void testGetDSpaceRolesForSite() throws Exception {
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();

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
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq("eperson"), eq("firstname"), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_FIRST_NAME);
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq("eperson"), eq("lastname"), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_LAST_NAME);
        when(ePersonService.getMetadataFirstValue(eq(ePerson), eq("eperson"), eq("language"), isNull(String.class),
                                                  eq(Item.ANY))).thenReturn(EPERSON_LANGUAGE);

        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles();

        assertThat(dSpaceRoles).isNotNull();
        final Set<Person> people = dSpaceRoles.getPeople();
        final Set<AssociatedGroup> groups = dSpaceRoles.getGroups();
        assertThat(people).isNotNull().hasSize(1);
        assertThat(groups).isNotNull().hasSize(1);

        for (Person person : people) {
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
        final Community community = mock(Community.class);

        final Group adminGroup = initDSO(Group.class);
        final Group groupMember = initDSO(Group.class);

        // set the name for both groups
        final Field name = Group.class.getDeclaredField(NAME_FIELD);
        name.setAccessible(true);
        name.set(adminGroup, Group.ADMIN);
        name.set(groupMember, Group.ANONYMOUS);

        adminGroup.getMemberGroups().add(groupMember);

        when(community.getAdministrators()).thenReturn(adminGroup);

        // run the function and validate the result
        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles(community);
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
        final Collection collection = mock(Collection.class);

        final Group administrators = initDSO(Group.class);
        final EPerson ePerson = initDSO(EPerson.class);

        // set the name for both groups
        final Field name = Group.class.getDeclaredField(NAME_FIELD);
        name.setAccessible(true);
        name.set(administrators, Group.ADMIN);
        ePerson.setEmail("bagitroles");

        administrators.getMembers().add(ePerson);

        when(collection.getAdministrators()).thenReturn(administrators);

        // run the function and validate the result
        final DSpaceRoles dSpaceRoles = BagItRolesUtil.getDSpaceRoles(collection);
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

                final Member member = associatedGroup.getMemberGroups().get(0);
                assertThat(member).isNotNull();
                assertThat(member.getName()).isEqualTo(ePerson.getName());
                assertThat(member.getId()).isEqualTo(valueOf(ePerson.getID()));
            } else {
                fail("Unexpected group");
            }
        }
    }

    @Test
    public void ingest() {
    }
}