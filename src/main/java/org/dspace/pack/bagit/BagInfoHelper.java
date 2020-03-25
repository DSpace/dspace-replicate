/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.util.Collections;
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

    private static final String TAG_KEY = "replicate.bag.tag";

    private static Map<String, Map<String, String>> tagFiles = new HashMap<>();

    private BagInfoHelper() {
    }

    /**
     * Loads the bag-info.txt and any other fields for tag files found under replicate.bag.tag
     */
    private static void loadFromConfiguration() {
        final ConfigurationService configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();

        final List<String> keys = configurationService.getPropertyKeys(TAG_KEY);

        // precomplie patterns for when we split strings
        final Pattern dotSplit = Pattern.compile("\\.");
        final Pattern hyphenSplit = Pattern.compile("-");

        for (String key : keys) {
            final String[] split = dotSplit.split(key);
            final int keyLength = split.length;
            if (keyLength != 5) {
                throw new IllegalArgumentException("Key " + key + " has more values than expected! Please format " +
                                                   "as replicate.bag.tag.TAG-FILE.TAG-FIELD");
            }

            // grab the last two parts of the key, which should be the file name and field
            final String file = split[keyLength - 2];
            final String field = split[keyLength - 1];

            // normalize the field to be formatted in a BagIt style, e.g. Field-Name
            final StringBuilder bagItNormalized = new StringBuilder();
            for (String fieldPart : hyphenSplit.split(field)) {
                bagItNormalized.append(Character.toUpperCase(fieldPart.charAt(0)));
                bagItNormalized.append(field.substring(1).toLowerCase());
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
    }

    /**
     * Get a read-only copy of the properties read in
     *
     * @return the tag file properties
     */
    public static Map<String, Map<String, String>> getTagFiles() {
        if (tagFiles.isEmpty()) {
            loadFromConfiguration();
        }

        return Collections.unmodifiableMap(tagFiles);
    }

}
