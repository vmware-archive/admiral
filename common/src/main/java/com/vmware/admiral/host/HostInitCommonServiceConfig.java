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

import com.vmware.admiral.log.EventLogService;
import com.vmware.admiral.service.common.ClusterMonitoringService;
import com.vmware.admiral.service.common.CommonInitialBootService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.EventTopicService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionCallbackService;
import com.vmware.admiral.service.common.ExtensibilitySubscriptionFactoryService;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ReverseProxyService;
import com.vmware.admiral.service.common.SslTrustCertificateFactoryService;
import com.vmware.admiral.service.common.SslTrustImportService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitCommonServiceConfig extends HostInitServiceHelper {

    public static void startServices(ServiceHost host) {
        startServices(host, NodeMigrationService.class, NodeHealthCheckService.class,
                SslTrustImportService.class,
                ClusterMonitoringService.class,
                ConfigurationFactoryService.class,
                SslTrustCertificateFactoryService.class,
                CommonInitialBootService.class,
                ReverseProxyService.class,
                ExtensibilitySubscriptionFactoryService.class);

        startServiceFactories(host, ResourceNamePrefixService.class, RegistryService.class,
                LogService.class, EventLogService.class,
                CounterSubTaskService.class,
                ExtensibilitySubscriptionCallbackService.class,
                EventTopicService.class);

        // start initialization of system documents, posting with pragma to queue a request,
        // for a service to become available
        host.sendRequest(Operation
                .createPost(UriUtils.buildUri(host, CommonInitialBootService.class))
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setReferer(host.getUri())
                .setBody(new ServiceDocument()));
    }

}
