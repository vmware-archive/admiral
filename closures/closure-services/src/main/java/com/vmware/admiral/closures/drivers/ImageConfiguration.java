/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.closures.drivers;

/**
 * Represents image configuration used by the runtime.
 *
 */
public class ImageConfiguration {

    /**
     * Host & port of the image registry
     */
    public String registry;

    /**
     * Name of the runtime image
     */
    public String imageName;

    /**
     * Version of the runtime image
     */
    public String imageNameVersion;

    /**
     * Name of the runtime base image
     */
    public String baseImageName;

    /**
     * Version of the runtime base image
     */
    public String baseImageVersion;

    public ImageConfiguration() {

    }

    @Override public String toString() {
        return "ImageConfiguration{" +
                "registry='" + registry + '\'' +
                ", imageName='" + imageName + '\'' +
                ", imageNameVersion='" + imageNameVersion + '\'' +
                ", baseImageName='" + baseImageName + '\'' +
                ", baseImageVersion='" + baseImageVersion + '\'' +
                '}';
    }
}
