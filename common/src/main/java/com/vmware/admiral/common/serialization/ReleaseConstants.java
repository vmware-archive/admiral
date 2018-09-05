/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.serialization;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

/**
 * Contains the release version constants as needed for the {@link Since} annotation, where
 * "Version of annotated field, default is 0, and must be incremental to maintain compatibility".
 * Contains also the equivalent REST API version constants as needed for the HTTP request handling
 * since the REST API version is expected in the accept header in the form of
 * application/json;version=0.9.5
 */
public final class ReleaseConstants {

    private ReleaseConstants() {
    }

    /**
     * The name used to indicate the REST API version.
     */
    public static final String VERSION_HEADER_NAME = "version";

    /**
     * The REST API version prefix added to the Accept header that is followed by the actual version
     * number.
     */
    public static final String VERSION_PREFIX = VERSION_HEADER_NAME + "=";

    /**
     * The 0.9.1 release and REST API version.
     */
    public static final int RELEASE_VERSION_0_9_1 = 0; // default value for @Since
    public static final String API_VERSION_0_9_1 = "0.9.1";
    public static final String VERSION_HEADER_0_9_1 = VERSION_PREFIX + API_VERSION_0_9_1;

    /**
     * The 0.9.5 release and REST API version.
     */
    public static final int RELEASE_VERSION_0_9_5 = 95;
    public static final String API_VERSION_0_9_5 = "0.9.5";
    public static final String VERSION_HEADER_0_9_5 = VERSION_PREFIX + API_VERSION_0_9_5;

    /**
     * The 1.2.0 release and REST API version.
     */
    public static final int RELEASE_VERSION_1_2_0 = 120;
    public static final String API_VERSION_1_2_0 = "1.2.0";
    public static final String VERSION_HEADER_1_2_0 = VERSION_PREFIX + API_VERSION_1_2_0;

    /**
     * The 1.2.2 release and REST API version.
     */
    public static final int RELEASE_VERSION_1_2_2 = 122;
    public static final String API_VERSION_1_2_2 = "1.2.2";
    public static final String VERSION_HEADER_1_2_2 = VERSION_PREFIX + API_VERSION_1_2_2;

    /**
     * The 1.4.1 release and REST API version.
     */
    public static final int RELEASE_VERSION_1_4_1 = 141;
    public static final String API_VERSION_1_4_1 = "1.4.1";
    public static final String VERSION_HEADER_1_4_1 = VERSION_PREFIX + API_VERSION_1_4_1;

    /**
     * The 1.4.2 release and REST API version.
     */
    public static final int RELEASE_VERSION_1_4_2 = 142;
    public static final String API_VERSION_1_4_2 = "1.4.2";
    public static final String VERSION_HEADER_1_4_2 = VERSION_PREFIX + API_VERSION_1_4_2;

    /**
     * The 1.4.3 release and REST API version.
     */
    public static final int RELEASE_VERSION_1_4_3 = 143;
    public static final String API_VERSION_1_4_3 = "1.4.3";
    public static final String VERSION_HEADER_1_4_3 = VERSION_PREFIX + API_VERSION_1_4_3;

    // Other examples:
    // public static final int RELEASE_VERSION_0_9_6 = 96;
    // public static final int RELEASE_VERSION_1_0_0 = 100;

    // Alternative, if we expect/want double digits, it could be something like:
    // public static final int RELEASE_VERSION_4_12_3 = 4 * 10000 + 12 * 100 + 3;

    /**
     * The current REST API version.
     */
    public static final String CURRENT_API_VERSION = API_VERSION_1_4_3;
    public static final String CURRENT_VERSION_HEADER = VERSION_PREFIX + CURRENT_API_VERSION;

}
