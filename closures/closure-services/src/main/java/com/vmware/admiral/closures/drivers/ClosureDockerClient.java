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

package com.vmware.admiral.closures.drivers;

import java.util.function.Consumer;

/**
 * Interface represents docker client.
 */
public interface ClosureDockerClient {

    /**
     * Removes container with Id.
     *
     * @param containerId Id of the container
     */
    void removeContainer(String containerId, Consumer<Throwable> errorHandler);

    /**
     * Creates and starts the container according to the provided configuration properties.
     *
     * @param imageConfig Image configuration to use
     * @param configuration Configuration properties to use
     */
    void createAndStartContainer(String closureLink, ImageConfiguration imageConfig,
            ContainerConfiguration
            configuration,
            Consumer<Throwable> errorHandler);

    /**
     * Cleans docker image
     *
     * @param imageName
     * @param computeStateLink
     * @param errorHandler
     */
    void cleanImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler);

    /**
     * Inspects docker image.
     *
     * @param imageName
     * @param computeStateLink
     * @param errorHandler
     */
    void inspectImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler);
}
