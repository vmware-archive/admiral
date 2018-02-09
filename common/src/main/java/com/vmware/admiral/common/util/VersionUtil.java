/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vmware.xenon.common.LocalizableValidationException;

/**
 * Utility class for version string comparison.
 */
public class VersionUtil {

    protected static final String CANNOT_EXTRACT_NUMERIC_VERSION_MESSAGE = "Cannot extract numeric version from";

    private static final String VERSION_GROUP_NAME = "version";
    private static final Pattern EXTRACT_NUMERIC_VERSION_PATTERN = Pattern
            .compile(String.format(".*?(?<%s>[0-9]+(?:\\.[0-9]+)*).*", VERSION_GROUP_NAME));

    /**
     * Tries to compare the provided numeric version strings. They must be in the format
     * <code>xxx.yyy.zzz</code> (variable number of segments is possible) where each segment is an
     * integer.
     *
     * @return -1 if numericVersion1 < numericVersion1, +1 if numericVersion1 > numericVersion1 and
     *         0 if the provided versions are equal
     * @throws LocalizableValidationException
     *             if any of the provided version strings is null or empty
     * @throws NumberFormatException.
     *             if any of sections of the version string cannot be parsed as an integer with
     *             radix 10.
     */
    public static int compareNumericVersions(String numericVersion1, String numericVersion2) {
        AssertUtil.assertNotNullOrEmpty(numericVersion1, "numericVersion1");
        AssertUtil.assertNotNullOrEmpty(numericVersion2, "numericVersion2");

        String[] v1Tokens = numericVersion1.split("\\.");
        String[] v2Tokens = numericVersion2.split("\\.");

        int index = 0;
        while (index < v1Tokens.length
                && index < v2Tokens.length
                && v1Tokens[index].equals(v2Tokens[index])) {
            index++;
        }

        if (index < Math.min(v1Tokens.length, v2Tokens.length)) {
            int diff = Integer.parseInt(v1Tokens[index]) - Integer.parseInt(v2Tokens[index]);
            return Integer.signum(diff);
        }

        return Integer.signum(v1Tokens.length - v2Tokens.length);
    }

    /**
     * Tries to compare the provided raw version strings by first extracting numeric versions of the
     * form <code>xxx.yyy.zzz</code> with variable number of segments.
     *
     * @return -1 if rawVersion1 < rawVersion2, +1 if rawVersion1 > rawVersion2 and 0 if the
     *         provided versions are equal
     * @throws LocalizableValidationException
     *             if any of the provided version strings is null or empty
     * @throws IllegalArgumentException
     *             if it is not possible to extract numeric versions out of the raw versions.
     */
    public static int compareRawVersions(String rawVersion1, String rawVersion2) {
        AssertUtil.assertNotNullOrEmpty(rawVersion1, "rawVersion1");
        AssertUtil.assertNotNullOrEmpty(rawVersion2, "rawVersion2");
        return compareNumericVersions(
                extractNumericVersion(rawVersion1),
                extractNumericVersion(rawVersion2));
    }

    /**
     * Extracts a version string in the format xxx.yyy.zzz (with variable number of segments) out of
     * a raw string. E.g. it will extract <code>1.3.0</code> out of <code>v1.3.0-abcdef</code>.
     *
     * @throws IllegalArgumentException
     *             if the extraction is not possible.
     */
    public static String extractNumericVersion(String rawVersion) {
        AssertUtil.assertNotNullOrEmpty(rawVersion, "rawVersion");

        Matcher matcher = EXTRACT_NUMERIC_VERSION_PATTERN.matcher(rawVersion);
        if (matcher.matches()) {
            return matcher.group(VERSION_GROUP_NAME);
        }

        throw new IllegalArgumentException(
                String.format("%s %s", CANNOT_EXTRACT_NUMERIC_VERSION_MESSAGE, rawVersion));
    }

}
