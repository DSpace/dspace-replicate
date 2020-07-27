package org.dspace;

import static org.mockito.Mockito.mock;

import org.dspace.handle.factory.HandleServiceFactory;
import org.dspace.handle.service.HandleService;

/**
 * {@link HandleServiceFactory} for testing
 *
 * @author mikejritter
 */
public class TestHandleServiceFactory extends HandleServiceFactory {

    private final HandleService handleService = mock(HandleService.class);

    @Override
    public HandleService getHandleService() {
        return handleService;
    }
}
