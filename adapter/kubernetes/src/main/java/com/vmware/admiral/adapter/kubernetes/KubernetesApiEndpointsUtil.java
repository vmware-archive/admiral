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

package com.vmware.admiral.adapter.kubernetes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.xenon.common.UriUtils;

public class KubernetesApiEndpointsUtil {

    private static final Map<String, String> exclusionsMap;
    private static final String VOWELS = "aeiou";

    static {
        exclusionsMap = Collections.unmodifiableMap(initExclusiionsMap());
    }

    /**
     * With a few exceptions, the API endpoint for a specific Kubernetes entity matches the lower
     * case plural form of the entity kind.
     *
     * @return the API endpoint for this entity without any version and namespace strings
     */
    public static String getEntityEndpoint(String entityType) {
        if (entityType == null || entityType.isEmpty()) {
            return null;
        }

        String endpoint = exclusionsMap.get(entityType);
        if (endpoint != null) {
            return UriUtils.URI_PATH_CHAR + endpoint;
        }

        return pluralize(UriUtils.URI_PATH_CHAR + entityType.toLowerCase());
    }

    /**
     * Build the map of exclusions to the general lowercase -> pluralize rule.
     */
    private static Map<String, String> initExclusiionsMap() {
        Map<String, String> exclusions = new HashMap<>();
        exclusions.put(KubernetesUtil.ENDPOINTS_TYPE, "endpoints");
        return exclusions;
    }

    /**
     * Tries to construct the plural form of a given singular word. The following basic rules are
     * applied:
     * <ul>
     * <li>if the singular ends with "s" or "x" append "es"</li>
     * <li>if the singular ends with "y" after a consonant, remove the "y" and append "ies"</li>
     * <li>in all other cases append "s"</li>
     * </ul>
     *
     * This method does not cover some of the more advanced plural rules of the English language
     * like f -> ves, o -> oes and irregular plurals. However, it should be just fine to construct
     * most of the Kubernetes paths. For paths that cannot be constructed with this method, add an
     * entry to the {@link #exclusionsMap}.
     *
     * @param singular
     *            the singular form of the word in lower case
     * @return the plural form of the word.
     * @see #initExclusiionsMap()
     */
    private static String pluralize(String singular) {
        int lastCharIndex = singular.length() - 1;
        StringBuilder sb = new StringBuilder(singular);

        if (sb.codePointAt(lastCharIndex) == 's'
                || sb.codePointAt(lastCharIndex) == 'x') {

            sb.append("es");

        } else if (sb.codePointAt(lastCharIndex) == 'y'
                && isConsonant(sb.codePointAt(lastCharIndex - 1))) {

            sb.setLength(lastCharIndex); // remove the trailing "y"
            sb.append("ies");

        } else {
            sb.append("s");
        }

        return sb.toString();
    }

    private static boolean isConsonant(int character) {
        return !isVowel(character);
    }

    private static boolean isVowel(int character) {
        return VOWELS.indexOf(character) >= 0;
    }

}
