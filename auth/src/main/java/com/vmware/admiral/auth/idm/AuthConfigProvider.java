/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.idm;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vmware.xenon.common.Claims;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.UserService.UserState;

public interface AuthConfigProvider {

    String PROPERTY_SCOPE = "scope";

    enum CredentialsScope {
        SYSTEM
    }

    /**
     * Initialization method to be executed as a first-boot script. When initialization completes
     * successful the passed as an parameter operation completes, in case of an error the operation
     * fails with the exception that has occurred.
     */
    void initBootConfig(ServiceHost host, Operation post, Consumer<Operation> authContext);

    /**
     * Initialization method to be executed as an every-boot script. When initialization completes
     * successful the passed as an parameter operation completes, in case of an error the operation
     * fails with the exception that has occurred.
     */
    void initConfig(ServiceHost host, Operation post, Consumer<Operation> authContext);

    /**
     * Waits for the first-boot initialization to be completed (for testing purposes).
     */
    void waitForInitBootConfig(ServiceHost host, String configFile, Runnable successfulCallback,
            Consumer<Throwable> failureCallback);

    Service getAuthenticationService();

    String getAuthenticationServiceSelfLink();

    Function<Claims, String> getAuthenticationServiceUserLinkBuilder();

    Function<Claims, String> getAuthenticationServiceUserFactoryLinkBuilder();

    Collection<FactoryService> createServiceFactories();

    Collection<Service> createServices();

    /**
     * @return a list of services that should be registered as privileged services.
     */
    Collection<Class<? extends Service>> getPrivilegedServices();

    Class<? extends UserState> getUserStateClass();

}
