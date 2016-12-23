/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
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
 */
public final class ReleaseConstants {

    private ReleaseConstants() {
    }

    public static final int RELEASE_VERSION_0_9_3 = 93;

    // Other examples:
    // public static final int RELEASE_VERSION_0_9_4 = 94;
    // public static final int RELEASE_VERSION_1_0_0 = 100;

    // Alternative, if we expect/want double digits, it could be something like:
    // public static final int RELEASE_VERSION_5_10_4 = 5 * 10000 + 10 * 100 + 4;

}
