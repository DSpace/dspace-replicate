/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;

/**
 * Load the replicate.bag.tag configuration properties into memory for use with the {@link BagItAipWriter}
 *
 * @author mikejritter
 * @since 2020-03-24
 */
public class BagInfoHelper {

    /**
     * Private constructor for this utility class
     */
    private BagInfoHelper() {}

    /**
     * Loads the bag-info.txt and any other fields for tag files found under 'replicate.bag.tag'
     *
     * @return a Map containing the identifier of each tag file to its key-value pairs
     */
    public static Map<String, Map<String, String>> getTagFiles() {
        final String TAG_KEY = "replicate-bagit.tag";
        final String TAG_SUFFIX = ".txt";
        final Map<String, Map<String, String>> tagFiles = new HashMap<>();
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        final List<String> keys = configurationService.getPropertyKeys(TAG_KEY);

        // precompile patterns for when we split strings
        final Pattern dotSplit = Pattern.compile("\\.");
        final Pattern hyphenSplit = Pattern.compile("-");

        for (String key : keys) {
            final String[] split = dotSplit.split(key);
            final int keyLength = split.length;
            if (keyLength != 4) {
                throw new IllegalArgumentException("Key " + key + " has more values than expected! Please format " +
                                                   "as replicate-bagit.tag.TAG-FILE.TAG-FIELD");
            }

            // get the filename to use, and check if it needs to have a suffix attached
            String file = split[keyLength - 2];
            if (!file.endsWith(TAG_SUFFIX)) {
                file = file + TAG_SUFFIX;
            }

            // normalize the field to be formatted in a BagIt style, e.g. Field-Name
            final String field = split[keyLength - 1];
            final StringBuilder bagItNormalized = new StringBuilder();
            for (String fieldPart : hyphenSplit.split(field)) {
                bagItNormalized.append(Character.toUpperCase(fieldPart.charAt(0)));
                bagItNormalized.append(fieldPart.substring(1).toLowerCase());
                bagItNormalized.append("-");
            }
            // remove the trailing hyphen
            bagItNormalized.deleteCharAt(bagItNormalized.length() - 1);

            Map<String, String> tagFields = tagFiles.get(file);
            if (tagFields == null) {
                tagFields = new HashMap<>();
                tagFiles.put(file, tagFields);
            }

            tagFields.put(bagItNormalized.toString(), configurationService.getProperty(key));
        }

        return tagFiles;
    }
}
