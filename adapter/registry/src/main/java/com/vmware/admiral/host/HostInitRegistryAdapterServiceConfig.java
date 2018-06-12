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

package com.vmware.admiral.host;

import static com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata.service;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;

import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.photon.controller.model.util.StartServicesHelper.ServiceMetadata;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitRegistryAdapterServiceConfig {

    public static final Collection<ServiceMetadata> SERVICES_METADATA = Collections
            .unmodifiableList(Arrays.asList(
                    service(RegistryAdapterService.class)));

    public static volatile URI registryAdapterReference;

    public static void startServices(ServiceHost host) {
        String remoteAdapterReference = System
                .getProperty("dcp.management.container.registry.adapter.service.reference");

        if (remoteAdapterReference != null && !remoteAdapterReference.isEmpty()) {
            registryAdapterReference = URI.create(remoteAdapterReference);

        } else {
            registryAdapterReference = UriUtils.buildUri(host, RegistryAdapterService.class);
            ensurePropertyExists(host, RegistryAdapterService.REGISTRY_PROXY_PARAM_NAME, () -> {
                ensurePropertyExists(host, RegistryAdapterService.REGISTRY_NO_PROXY_LIST_PARAM_NAME,
                        () -> {
                            host.startService(Operation.createPost(registryAdapterReference),
                                    new RegistryAdapterService());
                        });
            });
        }
    }

    private static void ensurePropertyExists(ServiceHost host, String propertyKey,
            Runnable callback) {
        new ServiceDocumentQuery<>(host, ConfigurationState.class)
                .queryDocument(
                        UriUtils.buildUriPath(ConfigurationFactoryService.SELF_LINK, propertyKey),
                        (r) -> {
                            if (r.hasException()) {
                                host.log(Level.WARNING,
                                        "Failed to get configuration property" + propertyKey, r.getException());
                            } else if (r.hasResult()) {
                                // configuration document exists, proceed.
                                callback.run();
                                return;
                            }
                            // create new configuration document with empty value and subscribe to it
                            ConfigurationState body = buildConfigurationState(propertyKey,
                                    RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE);

                            Operation
                                    .createPost(host, ConfigurationFactoryService.SELF_LINK)
                                    .addPragmaDirective(
                                            Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                                    .setBody(body)
                                    .setReferer(host.getUri())
                                    .setCompletion((o, e) -> {
                                        if (e != null) {
                                            host.log(Level.SEVERE,
                                                    "Failed to post configuration property" + propertyKey, e);
                                        }
                                        callback.run();
                                    })
                                    .sendWith(host);
                        });
    }

    static ConfigurationState buildConfigurationState(String propertyKey, String propertyValue) {
        ConfigurationState cs = new ConfigurationState();
        cs.documentSelfLink = propertyKey;
        cs.key = propertyKey;
        cs.value = (propertyValue != null && propertyValue.length() > 0) ? propertyValue
                : RegistryAdapterService.REGISTRY_PROXY_NULL_VALUE;

        return cs;
    }
}
