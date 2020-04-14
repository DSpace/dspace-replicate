/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import java.util.List;
import java.util.Properties;

import org.apache.commons.configuration.Configuration;
import org.dspace.services.ConfigurationService;

/**
 * Configuration service which holds values in memory
 *
 * @author mikejritter
 */
public class TestConfigurationService implements ConfigurationService {
    final Properties properties = new Properties();

    @Override
    public <T> T getPropertyAsType(String name, Class<T> type) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getPropertyAsType(String name, T defaultValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T getPropertyAsType(String name, T defaultValue, boolean setDefaultIfNotFound) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getPropertyKeys() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getPropertyKeys(String prefix) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        return properties.containsKey(name) ? properties.getProperty(name) : defaultValue;
    }

    @Override
    public String[] getArrayProperty(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getArrayProperty(String name, String[] defaultValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getBooleanProperty(String name) {
        return Boolean.parseBoolean(properties.getProperty(name));
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return properties.containsKey(name) ? Boolean.parseBoolean(properties.getProperty(name)) : defaultValue;
    }

    @Override
    public int getIntProperty(String name) {
        return Integer.parseInt(properties.getProperty(name));
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return properties.containsKey(name) ? Integer.parseInt(properties.getProperty(name)) : defaultValue;
    }

    @Override
    public long getLongProperty(String name) {
        return Long.parseLong(properties.getProperty(name));
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return properties.containsKey(name) ? Long.parseLong(properties.getProperty(name)) : defaultValue;
    }

    @Override
    public Object getPropertyValue(String name) {
        return properties.get(name);
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public Configuration getConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    @Override
    public boolean setProperty(String name, Object value) {
        properties.put(name, value);
        return true;
    }

    @Override
    public void reloadConfig() {
    }
}
