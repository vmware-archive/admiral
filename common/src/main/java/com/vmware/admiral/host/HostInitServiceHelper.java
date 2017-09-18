/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import com.vmware.admiral.service.common.EventTopicDeclarator;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.MigrationTaskService;

/**
 * Helper for starting services
 */
public abstract class HostInitServiceHelper {

    @SafeVarargs
    public static void startServiceFactories(ServiceHost host,
            Class<? extends Service>... serviceClasses) {
        Set<String> services = new HashSet<>();
        for (Class<? extends Service> serviceClass : serviceClasses) {
            Service serviceInstance;
            try {
                serviceInstance = serviceClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed to create factory for " + serviceClass, e);
            }
            populateServicesForHealthCheck(host, serviceInstance, serviceClass,
                    services, true);
            host.startFactory(serviceInstance);
            handleEventTopicDeclarators(serviceInstance, host);
        }
        registerServiceForHelathcheck(host, services);
        registerServiceForMigration(host, services);
    }

    @SafeVarargs
    public static void startServices(ServiceHost host,
            Class<? extends Service>... serviceClasses) {
        Set<String> services = new HashSet<>();
        for (Class<? extends Service> serviceClass : serviceClasses) {
            Service serviceInstance;
            try {
                serviceInstance = serviceClass.newInstance();

            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Failed to create instance of " + serviceClass, e);
            }

            populateServicesForHealthCheck(host, serviceInstance, serviceClass,
                    services, false);

            startService(host, serviceClass, serviceInstance);
            handleEventTopicDeclarators(serviceInstance, host);
        }
        registerServiceForHelathcheck(host, services);
        registerServiceForMigration(host, services);
    }

    public static void startService(ServiceHost host, Class<? extends Service> serviceClass,
            Service serviceInstance) {

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, serviceClass))
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                // shutdown the server when encountering an error
                                host.log(Level.SEVERE, "Failed to start service %s: %s",
                                        o.getUri(), Utils.toString(ex));

                                host.stop();
                                return;
                            }
                        }),
                serviceInstance);
    }

    private static void populateServicesForHealthCheck(ServiceHost host, Service serviceInstance,
            Class<? extends Service> serviceClass, Set<String> servicesForHelathceck,
            boolean factory) {

        String factoryOrSelfLink = factory ? "FACTORY_LINK" : "SELF_LINK";

        if (serviceInstance.hasOption(ServiceOption.REPLICATION)) {
            try {
                Field selfLink = serviceClass.getDeclaredField(factoryOrSelfLink);
                if (selfLink != null) {
                    Object value = selfLink.get(serviceInstance);
                    servicesForHelathceck.add(value.toString());
                }
            } catch (NoSuchFieldException | IllegalAccessException
                    | IllegalArgumentException e) {
                host.log(Level.SEVERE, "Exception while getting %s field for Service: %s :%s",
                        factoryOrSelfLink, serviceClass, e);
            }
        }
    }

    private static void registerServiceForHelathcheck(ServiceHost host, Set<String> services) {

        NodeHealthCheckService nodeHealthCheck = new NodeHealthCheckService();
        // Add migration task service explicitly because it is started from xenon
        services.add(MigrationTaskService.FACTORY_LINK);
        nodeHealthCheck.services = services;
        nodeHealthCheck.setSelfLink(NodeHealthCheckService.SELF_LINK);

        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host, nodeHealthCheck.getSelfLink()))
                .setBody(nodeHealthCheck)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE,
                                "Exception while register services for healthcheck: %s", e);
                    }
                }));
    }

    private static void registerServiceForMigration(ServiceHost host, Set<String> services) {

        NodeMigrationService nodeMigration = new NodeMigrationService();
        nodeMigration.services = services;

        host.sendRequest(Operation
                .createPatch(UriUtils.buildUri(host, NodeMigrationService.SELF_LINK))
                .setBody(nodeMigration)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.log(Level.SEVERE,
                                "Exception while register services for migration: %s", e);
                    }
                }));
    }

    private static void handleEventTopicDeclarators(Service service, ServiceHost host) {
        if (service instanceof EventTopicDeclarator) {
            ((EventTopicDeclarator) service).registerEventTopics(host);
        }
    }

}
