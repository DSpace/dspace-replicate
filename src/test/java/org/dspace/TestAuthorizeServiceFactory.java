package org.dspace;

import static org.mockito.Mockito.mock;

import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;

/**
 * {@link org.dspace.authorize.factory.AuthorizeServiceFactory} for testing
 *
 * @author mikejritter
 */
public class TestAuthorizeServiceFactory extends AuthorizeServiceFactory  {

    private final AuthorizeService authorizeService = mock(AuthorizeService.class);
    private final ResourcePolicyService resourcePolicyService = mock(ResourcePolicyService.class);


    @Override
    public AuthorizeService getAuthorizeService() {
        return authorizeService;
    }

    @Override
    public ResourcePolicyService getResourcePolicyService() {
        return resourcePolicyService;
    }
}
