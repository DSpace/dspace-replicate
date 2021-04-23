/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.dspace.content.Community;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CommunityService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.handle.Handle;
import org.junit.Test;

/**
 * Test for SitePacker
 *
 * @author mikejritter
 */
public class SitePackerTest extends BagItPackerTest {

    private final String handleAStr = "123456789/1";
    private final String handleBStr = "123456789/2";

    //@Test
    public void pack() throws Exception {
        // Output directory
        final URL resources = SitePackerTest.class.getClassLoader().getResource("");
        assertThat(resources).isNotNull();
        final Path output = Paths.get(resources.toURI()).resolve("site-packer-test");

        // DSpaceObjects
        final Site site = initDSO(Site.class);
        final Community communityA = initDSO(Community.class);
        final Handle handleA = initReloadable(Handle.class);
        handleA.setHandle(handleAStr);
        communityA.getHandles().add(handleA);
        final Community communityB = initDSO(Community.class);
        final Handle handleB = initReloadable(Handle.class);
        handleB.setHandle(handleBStr);
        communityB.getHandles().add(handleB);

        // mock interactions
        final List<Community> topCommunities = ImmutableList.of(communityA, communityB);
        final CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
        when(communityService.findAllTop(any(Context.class))).thenReturn(topCommunities);

        final GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
        final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
        when(groupService.findAll(any(Context.class), isNull(List.class))).thenReturn(ImmutableList.<Group>of());
        when(ePersonService.findAll(any(Context.class), eq(EPerson.EMAIL))).thenReturn(ImmutableList.<EPerson>of());

        // pack + verify
        final SitePacker packer = new SitePacker(site, archFmt);
        final File packedSite = packer.pack(output.toFile());

        assertThat(packedSite).exists();
        assertThat(packedSite).isFile();

        // pack does not store the members, so they should be absent
        assertThat(packer.getMembers()).isEqualTo(Optional.absent());

        packedSite.deleteOnExit();

        verify(communityService, times(1)).findAllTop(any(Context.class));
        verify(groupService, times(1)).findAll(any(Context.class), isNull(List.class));
        verify(ePersonService, times(1)).findAll(any(Context.class), eq(EPerson.EMAIL));
        verifyNoMoreInteractions(communityService, groupService, ePersonService);
    }

    //@Test
    public void unpack() throws Exception {
        final String siteAip = "SITE@123456789-0";

        final URL resources = CollectionPackerTest.class.getClassLoader().getResource("unpack");
        assertThat(resources).isNotNull();

        final Path archive = Paths.get(resources.toURI()).resolve(siteAip + "." + archFmt);
        final Path open = Paths.get(resources.toURI()).resolve(siteAip);

        final Site site = initDSO(Site.class);
        assertThat(site).isNotNull();

        final SitePacker sitePacker = new SitePacker(site, archFmt);
        sitePacker.unpack(archive.toFile());

        final List<String> members = sitePacker.getMembers().or(new ArrayList<String>());
        assertThat(members).isNotEmpty();
        assertThat(members).contains(handleAStr, handleBStr);

        assertThat(open).doesNotExist();
    }
}
