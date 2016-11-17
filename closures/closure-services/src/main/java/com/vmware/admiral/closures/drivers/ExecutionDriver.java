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

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.xenon.common.ServiceHost;

/**
 *
 * Execution driver responsible for closure execution.
 *
 * <Closure>
 */
public interface ExecutionDriver {

    /**
     * Runs a closure using provided inputs according to the requested resource constraints.
     *
     */
    void executeClosure(Closure closure, ClosureDescription closureDescription, String token, Consumer<Throwable>
            errorHandler);

    /**
     * Clean by closure.
     *
     * @param closure instance to cancel
     */
    void cleanClosure(Closure closure, Consumer<Throwable> errorHandler);

    /**
     * Clean docker image for specific compute state.
     *
     * @param imageName
     * @param computeStateLink
     * @param errorHandler
     */
    void cleanImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler);

    /**
     * Inspect docker image with a specific name.
     *
     * @param imageName
     * @param computeStateLink
     * @param errorHandler
     */
    void inspectImage(String imageName, String computeStateLink, Consumer<Throwable> errorHandler);

    ServiceHost getServiceHost();

}
