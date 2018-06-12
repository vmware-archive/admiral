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

package com.vmware.admiral.host;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.factoryService;
import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import com.vmware.admiral.log.EventLogFactoryService;
import com.vmware.admiral.service.common.CommonInitialBootService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionFactoryService;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LongURIGetService;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.RegistryFactoryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ReverseProxyService;
import com.vmware.admiral.service.common.SslTrustCertificateFactoryService;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.admiral.service.common.UniquePropertiesService;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class HostInitCommonServiceConfig extends HostInitServiceHelper {
    public static final int COMMON_SERVICES_AVAILABILITY_TIMEOUT = Integer
            .getInteger("common.services.initialization.timeout.seconds", 300);

    public static final Collection<ServiceMetadata> SERVICES_METADATA = Collections
            .unmodifiableList(Arrays.asList(
                    service(NodeHealthCheckService.class),
                    service(NodeMigrationService.class),
                    service(SslTrustImportService.class),
                    service(ConfigurationFactoryService.class),
                    service(SslTrustCertificateFactoryService.class),
                    service(CommonInitialBootService.class),
                    service(ReverseProxyService.class),
                    service(ExtensibilitySubscriptionFactoryService.class),
                    service(LongURIGetService.class),
                    service(RegistryFactoryService.class),
                    service(EventLogFactoryService.class),
                    factoryService(ResourceNamePrefixService.class),
                    factoryService(LogService.class),
                    factoryService(CounterSubTaskService.class),
                    factoryService(ExtensibilitySubscriptionCallbackService.class),
                    factoryService(EventTopicService.class),
                    factoryService(UniquePropertiesService.class)));

    @SuppressWarnings("unchecked")
    private static final Class<? extends Service>[] servicesToStart = new Class[] {
            NodeMigrationService.class,
            NodeHealthCheckService.class,
            SslTrustImportService.class,
            ConfigurationFactoryService.class,
            SslTrustCertificateFactoryService.class,
            CommonInitialBootService.class,
            ReverseProxyService.class,
            ExtensibilitySubscriptionFactoryService.class,
            LongURIGetService.class,
            RegistryFactoryService.class,
            EventLogFactoryService.class
    };

    @SuppressWarnings("unchecked")
    private static final Class<? extends Service>[] serviceFactoriesToStart = new Class[] {
            ResourceNamePrefixService.class,
            LogService.class,
            CounterSubTaskService.class,
            ExtensibilitySubscriptionCallbackService.class,
            EventTopicService.class,
            UniquePropertiesService.class
    };


    public static void startServices(ServiceHost host) {
        host.log(Level.INFO, "Start initializing common services");

        startServices(host, servicesToStart);

        startServiceFactories(host, serviceFactoriesToStart);

        // trigger common initial boot service and wait to finish, it is responsible for populating
        // configuration properties states
        waitCommonInitialBootInitialization(host);
    }

    /**
     * Start initialization of system documents, posting with pragma to queue a request, for a
     * service to become available
     */
    private static void waitCommonInitialBootInitialization(ServiceHost host) {
        Throwable[] t = new Throwable[1];
        CountDownLatch l = new CountDownLatch(1);
        host.sendRequest(Operation
                .createPost(UriUtils.buildUri(host, CommonInitialBootService.class))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getUri())
                .setBody(new ServiceDocument())
                .setCompletion((o, e) -> {
                    try {
                        if (e != null) {
                            host.log(Level.SEVERE, "Failure while waiting for service availability"
                                    + " of common services: %s", Utils.toString(e));
                            t[0] = e;
                            return;
                        }
                        host.log(Level.INFO, "Waiting for service availability of common services"
                                + " finished.");
                    } finally {
                        l.countDown();
                    }
                }
        ));
        try {
            if (!l.await(COMMON_SERVICES_AVAILABILITY_TIMEOUT, TimeUnit.SECONDS)) {
                t[0] = new TimeoutException("Waiting for service availability of common services"
                        + " timed out.");
                host.log(Level.SEVERE, "Waiting for service availability of common services timed"
                                + " out: %s", t[0]);
            }
        } catch (InterruptedException e1) {
            host.log(Level.SEVERE, "Waiting for service availability of common services was"
                            + " interrupted: %s", Utils.toString(e1));
            t[0] = e1;
        }
        if (t[0] != null) {
            throw new RuntimeException(t[0]);
        }
    }
}
