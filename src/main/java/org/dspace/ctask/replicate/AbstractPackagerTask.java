/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.util.Properties;
import org.dspace.content.packager.PackageParameters;
import org.dspace.core.ConfigurationManager;
import org.dspace.curate.AbstractCurationTask;

/**
 * AbstractPackagerTask encapsulates a few common convenience methods which may
 * be useful to curation tasks that wrap or utilize DSpace Packager classes
 * (org.dspace.content.packager.*).
 * 
 * @author Tim Donohue
 * @see org.dspace.app.packager.Packager
 * @see org.dspace.content.packager.PackageDisseminator
 * @see org.dspace.content.packager.PackageIngester
 */
public abstract class AbstractPackagerTask extends AbstractCurationTask 
{
    // Name of recursive mode option configurable in curation task configuration file
    private final String recursiveMode = "recursiveMode"; 
    
    // Name of useWorkflow option configurable in curation task configuration file
    private final String useWorkflow = "useWorkflow";
    
    // Name of useCollectionTemplate option configurable in curation task configuration file
    private final String useCollectionTemplate = "useCollectionTemplate";
    
    /**
     * Loads pre-configured PackageParameters settings from a given Module 
     * configuration file (specified by 'moduleName').
     * <p>
     * These PackageParameters should be configured using the following
     * configuration file format:
     * <p>
     * SETTING FORMAT: [taskname].[option] = [value]
     * <p>
     * Valid 'options' include all packager options supported by the
     * Packager class, e.g. AIP packagers minimally support these options:
     * https://wiki.duraspace.org/display/DSDOC18/AIP+Backup+and+Restore#AIPBackupandRestore-AdditionalPackagerOptions
     * <p>
     * Please note that different Packager classes will support different options.
     * You should determine which options are valid for your Packager class
     * and the curation task that utilizes it.
     * <p>
     * Example usage: if your curation task is named "myreplacetask" in curate.cfg,
     * then you can configure its PackageParameters like so:
     * <p>
     * myreplacetask.replaceMode = true
     * myreplacetask.recursiveMode = true
     * myreplacetask.createMetadataFields = true
     * myreplacetask.[any-supported-option] = [any-supported-value]
     * 
     * @param moduleName Module name to load configuration file and settings from
     * @return configured PackageParameters (or null, if configurations not found)
     * @see org.dspace.content.packager.PackageParameters
     */
    protected PackageParameters loadPackagerParameters(String moduleName)
    {
        //Load up the replicate-mets.cfg file & all settings inside it
        Properties moduleProps = ConfigurationManager.getProperties(moduleName);
        
        PackageParameters pkgParams = new PackageParameters();
        
        //If our config file doesn't load properly, we'll return null
        if(moduleProps!=null)
        {    
            //loop through all properties in the config file
            for(String property : moduleProps.stringPropertyNames())
            {
                //Only obey the setting(s) beginning with this task's ID/name, 
                if(property.startsWith(this.taskId))
                {
                    //Parse out the option name by removing the "[taskID]." from beginning of property
                    String option = property.replace(taskId + ".", "");
                    String value = moduleProps.getProperty(property);
                    
                    //Check which option is being set
                    if(option.equalsIgnoreCase(recursiveMode))
                    {
                        pkgParams.setRecursiveModeEnabled(Boolean.parseBoolean(value));
                    }
                    else if (option.equals(useWorkflow))
                    {
                        pkgParams.setWorkflowEnabled(Boolean.parseBoolean(value));
                    } 
                    else if (option.equals(useCollectionTemplate))
                    {
                        pkgParams.setUseCollectionTemplate(Boolean.parseBoolean(value));
                    }
                    else //otherwise, assume the Packager will understand what to do with this option
                    {
                        //just set it as a property in PackageParameters
                        pkgParams.addProperty(option, value);
                    }
                }    
            }

            return pkgParams;
        }
        else
            return null;
    }
}
