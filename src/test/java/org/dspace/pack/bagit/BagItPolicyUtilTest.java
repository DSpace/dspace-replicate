package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.matches;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Community;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.pack.bagit.xml.Policy;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the BagItPolicyUtil on the serialization and registration of ResourcePolicies on DSpaceObjects
 *
 * Note: These tests do not run over Managed Groups because they make calls to
 * {@link org.dspace.content.packager.PackageUtils} which has a myriad of static initializers. In order to support that
 * we need to add all Services to the {@link org.dspace.TestContentServiceFactory} as well as implementing
 * {@link ContentServiceFactory#getDSpaceObjectLegacySupportServices()}.
 *
 * @author mikejritter
 */
public class BagItPolicyUtilTest extends BagItPackerTest {

    private Community community;

    @Before
    public void setup() throws SQLException {
        super.setup();

        try {
            community = initDSO(Community.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to init DSpaceObject for testing", e);
        }
    }

    @Test
    public void getPolicy() {
    }

    @Test
    public void registerPolicies() throws Exception {
        // Read an aip in order to load a policy.xml
        final URL resources = BagItPolicyUtilTest.class.getClassLoader().getResource("");
        assertThat(resources).isNotNull();

        final Path aip = Paths.get(resources.toURI()).resolve("existing-bagit-aip");
        final BagItAipReader reader = new BagItAipReader(aip);

        final Policy policy = reader.readPolicy();
        assertThat(policy).isNotNull();

        // Create each of the expected groups and an ePerson: Admin, Anonymous, GROUP, dspace-user@localhost.localdomain
        final String personEmail = "dspace-user@localhost.localdomain";
        final Group group = initDSO(Group.class);
        final EPerson ePerson = initDSO(EPerson.class);

        // Set up expected interactions with our mocks
        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        final ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance().getResourcePolicyService();
        when(resourcePolicyService.create(any(Context.class))).thenReturn(initReloadable(ResourcePolicy.class));
        when(groupService.findByName(any(Context.class), eq(Group.ADMIN))).thenReturn(group);
        when(groupService.findByName(any(Context.class), eq(Group.ANONYMOUS))).thenReturn(group);
        when(ePersonService.findByEmail(any(Context.class), eq(personEmail))).thenReturn(ePerson);

        // Register the policy on a DSpaceObject
        final BagItPolicyUtil policyUtil = new BagItPolicyUtil();
        policyUtil.registerPolicies(community, policy);

        // verify service interactions
        verify(resourcePolicyService, times(8)).create(any(Context.class));
        verify(groupService, times(4)).findByName(any(Context.class), matches(Group.ADMIN + "|" + Group.ANONYMOUS));
        // verify(groupService, times(3)).findByName(any(Context.class), eq(Group.ANONYMOUS));
        verify(ePersonService, times(4)).findByEmail(any(Context.class), eq(personEmail));

        // additional verification of services which we didn't need to set up
        final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
        verify(authorizeService, times(1)).removeAllPolicies(any(Context.class), eq(community));
        verify(authorizeService, times(1)).addPolicies(any(Context.class), anyListOf(ResourcePolicy.class),
                                                       eq(community));
    }
}