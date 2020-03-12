package org.dspace;

import org.dspace.kernel.ServiceManager;
import org.dspace.services.CachingService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.EmailService;
import org.dspace.services.EventService;
import org.dspace.services.RequestService;
import org.dspace.services.SessionService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.utils.DSpace;

/**
 *
 */
public class TestDSpaceServicesFactory extends DSpaceServicesFactory {

    public static final String DSPACE_SERVICES_FACTORY = "dSpaceServicesFactory";

    @Override
    public CachingService getCachingService() {
        throw new UnsupportedOperationException();
    }

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
    public SessionService getSessionService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ServiceManager getServiceManager() {
        return new DSpace().getServiceManager();
    }
}
