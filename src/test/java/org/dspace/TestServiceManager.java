/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.kernel.ServiceManager;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Service manager which stores registered services in memory and returns them for use
 *
 * @author mikejritter
 */
public class TestServiceManager implements ServiceManager {

    private Map<String, Object> serviceNameMap = new HashMap<>();

    @Override
    public ConfigurableApplicationContext getApplicationContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> getServicesByType(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getServiceByName(String name, Class<T> type) {
        return (T) serviceNameMap.get(name);
    }

    @Override
    public boolean isServiceExists(String name) {
        return false;
    }

    @Override
    public List<String> getServicesNames() {
        return new ArrayList<>(serviceNameMap.keySet());
    }

    @Override
    public <T> Map<String, T> getServicesWithNamesByType(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void registerService(String name, Object service) {
        serviceNameMap.put(name, service);
    }

    @Override
    public void registerServiceNoAutowire(String name, Object service) {
        serviceNameMap.put(name, service);
    }

    @Override
    public <T> T registerServiceClass(String name, Class<T> type) {
        // because we can't retrieve the autowired bean, disallow this
        throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterService(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pushConfig(Map<String, Object> settings) {
        throw new UnsupportedOperationException();
    }
}
