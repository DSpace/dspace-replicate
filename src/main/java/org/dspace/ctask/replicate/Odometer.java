/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Odometer holds a small set of persistent operational parameters of service
 * usage. This can assist the consumer of the service to monitor it's cost,
 * inter alia.
 * <p>
 * The Odometer tracks basic statistics of replication activities: bytes uploaded, 
 * modified, count of objects, and external objectstore size.
 * <p>
 * See org.dspace.ctask.replicate.ReplicaManager for how the Odometer readings
 * are kept up-to-date.
 *
 * @author richardrodgers
 * @see org.dspace.ctask.replicate.ReplicaManager
 */
public class Odometer
{
    // name of file
    private static final String ODO_NAME = "odometer";
    // names of fixed properties
    public static final String COUNT = "count";
    public static final String SIZE = "storesize";
    public static final String UPLOADED = "uploaded";
    public static final String DOWNLOADED = "downloaded";
    public static final String MODIFIED = "modified";
    // is this a read-only copy?
    private boolean readOnly = false;
    // odometer properties - hold the values
    private Properties odoProps = null;
    // directory path
    private String dirPath = null;

    Odometer(String dirPath, boolean readOnly) throws IOException
    {
        this.readOnly = readOnly;
        this.dirPath = dirPath;
        odoProps = new Properties();
        try
        {
            File odoFile = new File(dirPath, ODO_NAME);
            if (odoFile.exists())
            {
                
                InputStream in = null;
                try
                {
                    in = new FileInputStream(odoFile);
                    odoProps.load(new FileInputStream(odoFile));
                }
                finally
                {
                    if (in != null)
                    {
                        in.close();
                    }
                }
            }
        }
        catch (FileNotFoundException fnfE)
        {
            throw new IOException(fnfE);
        }
    }

    void save() throws IOException
    {
        if (! readOnly)
        {
            odoProps.setProperty("modified", String.valueOf(System.currentTimeMillis()));
            File odoFile = new File(dirPath, ODO_NAME);
            OutputStream out = null;
            try
            {
                out = new FileOutputStream(odoFile);
                odoProps.store(out, null);
            }
            finally
            {
                if (out != null)
                {
                    out.close();
                }
            }
        }
    }

    void adjustProperty(String name, long adjustment)
    {
        long val = getProperty(name);
        setProperty(name, val + adjustment);
    }

    void setProperty(String name, long value)
    {
        odoProps.setProperty(name, String.valueOf(value));
    }
    
    public long getProperty(String name)
    {
       String val = odoProps.getProperty(name);
       long lval = val != null ? Long.valueOf(val) : 0L;
       return lval;
    }
}
