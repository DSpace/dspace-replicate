/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.Descriptor;
import javax.management.DynamicMBean;
import javax.management.MBeanInfo;
import javax.management.modelmbean.DescriptorSupport;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import org.dspace.kernel.CommonLifecycle;
import org.dspace.kernel.DSpaceKernel;
import org.dspace.kernel.ServiceManager;
import org.dspace.services.ConfigurationService;

/**
 * Test kernel which can be given a {@link ServiceManager} and {@link ConfigurationService} so that we can mock objects
 * which make use of factories and static calls
 *
 * @author mikejritter
 */
public class TestDSpaceKernelImpl implements DSpaceKernel, DynamicMBean, CommonLifecycle<DSpaceKernel> {

    public static final String MBEAN_NAME = "org.dspace:name=testDSpaceKernelImpl,type=DSpaceKernel";

    private final ServiceManager serviceManager;
    private final ConfigurationService configurationService;

    public TestDSpaceKernelImpl(ServiceManager serviceManager,
                                ConfigurationService configurationService) {
        this.serviceManager = serviceManager;
        this.configurationService = configurationService;
    }

    @Override
    public Object getAttribute(String s) {
        return s;
    }

    @Override
    public void setAttribute(Attribute attribute) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttributeList getAttributes(String[] strings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributeList) {
        return attributeList;
    }

    @Override
    public Object invoke(String s, Object[] objects, String[] strings) {
        return s;
    }

    @Override
    public MBeanInfo getMBeanInfo() {
        // from DSpaceKernelImpl
        Descriptor lastLoadDateDesc = new DescriptorSupport(new String[] {"name=LastLoadDate",
                                                                          "descriptorType=attribute", "default=0",
                                                                          "displayName=Last Load Date",
                                                                          "getMethod=getLastLoadDate"});
        Descriptor lastLoadTimeDesc = new DescriptorSupport(new String[] {"name=LastLoadTime",
                                                                          "descriptorType=attribute", "default=0",
                                                                          "displayName=Last Load Time",
                                                                          "getMethod=getLoadTime"});

        ModelMBeanAttributeInfo[] mmbai = new ModelMBeanAttributeInfo[2];
        mmbai[0] = new ModelMBeanAttributeInfo("LastLoadDate", "java.util.Date", "Last Load Date",
                                               true, false, false, lastLoadDateDesc);

        mmbai[1] = new ModelMBeanAttributeInfo("LastLoadTime", "java.lang.Long", "Last Load Time",
                                               true, false, false, lastLoadTimeDesc);

        ModelMBeanOperationInfo[] mmboi = new ModelMBeanOperationInfo[7];

        mmboi[0] = new ModelMBeanOperationInfo("start", "Start DSpace Kernel", null, "void",
                                               ModelMBeanOperationInfo.ACTION);
        mmboi[1] = new ModelMBeanOperationInfo("stop", "Stop DSpace Kernel", null, "void",
                                               ModelMBeanOperationInfo.ACTION);
        mmboi[2] = new ModelMBeanOperationInfo("getManagedBean", "Get the Current Kernel", null,
                                               DSpaceKernel.class.getName(), ModelMBeanOperationInfo.INFO);

        return new ModelMBeanInfoSupport(this.getClass().getName(), "DSpace Kernel", mmbai, null, mmboi, null);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public DSpaceKernel getManagedBean() {
        return this;
    }

    @Override
    public void destroy() {
    }

    @Override
    public String getMBeanName() {
        return MBEAN_NAME;
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    @Override
    public ConfigurationService getConfigurationService() {
        return configurationService;
    }
}
