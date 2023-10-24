/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import static org.dspace.TestContentServiceFactory.CONTENT_SERVICE_FACTORY;
import static org.dspace.TestDSpaceServicesFactory.DSPACE_SERVICES_FACTORY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.UUID;

import org.dspace.TestAuthorizeServiceFactory;
import org.dspace.TestConfigurationService;
import org.dspace.TestContentServiceFactory;
import org.dspace.TestDSpaceKernelImpl;
import org.dspace.TestDSpaceServicesFactory;
import org.dspace.TestEPersonServiceFactory;
import org.dspace.TestHandleServiceFactory;
import org.dspace.TestServiceManager;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.core.DBConnection;
import org.dspace.core.ReloadableEntity;
import org.dspace.event.factory.EventServiceFactory;
import org.dspace.event.service.EventService;
import org.dspace.kernel.DSpaceKernel;
import org.dspace.kernel.DSpaceKernelManager;
import org.dspace.kernel.ServiceManager;
import org.dspace.services.ConfigurationService;
import org.junit.Before;

/**
 * Base class for all BagIt packing/unpacking tests. This performs initial setup so that the DSpaceKernel is not null
 * and so that classes used through static contexts or have static initializers (e.g.
 * {@link org.dspace.services.factory.DSpaceServicesFactory}, {@link org.dspace.core.Context}) can initialize and
 * retrieve any classes which are necessary for basic operations.
 *
 * @author mikejritter
 */
public abstract class BagItPackerTest {

    // metadata info
    public static final String SCHEMA_NAME = "metadataSchema";
    public static final String FIELD_ELEMENT = "metadataFieldElement";
    public static final String FIELD_QUALIFIER = "metadataFieldQualifier";
    public static final String METADATA_VALUE = "metadataValue";
    // factory identifiers
    public static final String EVENT_SERVICE_FACTORY = "eventServiceFactory";
    public static final String HANDLE_SERVICE_FACTORY = "handleServiceFactory";
    public static final String EPERSON_SERVICE_FACTORY = "ePersonServiceFactory";
    public static final String AUTHORIZE_SERVICE_FACTORY = "authorizeServiceFactory";
    // Mocks for Context init
    private final DBConnection dbConnection = mock(DBConnection.class);
    private final EventService eventService = mock(EventService.class);
    private final EventServiceFactory eventServiceFactory = mock(EventServiceFactory.class);

    protected final Context mockContext = mock(Context.class);

    protected final String archFmt = "zip";

    @Before
    public void setup() throws SQLException {
        ServiceManager serviceManager = new TestServiceManager();
        ConfigurationService configurationService = new TestConfigurationService();
        configurationService.setProperty("replicate.packer.archfmt", archFmt);
        configurationService.setProperty("replicate-bagit.tag.bag-info.source-organization", "org.dspace");

        serviceManager.registerService(DBConnection.class.getName(), dbConnection);
        serviceManager.registerService(ConfigurationService.class.getName(), configurationService);
        serviceManager.registerService(EVENT_SERVICE_FACTORY, eventServiceFactory);
        serviceManager.registerService(HANDLE_SERVICE_FACTORY, new TestHandleServiceFactory());
        serviceManager.registerService(EPERSON_SERVICE_FACTORY, new TestEPersonServiceFactory());
        serviceManager.registerService(AUTHORIZE_SERVICE_FACTORY, new TestAuthorizeServiceFactory());
        serviceManager.registerService(DSPACE_SERVICES_FACTORY, new TestDSpaceServicesFactory());
        serviceManager.registerService(CONTENT_SERVICE_FACTORY, new TestContentServiceFactory());

        DSpaceKernel kernel = new TestDSpaceKernelImpl(serviceManager, configurationService);
        DSpaceKernelManager.registerMBean(kernel.getMBeanName(), kernel);
        DSpaceKernelManager.setDefaultKernel(kernel);

        // expected mock interactions
        when(eventServiceFactory.getEventService()).thenReturn(eventService);
    }

    /**
     * Initialize a DSpaceObject JPA entity with a random UUID
     *
     * @param clazz the {@link Class} to initialize
     * @param <T> the type of the class
     * @return the initialized object
     * @throws ReflectiveOperationException when the class cannot be instantiated
     */
    protected <T extends DSpaceObject> T initDSO(Class<T> clazz) throws ReflectiveOperationException {
        Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        T t = constructor.newInstance((Object []) null);
        Field id = DSpaceObject.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(t, UUID.randomUUID());
        return t;
    }

    /**
     * Initialize a {@link ReloadableEntity<Integer>} and set the id to 1
     *
     * @param clazz the class to initialize
     * @param <T> the type of the class
     * @return the initialized object
     * @throws ReflectiveOperationException when the class cannot be instantiated
     */
    protected <T extends ReloadableEntity<Integer>> T initReloadable(Class<T> clazz)
        throws ReflectiveOperationException {
        Constructor<T> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        T t = constructor.newInstance((Object []) null);
        Field id = clazz.getDeclaredField("id");
        id.setAccessible(true);
        id.set(t, 1);
        return t;
    }

    protected MetadataValue createMetadataValue() throws ReflectiveOperationException {
        final MetadataValue metadataValue;
        final MetadataField metadataField;
        final MetadataSchema metadataSchema;
        metadataSchema = initReloadable(MetadataSchema.class);
        metadataSchema.setName(SCHEMA_NAME);

        metadataField = initReloadable(MetadataField.class);
        metadataField.setMetadataSchema(metadataSchema);
        metadataField.setElement(FIELD_ELEMENT);
        metadataField.setQualifier(FIELD_QUALIFIER);

        metadataValue = initReloadable(MetadataValue.class);
        metadataValue.setMetadataField(metadataField);
        metadataValue.setValue(METADATA_VALUE);
        return metadataValue;
    }

}
