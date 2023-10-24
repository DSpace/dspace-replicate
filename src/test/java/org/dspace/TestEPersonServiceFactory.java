/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import static org.mockito.Mockito.mock;

import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.AccountService;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.eperson.service.RegistrationDataService;
import org.dspace.eperson.service.SubscribeService;

/**
 * {@link EPersonServiceFactory} for testing
 *
 * @author mikejritter
 */
public class TestEPersonServiceFactory extends EPersonServiceFactory {

    private final GroupService groupService = mock(GroupService.class);
    private final EPersonService ePersonService = mock(EPersonService.class);

    @Override
    public EPersonService getEPersonService() {
        return ePersonService;
    }

    @Override
    public GroupService getGroupService() {
        return groupService;
    }

    @Override
    public RegistrationDataService getRegistrationDataService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccountService getAccountService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscribeService getSubscribeService() {
        throw new UnsupportedOperationException();
    }

}
