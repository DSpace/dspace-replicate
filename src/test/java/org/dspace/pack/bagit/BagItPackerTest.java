package org.dspace.pack.bagit;

import static org.dspace.TestContentServiceFactory.CONTENT_SERVICE_FACTORY;
import static org.dspace.TestDSpaceServicesFactory.DSPACE_SERVICES_FACTORY;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.UUID;

import org.dspace.TestConfigurationService;
import org.dspace.TestContentServiceFactory;
import org.dspace.TestDSpaceKernelImpl;
import org.dspace.TestDSpaceServicesFactory;
import org.dspace.TestServiceManager;
import org.dspace.content.DSpaceObject;
import org.dspace.core.DBConnection;
import org.dspace.event.factory.EventServiceFactory;
import org.dspace.event.service.EventService;
import org.dspace.kernel.DSpaceKernel;
import org.dspace.kernel.DSpaceKernelManager;
import org.dspace.kernel.ServiceManager;
import org.dspace.services.ConfigurationService;
import org.junit.After;
import org.junit.Before;

/**
 * Base class for all BagIt packing/unpacking tests
 *
 */
public abstract class BagItPackerTest {

    public static final String EVENT_SERVICE_FACTORY = "eventServiceFactory";
    // Mocks for Context init
    private final DBConnection dbConnection = mock(DBConnection.class);
    private final EventService eventService = mock(EventService.class);
    private final EventServiceFactory eventServiceFactory = mock(EventServiceFactory.class);

    protected final String archFmt = "zip";
    protected final String objectType = "test-bag";
    protected final String bundleName = "test-bundle";
    protected final String xmlBody = "test-xml-body";
    protected final String xmlAttr = "test-xml-attr";
    protected final String xmlAttrName = "name";

    @Before
    public void setup() throws SQLException {
        ServiceManager serviceManager = new TestServiceManager();
        ConfigurationService configurationService = new TestConfigurationService();
        configurationService.setProperty("replicate.packer.archfmt", archFmt);

        serviceManager.registerService(DBConnection.class.getName(), dbConnection);
        serviceManager.registerService(ConfigurationService.class.getName(), configurationService);
        serviceManager.registerService(EVENT_SERVICE_FACTORY, eventServiceFactory);
        serviceManager.registerService(DSPACE_SERVICES_FACTORY, new TestDSpaceServicesFactory());
        serviceManager.registerService(CONTENT_SERVICE_FACTORY, new TestContentServiceFactory());

        DSpaceKernel kernel = new TestDSpaceKernelImpl(serviceManager, configurationService);
        DSpaceKernelManager.registerMBean(kernel.getMBeanName(), kernel);
        DSpaceKernelManager.setDefaultKernel(kernel);

        // expected mock interactions
        when(eventServiceFactory.getEventService()).thenReturn(eventService);
    }

    @After
    public void verifyMocks() {
        verify(eventServiceFactory, atLeastOnce()).getEventService();
    }

    /**
     * Initialize a DSpace JPA entity with a random UUID
     *
     * @param clazz the {@link Class} to initialize
     * @param <T> the type of the class
     * @return the initialized object
     */
    protected <T extends DSpaceObject> T initJpa(Class<T> clazz) throws ReflectiveOperationException {
        Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        T t = constructor.newInstance((Object []) null);
        Field id = DSpaceObject.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(t, UUID.randomUUID());
        return t;
    }

}
