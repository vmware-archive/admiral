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

    void initConfig(ServiceHost host, Operation post);

    void waitForInitConfig(ServiceHost host, String configFile, Runnable successfulCallback,
            Consumer<Throwable> failureCallback);

    Service getAuthenticationService();

    String getAuthenticationServiceSelfLink();

    Function<Claims, String> getAuthenticationServiceUserLinkBuilder();

    Collection<FactoryService> createServiceFactories();

    Class<? extends UserState> getUserStateClass();

}
