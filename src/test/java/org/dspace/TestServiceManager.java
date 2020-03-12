package org.dspace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.kernel.ServiceManager;

/**
 * Service manager which stores registered services in memory and returns them for use
 *
 */
public class TestServiceManager implements ServiceManager {

    private Map<String, Object> serviceNameMap = new HashMap<>();

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

    }

    @Override
    public void pushConfig(Map<String, Object> settings) {

    }
}
