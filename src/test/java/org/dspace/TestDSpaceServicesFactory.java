/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import org.dspace.kernel.ServiceManager;
import org.dspace.services.ConfigurationService;
import org.dspace.services.EmailService;
import org.dspace.services.EventService;
import org.dspace.services.RequestService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

/**
 * {@link DSpaceServicesFactory} so that we can retrieve the {@link ConfigurationService} and {@link ServiceManager}.
 * All other operations are unsupported.
 *
 * @author mikejritter
 */
public class TestDSpaceServicesFactory extends DSpaceServicesFactory {

    public static final String DSPACE_SERVICES_FACTORY = "dSpaceServicesFactory";

    @Override
    public ConfigurationService getConfigurationService() {
        return new DSpace().getConfigurationService();
    }

    @Override
    public EmailService getEmailService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EventService getEventService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RequestService getRequestService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceManager getServiceManager() {
        return new DSpace().getServiceManager();
    }
}
